package zio.http.netty.server

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.netty.{EventLoopGroups, NettyConfig}

import io.netty.channel.EventLoopGroup

/**
 * The event loop groups of a Netty server.
 *
 * @param boss
 *   the group that accepts incoming connections
 * @param worker
 *   the group that handles the accepted connections
 */
final case class ServerEventLoopGroups(
  boss: EventLoopGroup,
  worker: EventLoopGroup,
)

/** Creates the [[ServerEventLoopGroups]] of a server. */
object ServerEventLoopGroups {
  private implicit val trace: Trace = Trace.empty

  private def groupLayer(cfg: EventLoopGroups.Config): ULayer[EventLoopGroup] =
    (ZLayer.succeed(cfg) >>> EventLoopGroups.live).fresh

  /**
   * A layer that creates the boss group from `NettyConfig#bossGroup` and the
   * worker group from the other settings of the [[zio.http.netty.NettyConfig]].
   * Both groups are shut down when the layer's scope closes.
   */
  val live: ZLayer[NettyConfig, Nothing, ServerEventLoopGroups] = ZLayer.fromZIO {
    ZIO.serviceWith[NettyConfig] { cfg =>
      val boss   = groupLayer(cfg.bossGroup)
      val worker = groupLayer(cfg)
      boss.zipWithPar(worker) { (boss, worker) =>
        ZEnvironment(ServerEventLoopGroups(boss.get, worker.get))
      }
    }
  }.flatten
}
