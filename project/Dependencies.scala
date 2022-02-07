import sbt._

object Dependencies {
  val JwtCoreVersion                = "9.0.3"
  val NettyVersion                  = "4.1.73.Final"
  val NettyIncubatorVersion         = "0.0.11.Final"
  val ScalaCompactCollectionVersion = "2.6.0"
  val ZioVersion                    = "2.0.0-RC2"
  val SttpVersion                   = "3.3.18"
  val ZioLoggingVersion             = "2.0.0-RC5"
  val LogbackVersion                = "1.2.3"

  val `jwt-core`                 = "com.github.jwt-scala"   %% "jwt-core"                % JwtCoreVersion
  val `scala-compact-collection` = "org.scala-lang.modules" %% "scala-collection-compat" % ScalaCompactCollectionVersion

  val netty             = "io.netty" % "netty-all" % NettyVersion
  val `netty-incubator` =
    "io.netty.incubator" % "netty-incubator-transport-native-io_uring" % NettyIncubatorVersion classifier "linux-x86_64"

  val sttp       = "com.softwaremill.sttp.client3" %% "core"                          % SttpVersion % "test"
  val `sttp-zio` = "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % SttpVersion % "test"

  val zio                 = "dev.zio"       %% "zio"               % ZioVersion
  val `zio-streams`       = "dev.zio"       %% "zio-streams"       % ZioVersion
  val `zio-test`          = "dev.zio"       %% "zio-test"          % ZioVersion % "test"
  val `zio-test-sbt`      = "dev.zio"       %% "zio-test-sbt"      % ZioVersion % "test"
  val `zio-logging`       = "dev.zio"       %% "zio-logging"       % ZioLoggingVersion
  val `zio-logging-slf4j` = "dev.zio"       %% "zio-logging-slf4j" % ZioLoggingVersion
  val logback             = "ch.qos.logback" % "logback-classic"   % LogbackVersion
}
