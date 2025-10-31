name         := "zio-http-example-webauthn"
version      := "0.1.1"
scalaVersion := "2.13.17"

run / fork := true

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"      % "2.1.22",
  "dev.zio" %% "zio-http" % "3.5.1",
  "dev.zio" %% "zio-config" % "4.0.5",
  "com.yubico" % "webauthn-server-core"        % "2.7.0",
  "com.yubico" % "webauthn-server-attestation" % "2.7.0",
)

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

Compile / mainClass := Some("example.auth.webauthn.WebAuthnServer")

dockerBaseImage    := "eclipse-temurin:21-jre"
dockerExposedPorts := Seq(8080)
