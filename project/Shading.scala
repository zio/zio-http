import Dependencies._
import coursier.ShadingPlugin
import coursier.ShadingPlugin.autoImport._
import sbt.Plugins

object Shading {
  object env {
    val PUBLISH_SHADED = "PUBLISH_SHADED"
  }

  object sysprops {
    val `publish.shaded` = "publish.shaded"
  }

  // `zio-http/js/src/main/scala/com/lihaoyi/unroll.scala` is a Scala.js-only stub for the
  // JVM-only `unroll-annotation` artifact (see #4388). It is compiled into the zio-http JS
  // classes, so it ends up in the shaded JS jar. The namespace check only accepts the
  // `validNamespaces` prefixes (`zio`, `org`) plus explicitly listed entries, hence these.
  private val scalaJsUnrollStubEntries = Set(
    "com/",
    "com/lihaoyi/",
    "com/lihaoyi/unroll.class",
    "com/lihaoyi/unroll.sjsir",
    "com/lihaoyi/unroll.tasty",
  )

  def shadingSettings() = if (shadingEnabled) {
    Seq(
      shadedModules ++= (netty :+ `netty-pkitesting`).map(_.module).toSet,
      shadingRules += ShadingRule.rename("io.netty.**", "zio.http.shaded.netty.@1"),
      validNamespaces ++= Set("zio", "org"),
      validEntries ++= scalaJsUnrollStubEntries,
    )
  } else Nil

  lazy val shadingEnabled = {
    val enabled = sys.props.get(sysprops.`publish.shaded`).fold(false)(_.toBoolean) ||
      sys.env.get(env.PUBLISH_SHADED).fold(false)(_.toBoolean)
    println(s"*** shading enabled: $enabled (env.PUBLISH_SHADED=${sys.env
        .get(env.PUBLISH_SHADED)}, sysprops.`publish.shaded`=${sys.props.get(sysprops.`publish.shaded`)})")
    enabled
  }

  def plugins(): Seq[Plugins] = if (shadingEnabled) Seq(ShadingPlugin) else Nil
}
