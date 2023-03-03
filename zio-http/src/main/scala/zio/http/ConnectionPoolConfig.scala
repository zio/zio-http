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

import zio.Duration

sealed trait ConnectionPoolConfig
object ConnectionPoolConfig {
  case object Disabled                                                                    extends ConnectionPoolConfig
  final case class Fixed(size: Int)                                                       extends ConnectionPoolConfig
  final case class FixedPerHost(sizes: Map[URL.Location.Absolute, Fixed], default: Fixed) extends ConnectionPoolConfig
  final case class Dynamic(minimum: Int, maximum: Int, ttl: Duration)                     extends ConnectionPoolConfig
  final case class DynamicPerHost(configs: Map[URL.Location.Absolute, Dynamic], default: Dynamic)
      extends ConnectionPoolConfig
}
