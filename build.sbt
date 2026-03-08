name         := """play-scala-seed"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.18"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.5.1",
  // "com.typesafe.slick" %% "slick-hikaricp"  % "3.5.1",
  "com.h2database" % "h2" % "2.2.224"
  // "org.slf4j"           % "slf4j-nop"       % "2.0.12"
)

libraryDependencies ++= Seq(
  "org.playframework" %% "play-slick"            % "6.1.0",
  "org.playframework" %% "play-slick-evolutions" % "6.1.0"
)

// libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.6.21"

libraryDependencies ++= Seq(
  "io.jsonwebtoken" % "jjwt-api"     % "0.11.5",
  "io.jsonwebtoken" % "jjwt-impl"    % "0.11.5",
  "io.jsonwebtoken" % "jjwt-jackson" % "0.11.5"
)

// libraryDependencies += "org.playframework" %% "play-ws" % "2.9.4"
// libraryDependencies += "org.playframework" %% "play-ws" % "2.8.18"
libraryDependencies += "org.playframework" %% "play-ahc-ws" % "3.0.1"

// to load environment variables from .env file
libraryDependencies += "io.github.cdimascio" % "java-dotenv" % "5.2.2"

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"
