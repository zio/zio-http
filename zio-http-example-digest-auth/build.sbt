name         := "zio-http-example-digest-auth"
version      := "0.1.1"
scalaVersion := "2.13.17"

publish / skip  := true
publishArtifact := false
run / fork := true

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"      % "2.1.22",
  "dev.zio" %% "zio-http" % "3.5.1",
)

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

Compile / mainClass := Some("example.auth.digest.AuthenticationServer")

dockerBaseImage    := "eclipse-temurin:21-jre"
dockerExposedPorts := Seq(8080)
