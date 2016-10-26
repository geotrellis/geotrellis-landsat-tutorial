package tutorial

import geotrellis.raster._

/**
  * Created by FroehlingGallier on 10/26/16.
  */
object Calculations {
  /*
  * Calculates the normalized difference vegetation index
  * */
  def ndvi (r: Double, ir: Double) : Double = {
    if (isData(r) && isData(ir)) {
        (ir - r) / (ir + r)
    } else {
      Double.NaN
    }
  }

  /*
  * Calculates the normalized difference water index
  * */
  def ndwi (g: Double, ir: Double) : Double = {
    if (isData(g) && isData(ir)) {
      (g - ir) / (g + ir)
    } else {
      Double.NaN
    }
  }
}
