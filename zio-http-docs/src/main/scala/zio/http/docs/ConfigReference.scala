package zio.http.docs

import scala.language.reflectiveCalls

import zio._

import zio.config.generateDocs

object ConfigReference {
  private type ObjectWithConfig = Object { def config: Config[Any] }

  private val configs =
    Seq[ObjectWithConfig](
      http.netty.NettyConfig,
      http.ConnectionPoolConfig,
      http.DnsResolver.Config,
      http.Proxy,
      http.Server.Config,
      http.URL,
      // http.ZClient.Config, // TODO: causes stack overflow
      // http.gen.openapi.Config, // TODO
    )

  def build(): String = {
    configs
      .map { obj =>
        val name = obj.getClass.getName.stripSuffix("$").replace("$", ".")

        s"# ${name}.config\n" +
          generateDocs(obj.config).toTable.toGithubFlavouredMarkdown
      }
      .mkString("\n")
  }
}
