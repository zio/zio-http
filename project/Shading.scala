import Dependencies.*
import coursier.ShadingPlugin
import coursier.ShadingPlugin.autoImport.*
import sbt.Plugins

object Shading {
  def shadingSettings() = if (shadingEnabled) {
    Seq(
      shadedModules ++= (netty :+ `netty-incubator`).map(_.module).toSet,
      shadingRules += ShadingRule.rename("io.netty.**", "zio.http.shaded.netty.@1"),
      validNamespaces += "zio",
    )
  } else Nil

  def shadingEnabled = {
    sys.props.get("publish.shaded").fold(false)(_.toBoolean)
  }

  def plugins(): Seq[Plugins] = if(shadingEnabled) Seq(ShadingPlugin) else Nil
}
