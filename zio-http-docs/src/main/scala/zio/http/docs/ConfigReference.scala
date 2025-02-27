package zio.http.docs

import zio.config.generateDocs
import zio._

object ConfigReference {
  private type ObjectWithConfig = Object { def config: Config[Any] }

  private val configs =
    Seq[ObjectWithConfig](
      http.netty.NettyConfig,
      http.netty.NettyConfig.LeakDetectionLevel,
      http.ConnectionPoolConfig,
      http.Decompression,
      http.DnsResolver.Config,
      http.DnsResolver.ExpireAction,
      http.Proxy,
      http.Server.Config,
      http.URL,
      // http.ZClient.Config, // TODO: causes stack overflow
      // http.gen.openapi.Config, // TODO
    ).map { obj =>
      (
        obj.getClass.getName.stripSuffix("$").replace("$", "."),
        obj.config,
      )
    }

  def build(): String = {
    configs
      .map(item => {
        val (name, config) = item

        s"# ${name}.config\n" +
          generateDocs(config).toTable.toGithubFlavouredMarkdown
      })
      .mkString("\n")
  }
}
