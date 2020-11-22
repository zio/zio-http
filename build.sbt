import sbtcrossproject.CrossPlugin.autoImport.crossProject
import BuildHelper._

inThisBuild(
  List(
    name := "zio-http",
    organization := "dev.zio",
    homepage := Some(url("https://github.com/zio/zio-http")),
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
        "scm:git:git@github.com:zio/zio-http.git"
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
  .settings(stdSettings("zio-http-core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"        %% "zio"          % zioVersion,
      "dev.zio"        %% "zio-streams"  % zioVersion,
      "dev.zio"        %% "zio-nio"      % zioNioVersion,
      "dev.zio"        %% "zio-test"     % zioVersion % "Test",
      "dev.zio"        %% "zio-test-sbt" % zioVersion % "Test",
      "com.propensive" %% "magnolia"     % magnoliaVersion,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(schema)

lazy val schema =
  ProjectRef(uri("git://github.com/zio/zio-schema.git#main"), "core")
