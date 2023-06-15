import sbt.Keys._
import sbt._
import scalafix.sbt.ScalafixPlugin.autoImport._
import xerial.sbt.Sonatype.autoImport._
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{headerLicense, HeaderLicense}

object BuildHelper extends ScalaSettings {
  val Scala212         = "2.12.17"
  val Scala213         = "2.13.10"
  val Scala3           = "3.2.2"
  val ScoverageVersion = "1.9.3"
  val JmhVersion       = "0.4.3"
  val SilencerVersion  = "1.7.13"

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
      case Some((3, _))  => scala3Settings
      case Some((2, 12)) => scala212Settings
      case Some((2, 13)) => scala213Settings
      case _             => Seq.empty
    }

  def settingsWithHeaderLicense() =
    headerLicense := Some(HeaderLicense.ALv2("2021 - 2023", "Sporta Technologies PVT LTD & the ZIO HTTP contributors."))

  def publishSetting(publishArtifacts: Boolean) = {
    val publishSettings = Seq(
      organization           := "dev.zio",
      organizationName       := "zio",
      licenses               := Seq("Apache-2.0" -> url("https://github.com/zio/zio-http/blob/master/LICENSE")),
      sonatypeCredentialHost := "oss.sonatype.org",
      sonatypeRepository     := "https://oss.sonatype.org/service/local",
      sonatypeProfileName    := "dev.zio",
      publishTo              := sonatypePublishToBundle.value,
      sonatypeTimeoutMillis  := 300 * 60 * 1000,
      publishMavenStyle      := true,
      credentials ++=
        (for {
          username <- Option(System.getenv().get("SONATYPE_USERNAME"))
          password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
        } yield Credentials(
          "Sonatype Nexus Repository Manager",
          "oss.sonatype.org",
          username,
          password,
        )).toSeq,
    )
    val skipSettings    = Seq(
      publish / skip  := true,
      publishArtifact := false,
    )
    if (publishArtifacts) publishSettings else publishSettings ++ skipSettings
  }

  def stdSettings(prjName: String) = Seq(
    name                           := s"$prjName$shadedSuffix",
    ThisBuild / crossScalaVersions := Seq(Scala212, Scala213, Scala3),
    ThisBuild / scalaVersion       := Scala213,
    scalacOptions                  := stdOptions ++ extraOptions(scalaVersion.value),
    ThisBuild / scalafixDependencies ++=
      List(
        "com.github.vovapolu"  %% "scaluzzi"         % "0.1.23",
      ),
    Test / parallelExecution       := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings                := true,
    ThisBuild / javaOptions        := Seq(
      "-Dio.netty.leakDetectionLevel=paranoid",
      s"-DZIOHttpLogLevel=${Debug.ZIOHttpLogLevel}",
    ),
    ThisBuild / fork               := true,
    libraryDependencies ++= {
      if (scalaVersion.value == Scala3)
        Seq(
          "com.github.ghik" % s"silencer-lib_$Scala213" % SilencerVersion % Provided,
        )
      else
        Seq(
          "com.github.ghik" % "silencer-lib"            % SilencerVersion % Provided cross CrossVersion.full,
          compilerPlugin("com.github.ghik" % "silencer-plugin" % SilencerVersion cross CrossVersion.full),
        )
    },
    semanticdbEnabled              := scalaVersion.value != Scala3,
    semanticdbOptions += "-P:semanticdb:synthetics:on",
    semanticdbVersion              := {
      if (scalaVersion.value == Scala3) semanticdbVersion.value
      else scalafixSemanticdb.revision
    },
  )

  private def shadedSuffix = {
    if (Shading.shadingEnabled) "-shaded" else ""
  }

  def runSettings(className: String = "example.HelloWorld") = Seq(
    fork                      := true,
    Compile / run / mainClass := Option(className),
  )

  def meta = Seq(
    ThisBuild / homepage   := Some(url("https://github.com/zio/zio-http")),
    ThisBuild / scmInfo    :=
      Some(
        ScmInfo(url("https://github.com/zio/zio-http"), "scm:git@github.com:zio/zio-http.git"),
      ),
    ThisBuild / developers := List(
      Developer(
        "tusharmath",
        "Tushar Mathur",
        "tusharmath@gmail.com",
        new URL("https://github.com/tusharmath"),
      ),
      Developer(
        "amitksingh1490",
        "Amit Kumar Singh",
        "amitksingh1490@gmail.com",
        new URL("https://github.com/amitksingh1490"),
      ),
    ),
  )
}
