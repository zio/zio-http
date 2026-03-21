package zio.http

import java.util.concurrent.TimeUnit

import zio.test.Assertion.isWithin
import zio.test.{TestAspect, assertZIO}
import zio.{Clock, ZIO, ZLayer, durationInt}

import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClient

object ClientLayerSpec extends ZIOHttpSpec {

  def clientLayerSpec = suite("ClientLayerSpec")(
    test("default client should shutdown within 250 ms") {
      val timeDifference = for {
        startTime <- ZIO.scoped {
          NettyClient.default.build *>
            Clock.currentTime(TimeUnit.MILLISECONDS)
        }
        endTime   <- Clock.currentTime(TimeUnit.MILLISECONDS)
      } yield endTime - startTime
      assertZIO[Any, Throwable, Long](timeDifference)(isWithin(50L, 250L))
    } @@ TestAspect.withLiveClock,
    test("netty client should allow customizing quiet period for client shutdown") {
      val customNettyConfig =
        NettyConfig.default
          .copy(shutdownQuietPeriodDuration = 2900.millis, shutdownTimeoutDuration = 3100.millis)
      val customClientLayer =
        (ZLayer.succeed(ZClient.Config.default) ++ ZLayer.succeed(customNettyConfig) ++
          DnsResolver.default) >>> NettyClient.live

      val timeDifference = for {
        startTime <- ZIO.scoped {
          customClientLayer.build *> Clock.currentTime(TimeUnit.MILLISECONDS)
        }
        endTime   <- Clock.currentTime(TimeUnit.MILLISECONDS)
      } yield endTime - startTime
      assertZIO[Any, Throwable, Long](timeDifference)(
        isWithin(2900L, 3100L),
      )
    } @@ TestAspect.withLiveClock,
  )

  override def spec = suite("ClientLayer")(clientLayerSpec)

}
