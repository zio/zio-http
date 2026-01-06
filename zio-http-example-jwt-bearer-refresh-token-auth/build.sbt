name         := "zio-http-example-jwt-bearer-refresh-token-auth"
version      := "0.1.0"
scalaVersion := "2.13.17"

publish / skip  := true
publishArtifact := false
run / fork := true

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"      % "2.1.22",
  "dev.zio" %% "zio-http" % "3.5.1",
  "com.github.jwt-scala" %% "jwt-core"     % "11.0.3",
  "com.github.jwt-scala" %% "jwt-zio-json" % "11.0.3",
)

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

Compile / mainClass := Some("example.auth.bearer.jwt.refresh.AuthenticationServer")

dockerBaseImage    := "eclipse-temurin:21-jre"
dockerExposedPorts := Seq(8080)
