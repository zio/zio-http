import sbt._

object Dependencies {
  val JwtCoreVersion                = "9.0.2"
  val NettyVersion                  = "4.1.72.Final"
  val NettyIncubatorVersion         = "0.0.11.Final"
  val ScalaCompactCollectionVersion = "2.6.0"
  val ZioVersion                    = "1.0.13"
  val sttpVersion                   = "3.3.18"
  val `scala-compact-collection` = "org.scala-lang.modules" %% "scala-collection-compat" % ScalaCompactCollectionVersion
  val netty                      = "io.netty"                % "netty-all"               % NettyVersion
  val jaf                        = "com.sun.activation"      % "jakarta.activation"      % "2.0.0"
  val `jwt-core`                 = "com.github.jwt-scala"   %% "jwt-core"                % JwtCoreVersion
  val sttp              = "com.softwaremill.sttp.client3" %% "core"                          % sttpVersion % "test"
  val sttpzio           = "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttpVersion % "test"
  val zio               = "dev.zio"                       %% "zio"                           % ZioVersion
  val `zio-streams`     = "dev.zio"                       %% "zio-streams"                   % ZioVersion
  val `zio-test`        = "dev.zio"                       %% "zio-test"                      % ZioVersion  % "test"
  val `zio-test-sbt`    = "dev.zio"                       %% "zio-test-sbt"                  % ZioVersion  % "test"
  val `netty-incubator` =
    "io.netty.incubator" % "netty-incubator-transport-native-io_uring" % NettyIncubatorVersion classifier "linux-x86_64"
}
