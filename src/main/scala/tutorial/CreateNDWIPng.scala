package tutorial

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import com.typesafe.config.ConfigFactory


/**
  * Created by FroehlingGallier on 10/13/16.
  */
object CreateNDWIPng {

  val maskedPath = "data/r-g-nir.tif"
  val ndwiPath = "data/ndwi.png"

  def main(args: Array[String]): Unit = {
    val ndwi = {
      // Convert the tile to type double values,
      // because we will be performing an operation that
      // produces floating point values.
      println("Reading in multiband image...")
      val tile = MultibandGeoTiff(maskedPath).tile.convert(DoubleConstantNoDataCellType)

      // Use the combineDouble method to map over the red and infrared values
      // and perform the NDVI calculation.
      println("Performing NDWI calculation...")
      tile.combineDouble(MaskBandsRandGandNIR.G_BAND, MaskBandsRandGandNIR.NIR_BAND) { (g: Double, ir: Double) =>
        Calculations.ndwi(g, ir);
      }
    }

    // Get color map from the application.conf settings file.
    val colorMap = ColorMap.fromStringDouble(ConfigFactory.load().getString("tutorial.ndwiColormap")).get

    // Render this NDVI using the color breaks as a PNG,
    // and write the PNG to disk.
    println("Rendering PNG and saving to disk...")
    ndwi.renderPng(colorMap).write(ndwiPath)
  }
}
