package tutorial.vlm

import java.io.File

import geotrellis.contrib.vlm._
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.raster.resample.Bilinear
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.tiling.{LayoutLevel, ZoomedLayoutScheme}
import org.apache.spark._
import org.apache.spark.rdd._

import scala.io.StdIn

object RasterSourceRefPyramidIngestImage {
  import tutorial.vlm.avro.Implicits._
  val inputPath = "file://" + new File("data/r-g-nir.tif").getAbsolutePath
  val outputPath = "data/catalog"
  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf =
      new SparkConf()
        .setMaster("local[*]")
        .setAppName("Spark Tiler")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")

    val sc = new SparkContext(conf)
    try {
      run(sc)
      // Pause to wait to close the spark context,
      // so that you can check out the UI at http://localhost:4040
      println("Hit enter to exit.")
      StdIn.readLine()
    } finally {
      sc.stop()
    }
  }

  def fullPath(path: String) = new java.io.File(path).getAbsolutePath

  def run(implicit sc: SparkContext) = {
    val files = inputPath :: Nil
    val targetCRS = WebMercator

    val method = Bilinear
    val layoutScheme = ZoomedLayoutScheme(targetCRS, tileSize = 256)

    // we can read in the infomration about input raster sources
    val sourceRDD: RDD[RasterSource] =
      sc.parallelize(files, files.length)
        .map(uri => new GeoTiffRasterSource(uri).reproject(targetCRS, method): RasterSource)

    // after reading, we need to collect metadata for all input RasterSources
    val summary: RasterSummary = {
      val all = RasterSummary.collect(sourceRDD)
      println(s"Raster Summary: ${all.toList}")
      require(all.size == 1, "multiple CRSs detected") // what to do in this case?
      all.head // assuming we have a single one
    }

    val LayoutLevel(globalZoom, layout) = layoutScheme.levelFor(summary.extent, summary.cellSize)

    val numPartitions: Int = {
      import squants.information._
      val bytes = Bytes(summary.cellType.bytes * summary.cells)
      val numPartitions: Int = (bytes / Megabytes(64)).toInt
      println(s"Using $numPartitions partitions for ${bytes.toString(Gigabytes)}")
      numPartitions
    }

    val layoutRDD: RDD[LayoutTileSource] = sourceRDD.map(_.tileToLayout(layout, method))

    val rasterRefRdd: RDD[(SpatialKey, RasterRef)] = {
      layoutRDD
        .flatMap { tileSource =>
          // iterate over all tile keys we can read from this RasterSource
          tileSource.keys.toIterator.map { key: SpatialKey =>
            // generate reference to a tile we can read eventually
            val ref: RasterRef = tileSource.rasterRef(key)
            (key, ref)
          }
        }
    }

    val newSummary: RasterSummary = {
      val all = RasterSummary.collect(layoutRDD.map(_.source))
      println(s"Raster Summary: ${all.toList}")
      require(all.size == 1, "multiple CRSs detected") // what to do in this case?
      all.head // assuming we have a single one
    }

    /** Function that actually forces the reading of pixels */
    def readRefs(refs: Iterable[RasterRef]): MultibandTile =
      ArrayMultibandTile(refs.flatMap(_.raster.toList.flatMap(_.tile.bands.map(_.toArrayTile: Tile).toList)).toArray)

    val tileRdd: RDD[(SpatialKey, MultibandTile)] = {
      rasterRefRdd
        .groupByKey(SpatialPartitioner(numPartitions))
        .mapValues(readRefs)
    }

    // println(s"realKeys: ${tileRdd.keys.collect().toList}")

    // Finish assemling geotrellis raster layer type
    val layerRdd: MultibandTileLayerRDD[SpatialKey] = {
      val (ld, _) = (layout, globalZoom)
      // summary.copy(extent = TargetGrid(layout).apply(layout))
      // weird API issues
      val dataBounds: Bounds[SpatialKey] = KeyBounds(ld.mapTransform.extentToBounds(layout.extent.intersection(newSummary.extent).get))
      val (tlm, zoom) = TileLayerMetadata[SpatialKey](summary.cellType, ld, newSummary.extent, summary.crs, dataBounds) -> globalZoom

      // val (tlm, zoom) = summary.toTileLayerMetadata(GlobalLayout(256, globalZoom, 0.1))
      // println(s"collectedKeyBounds: ${tlm.bounds}")

      ContextRDD(tileRdd, tlm)
    }

    // Create the attributes store that will tell us information about our catalog.
    val attributeStore = FileAttributeStore(outputPath)

    // Create the writer that we will use to store the tiles in the local catalog.
    val writer = FileLayerWriter(attributeStore)

    val layerId = LayerId("landsat-nocog-ref-global", globalZoom)
    writer.write(layerId, layerRdd, ZCurveKeyIndexMethod)
  }
}
