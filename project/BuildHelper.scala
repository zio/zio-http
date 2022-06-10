import sbt.Keys._
import sbt._
import scalafix.sbt.ScalafixPlugin.autoImport._
import xerial.sbt.Sonatype.autoImport._

object BuildHelper extends ScalaSettings {
  val Scala212           = "2.12.15"
  val Scala213           = "2.13.8"
  val ScalaDotty         = "3.1.2"
  val ScoverageVersion   = "1.9.3"
  val JmhVersion         = "0.4.3"
  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-language:postfixOps",
  ) ++ {
    if (sys.env.contains("CI")) {
      Seq("-Xfatal-warnings")
    } else {
      Nil // to enable Scalafix locally
    }
  }

  def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, 0))  => scala3Settings
      case Some((2, 12)) => scala212Settings
      case Some((2, 13)) => scala213Settings
      case _             => Seq.empty
    }

  def publishSetting(publishArtifacts: Boolean) = {
    val publishSettings = Seq(
      organization           := "io.d11",
      organizationName       := "d11",
      licenses += ("MIT License", new URL("https://github.com/dream11/zio-http/blob/master/LICENSE")),
      sonatypeCredentialHost := "s01.oss.sonatype.org",
      sonatypeRepository     := "https://s01.oss.sonatype.org/service/local",
      sonatypeProfileName    := "io.d11",
    )
    val skipSettings    = Seq(
      publish / skip  := true,
      publishArtifact := false,
    )
    if (publishArtifacts) publishSettings else publishSettings ++ skipSettings
  }

  def stdSettings(prjName: String) = Seq(
    name                                   := s"$prjName",
    ThisBuild / crossScalaVersions         := Seq(Scala212, Scala213, ScalaDotty),
    ThisBuild / scalaVersion               := Scala213,
    scalacOptions                          := stdOptions ++ extraOptions(scalaVersion.value),
    semanticdbVersion                      := scalafixSemanticdb.revision, // use Scalafix compatible version
    ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
    ThisBuild / scalafixDependencies ++=
      List(
        "com.github.liancheng" %% "organize-imports" % "0.5.0",
        "com.github.vovapolu"  %% "scaluzzi"         % "0.1.16",
      ),
    Test / parallelExecution               := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings                        := true,
    ThisBuild / javaOptions                := Seq("-Dio.netty.leakDetectionLevel=paranoid", "-DZHttpLogLevel=INFO"),
    ThisBuild / fork                       := true,
  )

  def runSettings(className: String = "example.HelloWorld") = Seq(
    fork                      := true,
    Compile / run / mainClass := Option(className),
  )

  def meta = Seq(
    ThisBuild / homepage   := Some(url("https://github.com/dream11/zio-http")),
    ThisBuild / scmInfo    :=
      Some(
        ScmInfo(url("https://github.com/dream11/zio-http"), "scm:git@github.com:dream11/zio-http.git"),
      ),
    ThisBuild / developers := List(
      Developer(
        "tusharmath",
        "Tushar Mathur",
        "tushar@dream11.com",
        new URL("https://github.com/tusharmath"),
      ),
      Developer(
        "amitksingh1490",
        "Amit Kumar Singh",
        "amit.singh@dream11.com",
        new URL("https://github.com/amitksingh1490"),
      ),
    ),
  )
}
