addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.10")
addSbtPlugin("org.foundweekends.giter8" % "sbt-giter8-scaffold" % "0.18.0")

// Code formatting and quality
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
// addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.2.0")

// Build and packaging
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.0")

// Development tools
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
