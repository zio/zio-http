import BuildHelper._
import Dependencies._

name := "zio-http-core"

crossScalaVersions := Seq(Scala212, Scala213, Scala3)

libraryDependencies ++= Seq(
  Zio,
  ZioStream,
  ZioSchema,
  ZioSchemaJson,
  ZioParser,
)

// No Netty dependency for core module
