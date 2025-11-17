name         := "zio-http-example-basic-auth"
version      := "0.1.0"
scalaVersion := "2.13.17"

publish / skip := true
run / fork     := true

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"      % "2.1.22",
  "dev.zio" %% "zio-http" % "3.5.1",
)

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

Compile / mainClass := Some("example.auth.basic.AuthenticationServer")

dockerBaseImage    := "eclipse-temurin:21-jre"
dockerExposedPorts := Seq(8080)
