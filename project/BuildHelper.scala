import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{HeaderLicense, headerLicense}
import sbt.*
import sbt.Keys.*
import scalafix.sbt.ScalafixPlugin.autoImport.*
import xerial.sbt.Sonatype.autoImport.*
import sbtcrossproject.CrossPlugin.autoImport.crossProjectPlatform

object BuildHelper extends ScalaSettings {
  val Scala212         = "2.12.19"
  val Scala213         = "2.13.14"
  val Scala3           = "3.3.3"
  val ScoverageVersion = "2.0.12"
  val JmhVersion       = "0.4.7"

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

  def settingsWithHeaderLicense =
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
    scalacOptions ++= stdOptions ++ extraOptions(scalaVersion.value),
    ThisBuild / scalafixDependencies ++=
      List(
        "com.github.vovapolu" %% "scaluzzi" % "0.1.23",
      ),
    Test / parallelExecution       := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings                := true,
    ThisBuild / javaOptions        := Seq(
      "-Dio.netty.leakDetectionLevel=paranoid",
      s"-DZIOHttpLogLevel=${Debug.ZIOHttpLogLevel}",
    ),
    ThisBuild / fork               := true,
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
    ThisBuild / homepage   := Some(url("https://zio.dev/zio-http")),
    ThisBuild / scmInfo    :=
      Some(
        ScmInfo(url("https://github.com/zio/zio-http"), "scm:git@github.com:zio/zio-http.git"),
      ),
    ThisBuild / developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net"),
      ),
      Developer(
        "vigoo",
        "Daniel Vigovszky",
        "daniel.vigovszky@gmail.com",
        url("https://vigoo.github.io/"),
      ),
      Developer(
        "987Nabil",
        "Nabil Abdel-Hafeez",
        "987.nabil@gmail.com",
        url("https://github.com/987Nabil"),
      ),
    ),
  )

  def platformSpecificSources(platform: String, conf: String, baseDirectory: File)(versions: String*): Seq[File] =
    for {
      platform <- List("shared", platform)
      version  <- "scala" :: versions.toList.map("scala-" + _)
      result = baseDirectory.getParentFile / platform.toLowerCase / "src" / conf / version
      if result.exists
    } yield result

  def crossPlatformSources(scalaVer: String, platform: String, conf: String, baseDir: File): Seq[sbt.File] = {
    val versions = CrossVersion.partialVersion(scalaVer) match {
      case Some((2, 12)) =>
        List("2.12", "2.12+", "2.12-2.13", "2.x")
      case Some((2, 13)) =>
        List("2.13", "2.12+", "2.13+", "2.12-2.13", "2.x")
      case Some((3, _))  =>
        List("3")
      case _             =>
        List()
    }
    platformSpecificSources(platform, conf, baseDir)(versions: _*)
  }

  lazy val crossProjectSettings = Seq(
    Compile / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "main",
        baseDirectory.value,
      )
    },
    Test / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "test",
        baseDirectory.value,
      )
    },
  )

}
