package tutorial

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.raster.reproject._
import geotrellis.proj4._

import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.json._
import geotrellis.spark.ingest._
import geotrellis.spark.reproject._
import geotrellis.spark.tiling._
import geotrellis.spark.render._

import geotrellis.vector._

import org.apache.spark._
import org.apache.spark.rdd._

import java.io.File

object IngestImage {
  val inputPath = "file://" + new File("data/r-nir.tif").getAbsolutePath
  val outputPath = "data/catalog"

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
    // Read the geotiff in as a single image RDD,
    // using a method implicitly added to SparkContext by
    // an implicit class available via the
    // "import geotrellis.spark.io.hadoop._ " statement.
    val inputRdd = sc.hadoopMultiBandGeoTiffRDD(inputPath)

    // Use the "RasterMetaData.fromRdd" call to find the zoom
    // level that the closest match to the resolution of our source image,
    // and derive information such as the full bounding box and data type.
    val (_, rasterMetaData) =
      RasterMetaData.fromRdd(inputRdd, FloatingLayoutScheme(512))

    // Use the Tiler to cut our tiles into tiles that are index by the z/x/y map coordinates.
    val tiled: RDD[(SpatialKey, MultiBandTile)] = inputRdd.tileToLayout(rasterMetaData, Bilinear)

    // We'll be tiling the images using a zoomed layout scheme
    // in the web mercator format (which fits the slippy map tile specification).
    // We'll be creating 256 x 256 tiles.
    val layoutScheme = ZoomedLayoutScheme(WebMercator, tileSize = 256)

    // We need to reproject the tiles to WebMercator
    val (zoom, reprojected) = MultiBandRasterRDD(tiled, rasterMetaData).reproject(WebMercator, layoutScheme, Bilinear)

    // Create the writer that we will use to store the tiles in the local catalog.
    val writer = FileLayerWriter[SpatialKey, MultiBandTile, RasterMetaData](outputPath, ZCurveKeyIndexMethod)

    // Pyramiding up the zoom levels, write our tiles out to the local file system.
    val writeOp =
      Pyramid.upLevels(reprojected, layoutScheme, zoom) { (rdd, z) =>
        writer.write(LayerId("landsat", z), rdd)
      }
  }
}
