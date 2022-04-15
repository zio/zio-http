import sbt._

object Dependencies {
  val JwtCoreVersion                = "9.0.5"
  val NettyVersion                  = "4.1.76.Final"
  val NettyIncubatorVersion         = "0.0.13.Final"
  val ScalaCompactCollectionVersion = "2.7.0"
  val ZioVersion                    = "1.0.14"
  val ZioSchemaVersion              = "0.1.9"
  val ZioJsonVersion                = "0.2.0-M3"
  val SttpVersion                   = "3.3.18"

  val `jwt-core`                 = "com.github.jwt-scala"   %% "jwt-core"                % JwtCoreVersion
  val `scala-compact-collection` = "org.scala-lang.modules" %% "scala-collection-compat" % ScalaCompactCollectionVersion

  val netty             = "io.netty" % "netty-all" % NettyVersion
  val `netty-incubator` =
    "io.netty.incubator" % "netty-incubator-transport-native-io_uring" % NettyIncubatorVersion classifier "linux-x86_64"

  val zio               = "dev.zio" %% "zio"             % ZioVersion
  val `zio-streams`     = "dev.zio" %% "zio-streams"     % ZioVersion
  val `zio-test`        = "dev.zio" %% "zio-test"        % ZioVersion % "test"
  val `zio-test-sbt`    = "dev.zio" %% "zio-test-sbt"    % ZioVersion % "test"
  val `zio-schema`      = "dev.zio" %% "zio-schema"      % ZioSchemaVersion
  val `zio-schema-json` = "dev.zio" %% "zio-schema-json" % ZioSchemaVersion
  val `zio-json`        = "dev.zio" %% "zio-json"        % ZioJsonVersion
}
