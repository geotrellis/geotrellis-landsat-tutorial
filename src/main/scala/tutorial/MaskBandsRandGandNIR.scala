package tutorial

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import com.typesafe.config.ConfigFactory

object MaskBandsRandGandNIR {
  val maskedPath = "data/r-g-nir.tif"
  //constants to differentiate which bands to use
  val R_BAND = 0
  val G_BAND = 1
  val NIR_BAND = 2
  // Path to our landsat band geotiffs.
  def bandPath(b: String) = s"data/landsat/LC81070352015218LGN00_${b}.TIF"

  def main(args: Array[String]): Unit = {
    // Read in the red band
    println("Reading in the red band...")
    val rGeoTiff = SinglebandGeoTiff(bandPath("B4"))

    // Read in the green band
    println("Reading in green band...")
    val gGeoTiff = SinglebandGeoTiff(bandPath("B3"))

    // Read in the near infrared band
    println("Reading in the NIR band...")
    val nirGeoTiff = SinglebandGeoTiff(bandPath("B5"))

    // Read in the QA band
    println("Reading in the QA band...")
    val qaGeoTiff = SinglebandGeoTiff(bandPath("BQA"))

    // GeoTiffs have more information we need; just grab the Tile out of them.
    val (rTile, gTile, nirTile, qaTile) = (rGeoTiff.tile, gGeoTiff.tile, nirGeoTiff.tile, qaGeoTiff.tile)

    // This function will set anything that is potentially a cloud to NODATA
    def maskClouds(tile: Tile): Tile =
      tile.combine(qaTile) { (v: Int, qa: Int) =>
        val isCloud = qa & 0x8000
        val isCirrus = qa & 0x2000
        if(isCloud > 0 || isCirrus > 0) { NODATA }
        else { v }
      }

    // Mask our red, green and near infrared bands using the qa band
    println("Masking clouds in the red band...")
    val rMasked = maskClouds(rTile)
    println("Masking clouds in the green band...")
    val gMasked = maskClouds(gTile)
    println("Masking clouds in the NIR band...")
    val nirMasked = maskClouds(nirTile)

    // Create a multiband tile with our two masked red and infrared bands.
    val mb = ArrayMultibandTile(rMasked, gMasked, nirMasked).convert(IntConstantNoDataCellType)

    // Create a multiband geotiff from our tile, using the same extent and CRS as the original geotiffs.
    println("Writing out the multiband R + G + NIR tile...")
    MultibandGeoTiff(mb, rGeoTiff.extent, rGeoTiff.crs).write(maskedPath)
  }
}
