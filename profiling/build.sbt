name         := "zio-http"
version      := "1.0.0"
scalaVersion := "2.13.6"
lazy val zhttp = ProjectRef(uri(s"https://github.com/zio/zio-http.git#68ac888f"), "zioHttp")
lazy val root  = (project in file("."))
  .settings(
    name                             := "helloExample",
    fork                             := true,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    assembly / assemblyMergeStrategy := {
      case x if x.contains("io.netty.versions.properties") => MergeStrategy.discard
      case x                                               =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
  )
  .dependsOn(zhttp)
