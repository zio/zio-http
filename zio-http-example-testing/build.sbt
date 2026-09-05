ThisBuild / scalaVersion := "3.9.0"

name := "zio-http-example-testing"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"              % "2.1.25",
  "dev.zio" %% "zio-http"         % "3.11.1",
  "dev.zio" %% "zio-test"         % "2.1.25" % Test,
  "dev.zio" %% "zio-test-sbt"     % "2.1.25" % Test,
  "dev.zio" %% "zio-http-testkit" % "3.11.1" % Test,
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

Test / parallelExecution := false
