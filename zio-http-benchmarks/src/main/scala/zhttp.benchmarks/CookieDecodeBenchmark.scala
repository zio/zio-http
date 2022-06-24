package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.time.Instant
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class CookieDecodeBenchmark {
  val random = new scala.util.Random()
  val name   = random.alphanumeric.take(100).mkString("")
  val value  = random.alphanumeric.take(100).mkString("")
  val domain = random.alphanumeric.take(100).mkString("")
  val path   = Path.decode((0 to 10).map { _ => random.alphanumeric.take(10).mkString("") }.mkString(""))
  val maxAge = random.nextLong()

  private val cookie    = Cookie(
    name,
    value,
    Some(Instant.now()),
    Some(domain),
    Some(path),
    true,
    true,
    Some(maxAge),
    Some(Cookie.SameSite.Strict),
  )
  private val cookieStr = cookie.encode

  @Benchmark
  def benchmarkApp(): Unit = {
    val _ = Cookie.unsafeDecodeResponseCookie(cookieStr)
    ()
  }
}
