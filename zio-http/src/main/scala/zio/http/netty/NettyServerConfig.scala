package zio.http.netty

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Trace, ZLayer}

import zio.http.netty.NettyServerConfig.LeakDetectionLevel

import io.netty.util.ResourceLeakDetector

final case class NettyServerConfig(
  leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
) { self =>

  /**
   * Configure the server to use the leak detection level provided (@see <a
   * href="https://netty.io/4.1/api/io/netty/util/ResourceLeakDetector.Level.html">ResourceLeakDetector.Level</a>).
   */
  def leakDetection(level: LeakDetectionLevel): NettyServerConfig = self.copy(leakDetectionLevel = level)

}

object NettyServerConfig {
  val default: NettyServerConfig = NettyServerConfig()

  val live: ZLayer[Any, Nothing, NettyServerConfig] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(NettyServerConfig.default)
  }

  def live(config: NettyServerConfig)(implicit trace: Trace): ZLayer[Any, Nothing, NettyServerConfig] =
    ZLayer.succeed(config)

  sealed trait LeakDetectionLevel {
    self =>
    private[netty] def toNetty: ResourceLeakDetector.Level = self match {
      case LeakDetectionLevel.DISABLED => ResourceLeakDetector.Level.DISABLED
      case LeakDetectionLevel.SIMPLE   => ResourceLeakDetector.Level.SIMPLE
      case LeakDetectionLevel.ADVANCED => ResourceLeakDetector.Level.ADVANCED
      case LeakDetectionLevel.PARANOID => ResourceLeakDetector.Level.PARANOID
    }
  }

  object LeakDetectionLevel {
    case object DISABLED extends LeakDetectionLevel

    case object SIMPLE extends LeakDetectionLevel

    case object ADVANCED extends LeakDetectionLevel

    case object PARANOID extends LeakDetectionLevel
  }
}
