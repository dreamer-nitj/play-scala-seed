// project/Dependencies.scala
import sbt._

object Dependencies {
  // Versions
  val playVersion = "3.0.10"
  val scalaV = "2.13.18"
  val scalatestVersion = "7.0.0"
  val slickVersion = "3.5.1"
  val h2Version = "2.2.224"
  val jjwtVersion = "0.11.5"
  val javaDotenvVersion = "5.2.2"

  // Play Framework
  val playGuice = "org.playframework" %% "play-guice" % playVersion
  val playSlick = "org.playframework" %% "play-slick" % "6.1.0"
  val playSlickEvolutions = "org.playframework" %% "play-slick-evolutions" % "6.1.0"
  val playAhcWs = "org.playframework" %% "play-ahc-ws" % playVersion
  val playJson = "org.playframework" %% "play-json" % "3.0.0"

  // Database
  val slick = "com.typesafe.slick" %% "slick" % slickVersion
  val slickHikaricp = "com.typesafe.slick" %% "slick-hikaricp" % slickVersion
  val h2 = "com.h2database" % "h2" % h2Version

  // JWT
  val jjwtApi = "io.jsonwebtoken" % "jjwt-api" % jjwtVersion
  val jjwtImpl = "io.jsonwebtoken" % "jjwt-impl" % jjwtVersion
  val jjwtJackson = "io.jsonwebtoken" % "jjwt-jackson" % jjwtVersion

  // Environment
  val javaDotenv = "io.github.cdimascio" % "java-dotenv" % javaDotenvVersion

  // Testing
  val scalatestplusPlay = "org.scalatestplus.play" %% "scalatestplus-play" % scalatestVersion % Test

  // All dependencies
  val apiDeps = Seq(
    playGuice,
    playSlick,
    playSlickEvolutions,
    playAhcWs,
    jjwtApi,
    jjwtImpl,
    jjwtJackson,
    javaDotenv,
    scalatestplusPlay
  )

  val coreDeps = Seq(
    playGuice,
    playSlick,
    playAhcWs,
    playJson,
    slick,
    slickHikaricp,
    h2,
    jjwtApi,
    jjwtImpl,
    jjwtJackson,
    javaDotenv,
    scalatestplusPlay
  )

  val commonDeps = Seq(
    scalatestplusPlay
  )
}
