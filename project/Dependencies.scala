import sbt._

object Dependencies {
  val JwtCoreVersion                = "9.0.3"
  val NettyVersion                  = "4.1.73.Final"
  val NettyIncubatorVersion         = "0.0.11.Final"
  val ScalaCompactCollectionVersion = "2.6.0"
  val ZioVersion                    = "1.0.13"
  val SttpVersion                   = "3.3.18"

  val `jwt-core`                 = "com.github.jwt-scala"   %% "jwt-core"                % JwtCoreVersion
  val `scala-compact-collection` = "org.scala-lang.modules" %% "scala-collection-compat" % ScalaCompactCollectionVersion

  val netty             = "io.netty" % "netty-all" % NettyVersion
  val `netty-incubator` =
    "io.netty.incubator" % "netty-incubator-transport-native-io_uring" % NettyIncubatorVersion classifier "linux-x86_64"

  val zio            = "dev.zio" %% "zio"          % ZioVersion
  val `zio-streams`  = "dev.zio" %% "zio-streams"  % ZioVersion
  val `zio-test`     = "dev.zio" %% "zio-test"     % ZioVersion % "test"
  val `zio-test-sbt` = "dev.zio" %% "zio-test-sbt" % ZioVersion % "test"
}
