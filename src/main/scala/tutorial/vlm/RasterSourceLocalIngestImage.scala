package tutorial.vlm

import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.avro._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.json.Implicits._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.tiling.{FloatingLayoutScheme, LayoutLevel}

import org.apache.spark._
import org.apache.spark.rdd._

import scala.io.StdIn
import java.io.File

object RasterSourceLocalIngestImage {
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
        .map(new GeoTiffRasterSource(_): RasterSource)
        .cache()

    val summary = RasterSummary.fromRDD(sourceRDD)
    val layoutLevel @ LayoutLevel(_, layout) = summary.levelFor(new FloatingLayoutScheme(512, 512))
    val rasterRefRdd: RDD[(SpatialKey, RasterRef)] = sourceRDD.flatMap { new LayoutTileSource(_, layout).toRasterRefs }

    val tileRDD: RDD[(SpatialKey, MultibandTile)] =
      rasterRefRdd
        .groupByKey(SpatialPartitioner(summary.estimatePartitionsNumber))
        .mapValues { iter => MultibandTile(iter.flatMap(_.raster.toSeq.flatMap(_.tile.bands))) }

    val (metadata, _) = summary.toTileLayerMetadata(layoutLevel)
    val contextRDD: MultibandTileLayerRDD[SpatialKey] = ContextRDD(tileRDD, metadata)

    // Create the attributes store that will tell us information about our catalog.
    val attributeStore = FileAttributeStore(outputPath)

    // Create the writer that we will use to store the tiles in the local catalog.
    val writer = FileLayerWriter(attributeStore)

    val layerId = LayerId("landsat-nocog-ref", 0)
    writer.write(layerId, contextRDD, ZCurveKeyIndexMethod)
  }
}
