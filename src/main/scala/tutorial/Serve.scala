package tutorial

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.spark._
import geotrellis.spark.io.{ValueReader, _}
import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.vector.io.json._
import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import akka.stream.{ActorMaterializer, Materializer}
import scala.concurrent._
import com.typesafe.config.ConfigFactory
import MaskBandsRandGandNIR.{G_BAND, NIR_BAND, R_BAND}
import geotrellis.proj4.{CRS, LatLng}

object Serve extends App with Service {
  val catalogPath = new java.io.File("data/catalog").toURI
  // Create a readers that will read in the indexed tiles we produced in IngestImage.
  val attributeStore: AttributeStore =
    AttributeStore(catalogPath)

  val valueReader: ValueReader[LayerId] =
    ValueReader(attributeStore, catalogPath)

  val collectionReader: CollectionLayerReader[LayerId] =
    CollectionLayerReader(attributeStore, catalogPath)

  val ndviColorMap =
    ColorMap.fromStringDouble(ConfigFactory.load().getString("tutorial.ndviColormap")).get
  val ndwiColorMap =
    ColorMap.fromStringDouble(ConfigFactory.load().getString("tutorial.ndwiColormap")).get

  override implicit val system = ActorSystem("tutorial-system")
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(root, "0.0.0.0", 8080)
}

trait Service {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer
  val logger: LoggingAdapter

  def valueReader: ValueReader[LayerId]
  def collectionReader: CollectionLayerReader[LayerId]

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def root =
    pathPrefix(Segment / IntNumber / IntNumber / IntNumber) { (render, zoom, x, y) =>
      complete {
        Future {
          // Read in the tile at the given z/x/y coordinates.
          val tileOpt: Option[MultibandTile] =
            try {
              val reader = Serve.valueReader.reader[SpatialKey, MultibandTile](LayerId("landsat", zoom))
              Some(reader.read(x, y))
            } catch {
              case _: ValueNotFoundError =>
                None
            }
          render match {
            case "ndvi" =>
              tileOpt.map { tile =>
                // Compute the NDVI
                val ndvi =
                  tile.convert(DoubleConstantNoDataCellType).combineDouble(R_BAND, NIR_BAND) { (r, ir) =>
                    Calculations.ndvi(r, ir);
                  }
                // Render as a PNG
                val png = ndvi.renderPng(Serve.ndviColorMap)
                pngAsHttpResponse(png)
              }
            case "ndwi" =>
              tileOpt.map { tile =>
                // Compute the NDWI
                val ndwi =
                  tile.convert(DoubleConstantNoDataCellType).combineDouble(G_BAND, NIR_BAND) { (g, ir) =>
                    Calculations.ndwi(g, ir)
                  }
                // Render as a PNG
                val png = ndwi.renderPng(Serve.ndwiColorMap)
                pngAsHttpResponse(png)
              }
          }
        }
      }
    } ~
    pathPrefix("summary" / IntNumber) { zoom =>
      pathEndOrSingleSlash {
        post {
          entity(as[String]) { geoJson =>
            val poly = geoJson.parseGeoJson[Polygon]
            val id: LayerId = LayerId("landsat", zoom)

            // Leaflet produces polygon in LatLng, we need to reproject it to layer CRS
            val layerMetadata = Serve.attributeStore.readMetadata[TileLayerMetadata[SpatialKey]](id)
            val queryPoly = poly.reproject(LatLng, layerMetadata.crs)

            // Query all tiles that intersect the polygon and build histogram
            val queryHist = collectionReader
              .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](id)
              .where(Intersects(queryPoly))
              .result // all intersecting tiles have been fetched at this point
              .polygonalHistogramDouble(queryPoly)

            val result: Array[(Double, Double)] =
              queryHist.map( band =>
                band.minMaxValues().getOrElse((Double.NaN, Double.NaN))
              )
            // Provides implicit conversion from Array to JSON
            import spray.json.DefaultJsonProtocol._
            complete(result)
          }
        }
      }
    } ~
    pathEndOrSingleSlash {
      getFromFile("static/index.html")
    } ~
    pathPrefix("") {
      getFromDirectory("static")
    }
}
