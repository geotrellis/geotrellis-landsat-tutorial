package tutorial.vlm

import geotrellis.contrib.vlm._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.tiling.{LayoutDefinition, FloatingLayoutScheme}

import org.apache.spark._
import org.apache.spark.rdd._

import scala.io.StdIn
import java.io.File

object RasterSourceRefReadAll {
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
        .set("spark.kryoserializer.buffer.max", "1000")

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
    // Create the attributes store that will tell us information about our catalog.
    val attributeStore = FileAttributeStore(outputPath)

    // Create the writer that we will use to store the tiles in the local catalog.
    val reader = FileLayerReader(attributeStore)

    val layerId = LayerId("landsat-nocog-ref", 0)
    val raster = reader.read[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId).stitch()
    raster.tile.subsetBands(0, 1, 2).renderPng().write("/Users/daunnc/Downloads/landsat-nocog-ref.png")
  }
}
