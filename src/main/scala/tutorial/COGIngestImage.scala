package tutorial

import geotrellis.raster._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.raster.resample._
import geotrellis.proj4._

import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.cog._
import geotrellis.spark.io.file._
import geotrellis.spark.io.file.cog._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.index._
import geotrellis.spark.tiling._
import geotrellis.vector._

import org.apache.spark._
import org.apache.spark.rdd._

import scala.io.StdIn
import java.io.File

object COGIngestImage {
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
    // Read the geotiff in as a single image RDD,
    // using a method implicitly added to SparkContext by
    // an implicit class available via the
    // "import geotrellis.spark.io.hadoop._ " statement.
    val inputRdd: RDD[(ProjectedExtent, MultibandTile)] =
      sc.hadoopMultibandGeoTiffRDD(inputPath)

    // Use the "TileLayerMetadata.fromRdd" call to find the zoom
    // level that the closest match to the resolution of our source image,
    // and derive information such as the full bounding box and data type.
    val (_, rasterMetaData) = inputRdd.collectMetadata[SpatialKey](FloatingLayoutScheme(512))

    // Use the Tiler to cut our tiles into tiles that are index to a floating layout scheme.
    // We'll repartition it so that there are more partitions to work with, since spark
    // likes to work with more, smaller partitions (to a point) over few and large partitions.
    val tiled: RDD[(SpatialKey, MultibandTile)] =
      inputRdd
        .tileToLayout(rasterMetaData.cellType, rasterMetaData.layout, Bilinear)
        .repartition(100)

    // We'll be tiling the images using a zoomed layout scheme
    // in the web mercator format (which fits the slippy map tile specification).
    // We'll be creating 256 x 256 tiles.
    val layoutScheme = ZoomedLayoutScheme(WebMercator, tileSize = 256)

    // We need to reproject the tiles to WebMercator
    val (zoom, reprojected): (Int, RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]]) =
      MultibandTileLayerRDD(tiled, rasterMetaData)
        .reproject(WebMercator, layoutScheme, Bilinear)

    // Create the attributes store that will tell us information about our catalog.
    val attributeStore = FileAttributeStore(outputPath)

    // Create the writer that we will use to store the tiles in the local catalog.
    val writer = FileCOGLayerWriter(attributeStore)

    val layerName = "landsat-nocomp"

    val cogLayer =
      COGLayer.fromLayerRDD(
        reprojected,
        zoom,
        options = COGLayerWriter.Options.DEFAULT.copy(
          compression = NoCompression,
          maxTileSize = 4096
        )
      )

    val keyIndexes =
      cogLayer.metadata.zoomRangeInfos.
        map { case (zr, bounds) => zr -> ZCurveKeyIndexMethod.createIndex(bounds) }.
        toMap

    writer.writeCOGLayer(layerName, cogLayer, keyIndexes)
  }
}
