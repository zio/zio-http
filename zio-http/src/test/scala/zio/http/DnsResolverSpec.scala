package zio.http

import java.net.{InetAddress, UnknownHostException}

import zio._
import zio.test.{Spec, TestClock, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.DnsResolver.ExpireAction

object DnsResolverSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DnsResolver")(
      test("eventually maintains the specified max count") {
        for {
          entries1 <- DnsResolver.snapshot()
          _        <- ZIO.foreachDiscard(0 to 100) { idx =>
            DnsResolver.resolve("host" + idx.toString)
          }
          _        <- TestClock.adjust(2.seconds)
          entries2 <- DnsResolver.snapshot()
        } yield assertTrue(
          entries1.isEmpty,
          entries2.size == 10,
        )
      }.provide(
        DnsResolver.explicit(
          maxCount = 10,
          refreshRate = 1.second,
          implementation = TestResolver(),
        ),
      ),
      test("keeps the most recent elements") {
        for {
          entries1     <- DnsResolver.snapshot()
          _            <- ZIO.foreachDiscard(0 to 100) { idx =>
            TestClock.adjust(100.millis) *>
              DnsResolver.resolve("host" + idx.toString)
          }
          _            <- TestClock.adjust(2.seconds)
          allAddresses <- stringSnapshot()
        } yield assertTrue(
          entries1.isEmpty,
          allAddresses == Set(
            "/127.0.0.91",
            "/127.0.0.92",
            "/127.0.0.93",
            "/127.0.0.94",
            "/127.0.0.94",
            "/127.0.0.95",
            "/127.0.0.96",
            "/127.0.0.97",
            "/127.0.0.98",
            "/127.0.0.99",
            "/127.0.0.100",
          ),
        )
      }.provide(
        DnsResolver.explicit(
          maxCount = 10,
          refreshRate = 1.second,
          implementation = TestResolver(),
        ),
      ),
      test("Elements get dropped after TTL in dropping mode") {
        for {
          _        <- DnsResolver.resolve("host1") // 1
          _        <- TestClock.adjust(2.second)
          _        <- DnsResolver.resolve("host2") // 3
          _        <- DnsResolver.resolve("host3")
          h4       <- DnsResolver.resolve("unknown").exit
          entries1 <- stringSnapshot()
          _        <- TestClock.adjust(6.second)   // 9
          entries2 <- stringSnapshot()
          _        <- TestClock.adjust(4.second)   // 12
          entries3 <- stringSnapshot()
        } yield assertTrue(
          h4.isFailure,
          entries1 == Set("/127.0.0.1", "/127.0.0.2", "/127.0.0.3", "unknown"),
          entries2 == Set("/127.0.0.1", "/127.0.0.2", "/127.0.0.3"),
          entries3 == Set("/127.0.0.2", "/127.0.0.3"),
        )
      }.provide(
        DnsResolver.explicit(
          maxCount = 10,
          refreshRate = 1.second,
          ttl = 10.seconds,
          unknownHostTtl = 5.seconds,
          expireAction = ExpireAction.Drop,
          implementation = TestResolver(),
        ),
      ),
      test("Elements get refreshed after TTL in refresh mode") {
        for {
          t1 <- Clock.instant
          t2 = t1.plusSeconds(11) // ttl + refresh rate
          _        <- DnsResolver.resolve("host1") // 1
          _        <- DnsResolver.resolve("unknown").exit
          entries1 <- DnsResolver.snapshot()
          _        <- TestClock.adjust(15.second)
          entries2 <- DnsResolver.snapshot()
        } yield assertTrue(
          entries1.values.map(_.lastUpdatedAt).forall(_ == t1),
          entries1.values.map(_.previousAddresses).forall(_.isEmpty),
          entries2.values.map(_.lastUpdatedAt).forall(_ == t2),
          entries2.filter(_._1 != "unknown").values.map(_.previousAddresses).forall(_.nonEmpty),
        )
      }.provide(
        DnsResolver.explicit(
          maxCount = 10,
          refreshRate = 1.second,
          ttl = 10.seconds,
          unknownHostTtl = 10.seconds,
          expireAction = ExpireAction.Refresh,
          implementation = TestResolver(),
        ),
      ),
    )

  private def stringSnapshot(): ZIO[DnsResolver, Nothing, Set[String]] =
    DnsResolver.snapshot().flatMap { entries =>
      ZIO
        .foreach(Chunk.fromIterable(entries.values)) { entry => entry.resolvedAddresses.await.exit }
        .map {
          _.map {
            case Exit.Success(addrs) => addrs.map(_.toString)
            case Exit.Failure(_)     => Chunk("unknown")
          }.flatten.toSet
        }
    }

  private final case class TestResolver() extends DnsResolver {
    override def resolve(host: String): ZIO[Any, UnknownHostException, Chunk[InetAddress]] =
      if (host.startsWith("host"))
        ZIO.succeed(Chunk(InetAddress.getByAddress(Array(127, 0, 0, host.stripPrefix("host").toByte))))
      else
        ZIO.fail(new UnknownHostException(host))
  }
}
