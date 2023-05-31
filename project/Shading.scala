import Dependencies._
import coursier.ShadingPlugin.autoImport._

object Shading {
  def shadingSettings(enabled: Boolean = true) = Seq(
    shadedModules ++= (netty :+ `netty-incubator`).map(_.module).toSet,
    shadingRules += ShadingRule.rename("io.netty.**", "zio.http.shaded.netty.@1"),
    validNamespaces += "zio",
  )
}
