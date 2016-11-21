package tutorial

import geotrellis.raster._

/**
  * Object that can calculate ndvi and ndwi
  */
object Calculations {
  /** Calculates the normalized difference vegetation index
    * @param r value of red band
    * @param ir value of infra-red band
    */
  def ndvi (r: Double, ir: Double) : Double = {
    if (isData(r) && isData(ir)) {
        (ir - r) / (ir + r)
    } else {
      Double.NaN
    }
  }

  /** Calculates the normalized difference water index
    * @param g value of green band
    * @param ir value of infra-red band
    */
  def ndwi (g: Double, ir: Double) : Double = {
    if (isData(g) && isData(ir)) {
      (g - ir) / (g + ir)
    } else {
      Double.NaN
    }
  }
}
