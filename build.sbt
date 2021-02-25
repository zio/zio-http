import BuildHelper._
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.{ buildInfoKeys, buildInfoPackage }

inThisBuild(
  List(
    name := "zio-web",
    organization := "dev.zio",
    homepage := Some(url("https://github.com/zio/zio-web")),
    developers := List(
      Developer(
        "ioleo",
        "Piotr Gołębiewski",
        "ioleo+zio@protonmail.com",
        url("https://github.com/ioleo")
      ),
      Developer(
        "jczuchnowski",
        "Jakub Czuchnowski",
        "jakub.czuchnowski@gmail.com",
        url("https://github.com/jczuchnowski")
      )
    ),
    scmInfo := Some(
      ScmInfo(
        homepage.value.get,
        "scm:git:git@github.com:zio/zio-web.git"
      )
    ),
    licenses := Seq("Apache-2.0" -> url(s"${scmInfo.value.map(_.browseUrl).get}/blob/v${version.value}/LICENSE")),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc")
  )
)

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("testRelease", ";set every isSnapshot := false;+clean;+compile")

lazy val root = project
  .in(file("."))
  .settings(
    name := "zio-web",
    skip in publish := true
  )
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-web-core"))
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "zio.web",
    libraryDependencies ++= Seq(
      "dev.zio"        %% "zio"             % zioVersion,
      "dev.zio"        %% "zio-logging"     % zioLoggingVersion,
      "dev.zio"        %% "zio-streams"     % zioVersion,
      "dev.zio"        %% "zio-schema-core" % zioSchemaVersion,
      "dev.zio"        %% "zio-nio"         % zioNioVersion,
      "dev.zio"        %% "zio-json"        % zioJsonVersion,
      "dev.zio"        %% "zio-test"        % zioVersion % Test,
      "dev.zio"        %% "zio-test-sbt"    % zioVersion % Test,
      "com.propensive" %% "magnolia"        % magnoliaVersion,
      "org.scala-lang" % "scala-reflect"    % scalaVersion.value % Provided
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val docs = project
  .in(file("zio-web-docs"))
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .dependsOn(core)
  .settings(
    moduleName := "zio-web-docs"
  )
