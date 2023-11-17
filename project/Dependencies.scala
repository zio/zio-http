import sbt._
import sbt.Keys.scalaVersion

object Dependencies {
  val JwtCoreVersion                = "9.1.1"
  val NettyVersion                  = "4.1.101.Final"
  val NettyIncubatorVersion         = "0.0.24.Final"
  val ScalaCompactCollectionVersion = "2.11.0"
  val ZioVersion                    = "2.0.19"
  val ZioCliVersion                 = "0.5.0"
  val ZioSchemaVersion              = "0.4.15"
  val SttpVersion                   = "3.3.18"

  val `jwt-core`                 = "com.github.jwt-scala"   %% "jwt-core"                % JwtCoreVersion
  val `scala-compact-collection` = "org.scala-lang.modules" %% "scala-collection-compat" % ScalaCompactCollectionVersion

  val netty =
    Seq(
      "io.netty" % "netty-codec-http"              % NettyVersion,
      "io.netty" % "netty-handler-proxy"           % NettyVersion,
      "io.netty" % "netty-transport-native-epoll"  % NettyVersion,
      "io.netty" % "netty-transport-native-epoll"  % NettyVersion % Runtime classifier "linux-x86_64",
      "io.netty" % "netty-transport-native-epoll"  % NettyVersion % Runtime classifier "linux-aarch_64",
      "io.netty" % "netty-transport-native-kqueue" % NettyVersion,
      "io.netty" % "netty-transport-native-kqueue" % NettyVersion % Runtime classifier "osx-x86_64",
      "io.netty" % "netty-transport-native-kqueue" % NettyVersion % Runtime classifier "osx-aarch_64",
    )

  val `netty-incubator` =
    "io.netty.incubator" % "netty-incubator-transport-native-io_uring" % NettyIncubatorVersion classifier "linux-x86_64"

  val zio                   = "dev.zio" %% "zio"                 % ZioVersion
  val `zio-cli`             = "dev.zio" %% "zio-cli"             % ZioCliVersion
  val `zio-streams`         = "dev.zio" %% "zio-streams"         % ZioVersion
  val `zio-schema`          = "dev.zio" %% "zio-schema"          % ZioSchemaVersion
  val `zio-schema-json`     = "dev.zio" %% "zio-schema-json"     % ZioSchemaVersion
  val `zio-schema-protobuf` = "dev.zio" %% "zio-schema-protobuf" % ZioSchemaVersion
  val `zio-test`            = "dev.zio" %% "zio-test"            % ZioVersion % "test"
  val `zio-test-sbt`        = "dev.zio" %% "zio-test-sbt"        % ZioVersion % "test"

}
