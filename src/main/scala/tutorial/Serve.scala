package tutorial

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.resample._

import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.avro.codecs._

import geotrellis.vector._

import akka.actor._
import akka.io.IO
import spray.can.Http
import spray.routing.{HttpService, RequestContext}
import spray.routing.directives.CachingDirectives
import spray.http.MediaTypes
import scala.concurrent._
import com.typesafe.config.ConfigFactory

object Serve {

  var catalogPath = new java.io.File("data/catalog").getAbsolutePath
  // Create a reader that will read in the indexed tiles we produced in IngestImage.
  var fileValueReader = FileValueReader(catalogPath)
  def reader(layerId: LayerId) = fileValueReader.reader[SpatialKey, MultibandTile](layerId)

  def main(args: Array[String]): Unit = {
    implicit val system = akka.actor.ActorSystem("tutorial-system")

    // create and start our service actor
    val service =
    system.actorOf(Props(classOf[ServiceActor]), "tutorial")

    // start a new HTTP server on port 8080 with our service actor as the handler
    IO(Http) ! Http.Bind(service, "localhost", 8080)
  }
}

class ServiceActor extends Actor with HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global

  def actorRefFactory = context
  def receive = runRoute(root)

  val colorMap =
    ColorMap.fromStringDouble(ConfigFactory.load().getString("tutorial.colormap")).get

  def root =
    //pathPrefix(nd) {


      pathPrefix(Segment / IntNumber / IntNumber / IntNumber) { (nd, zoom, x, y) =>
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {

              // Read in the tile at the given z/x/y coordinates.
              val tileOpt: Option[MultibandTile] =
              try {
                Some(Serve.reader(LayerId("landsat", zoom)).read(x, y))
              } catch {
                case _: TileNotFoundError =>
                  None
              }
              if (nd == "ndvi") {
                tileOpt.map { tile =>
                  // Compute the NDVI
                  val ndvi =
                    tile.convert(DoubleConstantNoDataCellType).combineDouble(0, 1) { (r, ir) =>
                      Calculations.ndvi(r, ir);
                    }

                  // Render as a PNG
                  ndvi.renderPng(colorMap).bytes
                }
              } else {
                tileOpt.map { tile =>
                  // Compute the NDWI
                  val ndwi =
                    tile.convert(DoubleConstantNoDataCellType).combineDouble(0, 1) { (g, ir) =>
                      Calculations.ndwi(g, ir)
                    }
                  // Render as a PNG
                  ndwi.renderPng(colorMap).bytes
                }
              }
            }
          }
        }
      }
    //}
}
