package zio.benchmarks

import org.openjdk.jmh.annotations._
import zio.http.Path
import zio.http.model.Cookie
import zio.http.model.Cookie.SameSite
import zio.{Unsafe, http}

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

  private val oldCookie = Cookie(name, value)
    .withMaxAge(maxAge)
    .withDomain(domain)
    .withPath(path)
    .withHttpOnly(true)
    .withSecure(true)
    .withSameSite(SameSite.Strict)

  private val oldCookieString = oldCookie.encode.getOrElse(throw new Exception("Failed to encode cookie"))

  @Benchmark
  def benchmarkNettyCookie(): Unit = {
    val _ = http.CookieDecoder.ResponseCookieDecoder.unsafe.decode(oldCookieString, false)(Unsafe.unsafe)
    ()
  }
}
