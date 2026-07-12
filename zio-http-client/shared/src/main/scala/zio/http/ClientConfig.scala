package zio.http

import java.time.Duration

import zio.blocks.schema.Schema

/**
 * Configuration for the HTTP client.
 *
 * Load from an external source via:
 * {{{
 *   import zio.blocks.config.{Config, ConfigSource}
 *   val cfg: Either[_, ClientConfig] =
 *     Config.load[ClientConfig](ConfigSource.fromSystemProperties())
 * }}}
 */
final case class ClientConfig(
  connectTimeout: Duration = Duration.ofSeconds(10),
  requestTimeout: Duration = Duration.ofSeconds(30),
  followRedirects: Boolean = true,
  maxRedirects: Int        = 5,
)

object ClientConfig {
  implicit val schema: Schema[ClientConfig] = Schema.derived[ClientConfig]
}
