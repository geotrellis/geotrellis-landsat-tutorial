package tutorial.vlm

import geotrellis.contrib.vlm.avro._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.json.Implicits._
import geotrellis.spark.io.file._
import org.apache.spark._

import scala.io.StdIn
import java.io.File

import geotrellis.spark.io.Intersects
import geotrellis.vector.Extent

object RasterSourceReadAll {
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
    val layerId = LayerId("landsat-nocog-ref-global", 13)
    val attributeStore = FileAttributeStore(outputPath)
    val reader = FileLayerReader(attributeStore)
    val extent = {
      val extent = attributeStore.readMetadata[TileLayerMetadata[SpatialKey]](layerId).extent
      extent.buffer(- math.min(extent.width / 2, extent.height / 2))
    }

    val raster =
      reader
        .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
        .where(Intersects(extent))
        .result
        .stitch()

    raster.tile.band(0).renderPng().write("/Users/daunnc/Downloads/landsat-nocog-ref-global.png")
  }
}
