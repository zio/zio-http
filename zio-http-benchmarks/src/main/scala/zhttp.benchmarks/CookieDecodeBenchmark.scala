package zio.benchmarks

import java.util.concurrent.TimeUnit

import zio.{Unsafe, http}

import zio.http.Path
import zio.http.model.Cookie
import zio.http.model.Cookie.SameSite

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class CookieDecodeBenchmark {
  val random = new scala.util.Random()
  val name   = random.alphanumeric.take(100).mkString("")
  val value  = random.alphanumeric.take(100).mkString("")
  val domain = random.alphanumeric.take(100).mkString("")
  val path   = Path.decode((0 to 10).map { _ => random.alphanumeric.take(10).mkString("") }.mkString(""))
  val maxAge = java.time.Duration.ofSeconds(random.nextLong())

  private val oldCookie = Cookie.Response(
    name,
    value,
    maxAge = Some(maxAge),
    domain = Some(domain),
    path = Some(path),
    isHttpOnly = true,
    isSecure = true,
    sameSite = Some(SameSite.Strict),
  )

  private val oldCookieString = oldCookie.encode.getOrElse(throw new Exception("Failed to encode cookie"))

  @Benchmark
  def benchmarkNettyCookie(): Any =
    Cookie.Response.decode(oldCookieString, false)
}
