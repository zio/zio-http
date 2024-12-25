lazy val root = (project in file("."))
  .enablePlugins(ZioHttpCodegen)
  .settings(
    name := "zoo-sdk",
    organization := "dev.zoo",
    scalaVersion := "2.13.15",
    libraryDependencies +="dev.zio" %% "zio-http" % "3.0.1"
  )