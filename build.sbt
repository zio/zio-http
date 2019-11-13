import java.time.LocalDate

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
  .settings(skip in publish := true)
  .aggregate(
    core
  )

lazy val core = project
  .enablePlugins(AutomateHeaderPlugin)
  .in(file("core"))
  .settings(stdSettings("zio-http-core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"     % zioVersion,
      "dev.zio" %% "zio-nio" % zioNioVersion
    )
  )
  .settings(
    headerLicense := Some(
      HeaderLicense.Custom(
        s"""|
            | Copyright 2017-${LocalDate.now().getYear} John A. De Goes and the ZIO Contributors
            |
            | Licensed under the Apache License, Version 2.0 (the "License");
            | you may not use this file except in compliance with the License.
            | You may obtain a copy of the License at
            |
            |     http://www.apache.org/licenses/LICENSE-2.0
            |
            | Unless required by applicable law or agreed to in writing, software
            | distributed under the License is distributed on an "AS IS" BASIS,
            | WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            | See the License for the specific language governing permissions and
            | limitations under the License.
            |
            |""".stripMargin
      )
    )
  )
