package tutorial

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.file.cog._

import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent._
import com.typesafe.config.ConfigFactory
import MaskBandsRandGandNIR.{G_BAND, NIR_BAND, R_BAND}

object Serve extends App with Service {
  val catalogPath = new java.io.File("data/catalog").getAbsolutePath
  // Create a reader that will read in the indexed tiles we produced in IngestImage.
  val attributeStore = FileAttributeStore(catalogPath)
  val fileValueReader = new FileCOGValueReader(attributeStore, catalogPath)
  def reader(layerId: LayerId) = fileValueReader.reader[SpatialKey, MultibandTile](layerId)
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

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def root =
    pathPrefix(Segment / IntNumber / IntNumber / IntNumber) { (render, zoom, x, y) =>
      complete {
        Future {
          // Read in the tile at the given z/x/y coordinates.
          val tileOpt: Option[MultibandTile] =
            try {
              Some(Serve.reader(LayerId("landsat_cog", zoom)).read(x, y))
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
      pathEndOrSingleSlash {
        getFromFile("static/index.html")
      } ~
      pathPrefix("") {
        getFromDirectory("static")
      }
}
