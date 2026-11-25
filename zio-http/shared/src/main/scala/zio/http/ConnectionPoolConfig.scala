/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio.{Config, Duration}

/**
 * How a client pools its connections. Set it with
 * `ZClient.Config#connectionPool`; the default is `Fixed(10)`.
 *
 * A pool is kept per destination: the scheme, host and port of the request,
 * together with its proxy, TLS, timeout, header size and decompression
 * settings. The connections of the pools `Fixed`, `FixedPerHost`, `Dynamic` and
 * `DynamicPerHost` are opened without the client's `localAddress`; the pool
 * `Disabled` binds to it.
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/configs/connection-pool/ ConnectionPoolConfig]]
 */
sealed trait ConnectionPoolConfig

/** The pool configurations, and a `Config` reader for them. */
object ConnectionPoolConfig {

  /**
   * No pooling: each request opens a connection, and keep-alive is not used.
   */
  case object Disabled extends ConnectionPoolConfig

  /** A pool of `size` connections for each destination. */
  final case class Fixed(size: Int) extends ConnectionPoolConfig

  /**
   * A pool of fixed size for each destination. A destination found in `sizes`
   * uses that size, any other uses `default`.
   */
  final case class FixedPerHost(sizes: Map[URL.Location.Absolute, Fixed], default: Fixed) extends ConnectionPoolConfig

  /**
   * A pool that holds between `minimum` and `maximum` connections for each
   * destination. Connections beyond the minimum that stay unused for `ttl` are
   * released.
   */
  final case class Dynamic(minimum: Int, maximum: Int, ttl: Duration) extends ConnectionPoolConfig

  /**
   * A dynamic pool for each destination. A destination found in `configs` uses
   * that `Dynamic` configuration, any other uses `default`.
   */
  final case class DynamicPerHost(configs: Map[URL.Location.Absolute, Dynamic], default: Dynamic)
      extends ConnectionPoolConfig

  /**
   * Reads a configuration, trying these shapes in order:
   *
   *   - the string `disabled` gives `Disabled`;
   *   - the integer `fixed` gives `Fixed`;
   *   - `dynamic.minimum`, `dynamic.maximum` and `dynamic.ttl` give `Dynamic`;
   *   - `fixed.per-host` entries with `url` and `fixed`, and
   *     `fixed.default.fixed`, give `FixedPerHost`;
   *   - the corresponding `dynamic.per-host` and `dynamic.default` entries give
   *     `DynamicPerHost`.
   *
   * The `url` of a per-host entry must be an absolute URL.
   */
  def config: Config[ConnectionPoolConfig] = {
    val disabled       = Config.string.mapOrFail {
      case "disabled" => Right(Disabled)
      case other      => Left(Config.Error.InvalidData(message = s"Invalid value for ConnectionPoolConfig: $other"))
    }
    val fixed          = Config.int("fixed").map(Fixed.apply)
    val dynamic        =
      (Config.int("minimum") ++
        Config.int("maximum") ++
        Config.duration("ttl")).nested("dynamic").map { case (minimum, maximum, ttl) =>
        Dynamic(minimum, maximum, ttl)
      }
    val fixedPerHost   =
      (Config
        .chunkOf(
          "per-host",
          (Config.string("url") ++ fixed).mapOrFail { case (s, fixed) =>
            URL
              .decode(s)
              .left
              .map(error => Config.Error.InvalidData(message = error.getMessage))
              .flatMap { url =>
                url.kind match {
                  case url: URL.Location.Absolute => Right(url -> fixed)
                  case _ => Left(Config.Error.InvalidData(message = s"Invalid value for ConnectionPoolConfig: $s"))
                }
              }
          },
        ) ++
        fixed.nested("default")).nested("fixed").map { case (perHost, default) =>
        FixedPerHost(perHost.toMap, default)
      }
    val dynamicPerHost =
      (Config
        .chunkOf(
          "per-host",
          (Config.string("url") ++ dynamic).mapOrFail { case (s, fixed) =>
            URL
              .decode(s)
              .left
              .map(error => Config.Error.InvalidData(message = error.getMessage))
              .flatMap { url =>
                url.kind match {
                  case url: URL.Location.Absolute => Right(url -> fixed)
                  case _ => Left(Config.Error.InvalidData(message = s"Invalid value for ConnectionPoolConfig: $s"))
                }
              }
          },
        ) ++
        dynamic.nested("default")).nested("dynamic").map { case (perHost, default) =>
        DynamicPerHost(perHost.toMap, default)
      }

    disabled orElse fixed orElse dynamic orElse fixedPerHost orElse dynamicPerHost
  }
}
