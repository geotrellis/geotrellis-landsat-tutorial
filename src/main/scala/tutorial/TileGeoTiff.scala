package tutorial

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.proj4._

import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.slippy._
import geotrellis.spark.ingest._
import geotrellis.spark.reproject._
import geotrellis.spark.tiling._
import geotrellis.spark.render._

import geotrellis.vector._

import org.apache.spark._
import org.apache.spark.rdd._

object TileGeoTiff {
  val inputPath = fullPath("data/landsat-tiles")
  val outputPath = fullPath("data/tiles")

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf =
      new SparkConf()
        .setMaster("local[*]")
        .setAppName("Spark Tiler")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    val sc = new SparkContext(conf)

    try {
      run(sc)
      // Pause to wait to close the spark context,
      // so that you can check out the UI at http://localhost:4040
      println("Hit enter to exit.")
      readLine()
    } finally {
      sc.stop()
    }
  }

  def fullPath(path: String) = new java.io.File(path).getAbsolutePath

  def run(implicit sc: SparkContext) = {
    // We'll be tiling the images using a zoomed layout scheme
    // in the web mercator format (which fits the slippy map tile specification).
    // We'll be creating 256 x 256 tiles.
    val layoutScheme = ZoomedLayoutScheme(WebMercator, tileSize = 256)

    // Read the geotiff tiles in as an RDD,
    // using a method implicitly added to SparkContext by
    // an implicit class available via the
    // "import geotrellis.spark.io.hadoop._ " statement.
    val inputTiles = sc.hadoopMultiBandGeoTiffRDD(inputPath)

    // Use the "RasterMetaData.fromRdd" call to find the zoom
    // level that the closest match to the resolution of our source images,
    // and derive information such as the full bounding box and data type.
    val (zoom, rasterMetaData) =
      RasterMetaData.fromRdd(inputTiles, WebMercator, layoutScheme)(_.projectedExtent.extent)

    // Use the Tiler to cut our tiles into tiles that are index by the z/x/y slippy map coordinates.
    val tiled: RDD[(SpatialKey, MultiBandTile)] = Tiler(inputTiles, rasterMetaData, Bilinear)

    // Pyramiding up the zoom levels, write our tiles out to the local file system.
    val writeOp =
      Pyramid.upLevels(new MultiBandRasterRDD(tiled, rasterMetaData), layoutScheme, zoom) { (rdd, z) =>
        val md = rdd.metaData

        val writer = new HadoopSlippyTileWriter[MultiBandTile](outputPath, "tif")({ (key, tile) =>
          val extent = md.mapTransform(key)
          MultiBandGeoTiff(tile, extent, WebMercator).toByteArray
        })
        new MultiBandRasterRDD(writer.setupWrite(z, rdd), md)
      }

    // We've set up a set of Spark "transformations", but need to call an "action" to kick off the work.
    // An empty foreach call is enough to set the processing in motion.
    writeOp.foreach { x => }
  }
}
