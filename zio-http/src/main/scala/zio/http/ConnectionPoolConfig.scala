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

sealed trait ConnectionPoolConfig
object ConnectionPoolConfig {
  case object Disabled                                                                    extends ConnectionPoolConfig
  final case class Fixed(size: Int)                                                       extends ConnectionPoolConfig
  final case class FixedPerHost(sizes: Map[URL.Location.Absolute, Fixed], default: Fixed) extends ConnectionPoolConfig
  final case class Dynamic(minimum: Int, maximum: Int, ttl: Duration)                     extends ConnectionPoolConfig
  final case class DynamicPerHost(configs: Map[URL.Location.Absolute, Dynamic], default: Dynamic)
      extends ConnectionPoolConfig

  lazy val config: Config[ConnectionPoolConfig] = {
    val disabled       = Config.string.mapOrFail {
      case "disabled" => Right(Disabled)
      case other      => Left(Config.Error.InvalidData(message = s"Invalid value for ConnectionPoolConfig: $other"))
    }
    val fixed          = Config.int("fixed").map(Fixed)
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
              .fromString(s)
              .left
              .map(error => Config.Error.InvalidData(message = s"Invalid URL: ${error.getMessage}"))
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
              .fromString(s)
              .left
              .map(error => Config.Error.InvalidData(message = s"Invalid URL: ${error.getMessage}"))
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
