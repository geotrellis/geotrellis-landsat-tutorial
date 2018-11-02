package tutorial.vlm

import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.avro._
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.raster.resample.Bilinear
import geotrellis.spark._
import geotrellis.spark.io.json.Implicits._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.tiling.{LayoutLevel, ZoomedLayoutScheme}
import geotrellis.spark.pyramid.Pyramid

import org.apache.spark._
import org.apache.spark.rdd._

import scala.io.StdIn
import java.io.File

object RasterSourceGlobalIngestImage {
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

    val sourceRDD: RDD[RasterSource] =
      sc.parallelize(files, files.length)
        .map(new GeoTiffRasterSource(_).reproject(targetCRS, method): RasterSource)

    val summary = RasterSummary.fromRDD(sourceRDD)
    val layoutLevel @ LayoutLevel(zoom, layout) = summary.levelFor(layoutScheme)
    val tiledLayoutSource: RDD[LayoutTileSource] = sourceRDD.map(_.tileToLayout(layout, method))
    val rasterRefRdd: RDD[(SpatialKey, RasterRef)] = tiledLayoutSource.flatMap(_.toRasterRefs)

    val tileRDD: RDD[(SpatialKey, MultibandTile)] =
      rasterRefRdd
        .groupByKey(SpatialPartitioner(summary.estimatePartitionsNumber))
        .mapValues { iter => MultibandTile(iter.flatMap(_.raster.toSeq.flatMap(_.tile.bands))) }

    val (metadata, _) = summary.toTileLayerMetadata(layoutLevel)
    val contextRDD: MultibandTileLayerRDD[SpatialKey] = ContextRDD(tileRDD, metadata)

    val attributeStore = FileAttributeStore(outputPath)
    val writer = FileLayerWriter(attributeStore)

    val layerId = LayerId("landsat-nocog-ref-global", zoom)
    writer.write(layerId, contextRDD, ZCurveKeyIndexMethod)

    /*Pyramid.upLevels(contextRDD, layoutScheme, zoom, Bilinear) { (rdd, z) =>
      val layerId = LayerId("landsat-nocog-ref-global", z)
      // If the layer exists already, delete it out before writing
      if(attributeStore.layerExists(layerId)) {
        new FileLayerManager(attributeStore).delete(layerId)
      }
      writer.write(layerId, rdd, ZCurveKeyIndexMethod)
    }*/
  }
}
