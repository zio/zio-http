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

  def shadingSettings() = if (shadingEnabled) {
    Seq(
      shadedModules ++= (netty :+ `netty-incubator`).map(_.module).toSet,
      shadingRules += ShadingRule.rename("io.netty.**", "zio.http.shaded.netty.@1"),
      validNamespaces += "zio",
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
