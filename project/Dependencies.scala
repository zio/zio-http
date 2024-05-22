import sbt.*

object Dependencies {
  val JwtCoreVersion                = "10.0.1"
  val NettyVersion                  = "4.1.109.Final"
  val NettyIncubatorVersion         = "0.0.25.Final"
  val ScalaCompactCollectionVersion = "2.12.0"
  val ZioVersion                    = "2.1.1"
  val ZioCliVersion                 = "0.5.0"
  val ZioSchemaVersion              = "1.1.1"
  val SttpVersion                   = "3.3.18"
  val ZioConfigVersion              = "4.0.2"

  val `jwt-core`                 = "com.github.jwt-scala"   %% "jwt-core"                % JwtCoreVersion
  val `scala-compact-collection` = "org.scala-lang.modules" %% "scala-collection-compat" % ScalaCompactCollectionVersion

  val scalafmt = "org.scalameta" %% "scalafmt-dynamic" % "3.8.1"
  val scalametaParsers = "org.scalameta" %% "parsers" % "4.9.4"

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
  val `zio-schema-avro`     = "dev.zio" %% "zio-schema-avro"     % ZioSchemaVersion
  val `zio-schema-thrift`   = "dev.zio" %% "zio-schema-thrift"   % ZioSchemaVersion
  val `zio-schema-msg-pack` = "dev.zio" %% "zio-schema-msg-pack" % ZioSchemaVersion
  val `zio-test`            = "dev.zio" %% "zio-test"            % ZioVersion % "test"
  val `zio-test-sbt`        = "dev.zio" %% "zio-test-sbt"        % ZioVersion % "test"

}
