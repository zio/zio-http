package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.time.Instant
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class CookieDecodeBenchmark {
  private val cookie    = Cookie(
    "CookiesName",
    "CookiesValue",
    Some(Instant.now()),
    Some("domainValue"),
    Some(Path("Some", "Path")),
    true,
    true,
    Some(1200),
    Some(Cookie.SameSite.Strict),
  )
  private val cookieStr = cookie.encode

  @Benchmark
  def benchmarkApp(): Unit = {
    val _ = Cookie.decodeResponseCookie(cookieStr)
    ()
  }
}
