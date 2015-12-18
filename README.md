# GeoTrellis landsat tutorial project

This tutorial goes over how to process a single landsat image on a single machine, using GeoTrellis and spark.
We will create a server that will serve out tiles onto a web map that represent an NDVI calculation on our image.
The calculation of the NDVI and the rendering of the PNG tile will be dynamic and happen per tile request.

![Sample NDVI thumbail](https://raw.githubusercontent.com/geotrellis/geotrellis-landsat-tutorial/sample-ndvi-thumbnail.png)

### Download a Landsat image
Download this landsat image: https://s3.amazonaws.com/geotrellis-sample-datasets/landsat/LC80140322014139LGN00.tar.bz

Un-tar this into `data/landsat`

### Creating a 2 band geotiff from the red and NIR bands masked with the QA band

The code in `src/main/scala/tutorial/MaskRedAndNearInfrared.scala` will do this.

```console
> ./sbt run
```

Select the `tutorial.MaskRedAndNearInfrared` to run.

This will produce `data/r-nir.tif`

### Create a PNG of an NDVI of of our image.

The code in `src/main/scala/tutorial/CreateNDVIPng.scala` will do this.

```console
> ./sbt run
```

Select the `tutorial.CreateNDVIPng` to run.

This will produce `data/ndvi.png`

### Preprocessing our data with GDAL

We want to use the [GDAL](http://www.gdal.org/) library to do some preproccessing of our data

#### Reproject our image using gdalwarp

First, we want to reproject our data into "web mercator" (EPSG:3857)

In the `data` directory:

```console
> gdalwarp -t_srs EPSG:3857 r-nir.tif r-nir-wm.tif
```

#### Tile out the R and NIR bands with gdal_retile.py

We'll want to work with smaller tiles of our image when working with spark.
Tile them out using [gdal_retile.py](http://www.gdal.org/gdal_retile.html)

In the `data` directory:

```console
> mkdir landsat-tiles
> gdal_retile.py -ps 512 512 -targetDir landsat-tiles/ r-nir-wm.tif
```

### Transform the tiled image into z/x/y indexed GeoTiffs.

This step will transform the tiles we made last step into a set of GeoTiffs representing a version of the raster in GeoTiffs
according to the [Slippy Map](http://wiki.openstreetmap.org/wiki/Slippy_Map) tile coordinate representation, at multiple zoom levels.

The code is in the `src/main/scala/tutorial/TileGeoTiff.scala` file.

```console
> ./sbt run
```

Select the `tutorial.TileGeoTiff` to run.

Tiles will be generated in the `data/tiles` directory.

### Serve out dynamically created NDVI images using Spray

This step will start a server that will serve out NDVI images onto a web map.

The code is located in the `src/main/scala/tutorial/ServeNDVI.scala` file.

```console
> ./sbt run
```

Select the `tutorial.ServeNDVI` to run.

You can either go tonow open `static/index.html` and see our NDVI tiles on a web map.
