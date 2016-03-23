package tutorial

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import com.typesafe.config.ConfigFactory

object MaskRedAndNearInfrared {
  val maskedPath = "data/r-nir.tif"

  // Path to our landsat band geotiffs.
  def bandPath(b: String) = s"data/landsat/LC80140322014139LGN00_${b}.TIF"

  def main(args: Array[String]): Unit = {
    // Read in the red band.
    val rGeoTiff = SinglebandGeoTiff(bandPath("B4"))

    // Read in the near infrared band
    val nirGeoTiff = SinglebandGeoTiff(bandPath("B5"))

    // Read in the QA band
    val qaGeoTiff = SinglebandGeoTiff(bandPath("BQA"))

    // GeoTiffs have more information we need; just grab the Tile out of them.
    val (rTile, nirTile, qaTile) = (rGeoTiff.tile, nirGeoTiff.tile, qaGeoTiff.tile)

    // This function will set anything that is potentially a cloud to NODATA
    def maskClouds(tile: Tile): Tile =
      tile.combine(qaTile) { (v: Int, qa: Int) =>
        val isCloud = qa & 0x8000
        val isCirrus = qa & 0x2000
        if(isCloud > 0 || isCirrus > 0) { NODATA }
        else { v }
      }

    // Mask our red and near infrared bands using the qa band
    val rMasked = maskClouds(rTile)
    val nirMasked = maskClouds(nirTile)

    // Create a multiband tile with our two masked red and infrared bands.
    val mb = ArrayMultibandTile(rMasked, nirMasked).convert(IntConstantNoDataCellType)

    // Create a multiband geotiff from our tile, using the same extent and CRS as the original geotiffs.
    MultibandGeoTiff(mb, rGeoTiff.extent, rGeoTiff.crs).write(maskedPath)
  }
}
