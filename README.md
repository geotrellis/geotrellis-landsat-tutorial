# GeoTrellis landsat tutorial project

This tutorial goes over how to process a single landsat image on a single machine, using GeoTrellis and spark.
We will create a server that will serve out tiles onto a web map that represent NDVI and NDWI calculations on our image.
The calculations of the NDVI/NDWI and the rendering of the PNG tile will be dynamic and happen per tile request.

![Sample NDVI thumbail](https://raw.githubusercontent.com/geotrellis/geotrellis-landsat-tutorial/master/sample-ndvi-thumbnail.png)

### Download a Landsat image bands
Run the `data/landsat/download-data.sh` script to download the landsat image we will be working with:

```bash
cd data/landsat
./download-data.sh
```

Here is a thumbnail of the image:

![Landsat image thumbail](https://raw.githubusercontent.com/geotrellis/geotrellis-landsat-tutorial/master/LC81070352015218LGN00.jpg)

### Creating a 3 band geotiff from the red, green and NIR bands masked with the QA band

The code in `src/main/scala/tutorial/MaskBandsRandGandNIR.scala` will do this.

```console
> ./sbt run
```

Select the `tutorial.MaskBandsRandGandNIR` to run.

This will produce `data/r-g-nir.tif`

### Create a PNG of an NDVI of of our image.

The code in `src/main/scala/tutorial/CreateNDVIPng.scala` will do this.

```console
> ./sbt run
```

Select the `tutorial.CreateNDVIPng` to run.

This will produce `data/ndvi.png`

### Create a PNG of an NDWI of of our image.

The code in `src/main/scala/tutorial/CreateNDWIPng.scala` will do this.

```console
> ./sbt run
```

Select the `tutorial.CreateNDWIPng` to run.

This will produce `data/ndwi.png`

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

### Serve out dynamically created NDVI/NDWI images using Spray

This step will start a server that will serve out NDVI/NDWI images onto a web map, allowing users to toggle between layers.

The code is located in the `src/main/scala/tutorial/Serve.scala` file.

```console
> ./sbt run
```

Select the `tutorial.Serve` to run.

You can now open `static/index.html` and see our NDVI/NDWI tiles on a web map.
