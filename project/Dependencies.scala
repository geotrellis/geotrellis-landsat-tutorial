import scala.util.Properties

import sbt._

object Dependencies {
  def either(environmentVariable: String, default: String): String =
    Properties.envOrElse(environmentVariable, default)

  private val sprayVersion = Properties.envOrElse("SPRAY_VERSION", "1.3.3")

  val sprayRouting =
    if(sprayVersion == "1.2.3") { 
      "io.spray"        % "spray-routing" % sprayVersion
    } else {
      "io.spray"        %% "spray-routing" % sprayVersion
    }

  val sprayCan =
    if(sprayVersion == "1.2.3") { 
      "io.spray"        % "spray-can" % sprayVersion
    } else {
      "io.spray"        %% "spray-can" % sprayVersion
    }
}
