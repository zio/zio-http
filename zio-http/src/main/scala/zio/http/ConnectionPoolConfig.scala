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
