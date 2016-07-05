# GeoTrellis landsat tutorial project

This tutorial goes over how to process a single landsat image on a single machine, using GeoTrellis and spark.
We will create a server that will serve out tiles onto a web map that represent an NDVI calculation on our image.
The calculation of the NDVI and the rendering of the PNG tile will be dynamic and happen per tile request.

![Sample NDVI thumbail](https://raw.githubusercontent.com/geotrellis/geotrellis-landsat-tutorial/master/sample-ndvi-thumbnail.png)

### Download a Landsat image bands
Run the `data/landsat/download.sh` script to download the landsat image we will be working with.

Here is a thumbnail of the image:

![Landsat image thumbail](https://raw.githubusercontent.com/geotrellis/geotrellis-landsat-tutorial/master/LC81070352015218LGN00.jpg)

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

### Ingest the multiband geotiff into a GeoTrellis catalog.

This step will ingest the multiband image we made a previous step into a indexed tile set that GeoTrellis can quickly read data out of.
We'll ingest it as WebMercator tiles, where the tiles are cut according to the
[Slippy Map](http://wiki.openstreetmap.org/wiki/Slippy_Map) tile coordinate representation, at multiple zoom levels.

The code is in the `src/main/scala/tutorial/IngestImage.scala` file.

```console
> ./sbt run
```

Select the `tutorial.IngestImage` to run.

Tiles will be generated in the `data/catalog` directory.

### Serve out dynamically created NDVI images using Spray

This step will start a server that will serve out NDVI images onto a web map.

The code is located in the `src/main/scala/tutorial/ServeNDVI.scala` file.

```console
> ./sbt run
```

Select the `tutorial.ServeNDVI` to run.

You can now open `static/index.html` and see our NDVI tiles on a web map.
