import sbt.*

object Dependencies {
  val JwtCoreVersion                = "10.0.1"
  val NettyVersion                  = "4.1.118.Final"
  val NettyIncubatorVersion         = "0.0.26.Final"
  val ScalaCompactCollectionVersion = "2.12.0"
  val ZioVersion                    = "2.1.11"
  val ZioCliVersion                 = "0.5.0"
  val ZioJsonVersion                = "0.7.12"
  val ZioParserVersion              = "0.1.10"
  val ZioSchemaVersion              = "1.6.1"
  val SttpVersion                   = "3.3.18"
  val ZioConfigVersion              = "4.0.3"

  val `jwt-core`                 = "com.github.jwt-scala"   %% "jwt-core"                % JwtCoreVersion
  val `scala-compact-collection` = "org.scala-lang.modules" %% "scala-collection-compat" % ScalaCompactCollectionVersion

  val scalafmt         = "org.scalameta" %% "scalafmt-dynamic" % "3.8.6"
  val scalametaParsers = "org.scalameta" %% "parsers"          % "4.12.7"

  val netty =
    Seq(
      "io.netty"                   % "netty-codec-http"              % NettyVersion,
      "io.netty"                   % "netty-handler-proxy"           % NettyVersion,
      "io.netty"                   % "netty-transport-native-epoll"  % NettyVersion,
      "io.netty"                   % "netty-transport-native-epoll"  % NettyVersion classifier "linux-x86_64",
      "io.netty"                   % "netty-transport-native-epoll"  % NettyVersion classifier "linux-aarch_64",
      "io.netty"                   % "netty-transport-native-kqueue" % NettyVersion,
      "io.netty"                   % "netty-transport-native-kqueue" % NettyVersion classifier "osx-x86_64",
      "io.netty"                   % "netty-transport-native-kqueue" % NettyVersion classifier "osx-aarch_64",
      "com.aayushatharva.brotli4j" % "brotli4j"                      % "1.16.0" % "provided",
    )

  val `netty-incubator` =
    "io.netty.incubator" % "netty-incubator-transport-native-io_uring" % NettyIncubatorVersion classifier "linux-x86_64"

  val zio                   = "dev.zio" %% "zio"                 % ZioVersion
  val `zio-cli`             = "dev.zio" %% "zio-cli"             % ZioCliVersion
  val `zio-config`          = "dev.zio" %% "zio-config"          % ZioConfigVersion
  val `zio-config-magnolia` = "dev.zio" %% "zio-config-magnolia" % ZioConfigVersion
  val `zio-config-typesafe` = "dev.zio" %% "zio-config-typesafe" % ZioConfigVersion
  val `zio-json-yaml`       = "dev.zio" %% "zio-json-yaml"       % ZioJsonVersion
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
