package tutorial.vlm

import geotrellis.contrib.vlm._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.tiling.{FloatingLayoutScheme, LayoutDefinition}
import org.apache.spark._
import org.apache.spark.rdd._

import scala.io.StdIn
import java.io.File

object RasterSourceRefIngestImage {
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
    // for a given list of files
    val files = inputPath :: Nil

    // we can read in the infomration about input raster sources
    val sourceRDD: RDD[RasterSource] =
      sc.parallelize(files, files.length)
        .map(uri => new GeoTiffRasterSource(uri): RasterSource)
        .cache()

    // after reading, we need to collect metadata for all input RasterSources
    val summary: RasterSummary = {
      val all = RasterSummary.collect(sourceRDD)
      println(s"Raster Summary: ${all.toList}")
      require(all.size == 1, "multiple CRSs detected") // what to do in this case?
      all.head // assuming we have a single one
    }

    // LayoutDefinition specifies a pixel grid as well as tile grid over a single CRS
    val layout: LayoutDefinition = {
      // LayoutScheme is something that will produce a pixel and tile grid for rasters footprints
      // Floating layout scheme will preserve the raster cell size and start tiling at the top-most, left-most pixel available
      val scheme = new FloatingLayoutScheme(512, 512)
      scheme.levelFor(summary.extent, summary.cellSize).layout
    }


    val numPartitions: Int = {
      import squants.information._
      val bytes = Bytes(summary.cellType.bytes * summary.cells)
      val numPartitions: Int = (bytes / Megabytes(64)).toInt
      println(s"Using $numPartitions partitions for ${bytes.toString(Gigabytes)}")
      numPartitions
    }

    val rasterRefRdd: RDD[(SpatialKey, RasterRef)] = {
      sourceRDD
        .flatMap { rs =>
          val tileSource = new LayoutTileSource(rs, layout)
          // iterate over all tile keys we can read from this RasterSource
          tileSource.keys.toIterator.map { key: SpatialKey =>
            // generate reference to a tile we can read eventually
            val ref: RasterRef = tileSource.rasterRef(key)
            (key, ref)
          }
        }
    }

    // implicit def supe: AvroRecordCodec[RasterRef] = { def encode(t: RasterRef) = AvroRecordCodec[MultibandTile].encode(t.tile) }
    //resample raster ref??

    // implicit def supe: AvroRecordCodec[PaddedTile] = { def encode(t: PaddedTile) = geotrellis.spark.io.avro.codecs.Implicits.tileUnionCodec.encode(t.toArrayTile) }

    /** Function that actually forces the reading of pixels */
    def readRefs(refs: Iterable[RasterRef]): MultibandTile =
      ArrayMultibandTile(refs.flatMap(_.raster.toList.flatMap(_.tile.bands.toList)).toArray)

    // implicit avro codec for a raster ref and for each tile we can map over the bands and convert them into whatever type we need
    val tileRdd: RDD[(SpatialKey, MultibandTile)] = {
      rasterRefRdd
        .groupByKey(SpatialPartitioner(numPartitions))
        .mapValues(readRefs)
    }

    // Finish assemling geotrellis raster layer type
    val layerRdd: MultibandTileLayerRDD[SpatialKey] = {
      val (tlm, zoom) = summary.toTileLayerMetadata(LocalLayout(512, 512))
      ContextRDD(tileRdd, tlm)
    }

    // Create the attributes store that will tell us information about our catalog.
    val attributeStore = FileAttributeStore(outputPath)

    // Create the writer that we will use to store the tiles in the local catalog.
    val writer = FileLayerWriter(attributeStore)

    val layerId = LayerId("landsat-nocog-ref", 0)
    writer.write(layerId, layerRdd, ZCurveKeyIndexMethod)
  }
}
