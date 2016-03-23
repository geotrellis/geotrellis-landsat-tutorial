package tutorial

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import com.typesafe.config.ConfigFactory

object CreateNDVIPng {
  val maskedPath = "data/r-nir.tif"
  val ndviPath = "data/ndvi.png"

  def main(args: Array[String]): Unit = {
    val ndvi = {
      // Convert the tile to type double values,
      // because we will be performing an operation that
      // produces floating point values.
      val tile = MultibandGeoTiff(maskedPath).convert(DoubleConstantNoDataCellType)

      // Use the combineDouble method to map over the red and infrared values
      // and perform the NDVI calculation.
      tile.combineDouble(0, 1) { (r: Double, ir: Double) =>
        if(isData(r) && isData(ir)) {
          (ir - r) / (ir + r)
        } else {
          Double.NaN
        }
      }
    }

    // Get color map from the application.conf settings file.
    val colorMap = ColorMap.fromStringDouble(ConfigFactory.load().getString("tutorial.colormap")).get

    // Render this NDVI using the color breaks as a PNG,
    // and write the PNG to disk.
    ndvi.renderPng(colorMap).write(ndviPath)
  }
}
