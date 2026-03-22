package zio.http.netty.server

import zio._
import zio.test.TestAspect.{sequential, withLiveClock}
import zio.test.{Spec, TestEnvironment, assertTrue}

import zio.http.ZIOHttpSpec
import zio.http.netty.NettyConfig

object ServerEventLoopGroupsSpec extends ZIOHttpSpec {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerEventLoopGroups")(
      test("group sizes are as configured") {
        val nBoss   = 2
        val nWorker = 5
        val config  = ZLayer.succeed {
          val c = NettyConfig.defaultWithFastShutdown
          c.copy(nThreads = nWorker, bossGroup = c.bossGroup.copy(nThreads = nBoss))
        }

        ZIO.scoped {
          ServerEventLoopGroups.live.build.map { env =>
            val groups        = env.get[ServerEventLoopGroups]
            var bossThreads   = 0
            var workerThreads = 0
            groups.boss.forEach(_ => bossThreads += 1)
            groups.worker.forEach(_ => workerThreads += 1)
            assertTrue(bossThreads == nBoss, workerThreads == nWorker)
          }
        }.provide(config)
      },
      test("finalizers are run in parallel") {
        val configLayer = ZLayer.succeed {
          val c = NettyConfig.default
          c.copy(
            shutdownQuietPeriodDuration = 500.millis,
            bossGroup = c.bossGroup.copy(shutdownQuietPeriodDuration = 500.millis),
          )
        }

        val st = Ref.unsafe.make(0L)(Unsafe)

        (for {
          _  <- ZIO.scoped(ServerEventLoopGroups.live.build <* Clock.nanoTime.flatMap(st.set))
          et <- Clock.nanoTime
          st <- st.get
          d = Duration.fromNanos(et - st).toMillis
        } yield assertTrue(d >= 500L, d < 700L)).provide(configLayer)
      } @@ withLiveClock,
    ) @@ sequential
}
