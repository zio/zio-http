import sbt.Keys.scalaVersion
import sbt._

object Dependencies {
  val JwtCoreVersion                = "9.0.5"
  val NettyVersion                  = "4.1.77.Final"
  val NettyIncubatorVersion         = "0.0.14.Final"
  val ScalaCompactCollectionVersion = "2.7.0"
  val ZioVersion                    = "1.0.14"
  val SttpVersion                   = "3.3.18"

  val `jwt-core`                 = "com.github.jwt-scala"   %% "jwt-core"                % JwtCoreVersion
  val `scala-compact-collection` = "org.scala-lang.modules" %% "scala-collection-compat" % ScalaCompactCollectionVersion

  val netty =
    Seq(
      "netty-codec-http",
      "netty-transport-native-epoll",
      "netty-transport-native-kqueue",
    ).map { name =>
      "io.netty" % name % NettyVersion
    }

  val `netty-incubator` =
    "io.netty.incubator" % "netty-incubator-transport-native-io_uring" % NettyIncubatorVersion classifier "linux-x86_64"

  val zio            = "dev.zio" %% "zio"          % ZioVersion
  val `zio-streams`  = "dev.zio" %% "zio-streams"  % ZioVersion
  val `zio-test`     = "dev.zio" %% "zio-test"     % ZioVersion % "test"
  val `zio-test-sbt` = "dev.zio" %% "zio-test-sbt" % ZioVersion % "test"

  val reflect = Def.map(scalaVersion)("org.scala-lang" % "scala-reflect" % _)
}
