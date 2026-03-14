// build.sbt
import Dependencies._

ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := scalaV

// Common settings
lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings"
  ),
  // Enable Scalafmt
  scalafmtOnCompile := true,
  // Enable auto-generated API mappings
  autoAPIMappings := true
)

// Common module - shared utilities
lazy val common = (project in file("common"))
  .settings(
    commonSettings,
    name := "play-scala-seed-common",
    libraryDependencies ++= commonDeps
  )

// Core module - business logic
lazy val core = (project in file("core"))
  .dependsOn(common)
  .settings(
    commonSettings,
    name := "play-scala-seed-core",
    libraryDependencies ++= coreDeps
  )

// API module - Play web application
lazy val api = (project in file("api"))
  .dependsOn(core)
  .enablePlugins(PlayScala)
  .settings(
    commonSettings,
    name := "play-scala-seed-api",
    libraryDependencies ++= apiDeps,
    // Play-specific settings
    PlayKeys.playDefaultPort := 9000,
    // Routes import
    routesImport ++= Seq(
      "models._",
      "utils._"
    )
  )

// Root project
lazy val root = (project in file("."))
  .aggregate(common, core, api)
  .settings(
    name := "play-scala-seed",
    // Don't publish the root project
    publish / skip := true
  )
