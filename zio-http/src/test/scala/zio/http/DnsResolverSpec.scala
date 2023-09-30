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

import java.net.{Inet6Address, InetAddress, UnknownHostException}

import zio._
import zio.test.{Spec, TestClock, TestEnvironment, assertTrue}

import zio.http.DnsResolver.ExpireAction

object DnsResolverSpec extends ZIOHttpSpec {
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
      test("Only IPv4 is resolved by default") {
        val host = "google.com"
        for {
          resolved      <- DnsResolver.resolve(host)
          ipv6Addresses <- ZIO.succeed(resolved.filter(_.isInstanceOf[Inet6Address]))
        } yield assertTrue(!resolved.isEmpty, ipv6Addresses.isEmpty)
      }.provide(
        DnsResolver.default,
      ),
      test("Only return IPv6 when specified in the config") {
        val host = "google.com"
        for {
          resolved      <- DnsResolver.resolve(host)
          ipv4Addresses <- ZIO.succeed(resolved.filter(!_.isInstanceOf[Inet6Address]))
        } yield assertTrue(!resolved.isEmpty, ipv4Addresses.isEmpty)
      }.provide(
        ZLayer.succeed(DnsResolver.Config.default.useIPv6(true)) >>> DnsResolver.live,
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
    override def resolve(host: String)(implicit trace: Trace): ZIO[Any, UnknownHostException, Chunk[InetAddress]] =
      if (host.startsWith("host"))
        ZIO.succeed(Chunk(InetAddress.getByAddress(Array(127, 0, 0, host.stripPrefix("host").toByte))))
      else
        ZIO.fail(new UnknownHostException(host))
  }
}
