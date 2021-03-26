import sbt._
import Keys._
import dotty.tools.sbtplugin.DottyPlugin.autoImport._
import scalafix.sbt.ScalafixPlugin.autoImport._

object BuildHelper extends ScalaSettings {
  val Scala213   = "2.13.5"
  val ScalaDotty = "3.0.0-RC1"

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

  private val std2xOptions = Seq(
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
  )

  private def optimizerOptions(optimize: Boolean) =
    if (optimize)
      Seq(
        "-opt:l:inline",
      )
    else Nil

  def extraOptions(scalaVersion: String, isDotty: Boolean, optimize: Boolean) =
    CrossVersion.partialVersion(scalaVersion) match {
      case _ if isDotty  =>
        Seq(
          "-language:implicitConversions",
          "-Xignore-scala2-macros",
          "-noindent",
        )
      case Some((2, 13)) =>
        Seq("-Ywarn-unused:params,-implicits") ++ std2xOptions ++ tpoleCatSettings ++ optimizerOptions(optimize)
      case _             => Seq.empty
    }

  def stdSettings(prjName: String) = Seq(
    name := s"$prjName",
    crossScalaVersions in ThisBuild := Seq(Scala213, ScalaDotty),
    scalaVersion in ThisBuild := Scala213,
    useScala3doc := true,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value, isDotty.value, optimize = !isSnapshot.value),
    scalacOptions --= {
      if (isDotty.value)
        Seq("-Xfatal-warnings")
      else
        Seq()
    },
    semanticdbEnabled := !isDotty.value,              // enable SemanticDB
    semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
    ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
    ThisBuild / scalafixDependencies ++=
      List(
        "com.github.liancheng" %% "organize-imports" % "0.5.0",
        "com.github.vovapolu"  %% "scaluzzi"         % "0.1.16",
      ),
    parallelExecution in Test := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true,
  )
}
