package zio.http.netty.server

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.netty.{EventLoopGroups, NettyConfig}

import io.netty.channel.EventLoopGroup

final case class ServerEventLoopGroups(
  boss: EventLoopGroup,
  worker: EventLoopGroup,
)

object ServerEventLoopGroups {
  private implicit val trace: Trace = Trace.empty

  private def groupLayer(cfg: EventLoopGroups.Config): ULayer[EventLoopGroup] =
    (ZLayer.succeed(cfg) >>> EventLoopGroups.live).fresh

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
