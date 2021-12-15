package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class CookieDecodeBenchmark {
  private val cookie = "SUID=123;httponly=true;expires=2007-12-03T10:15:30.00Z"

  @Benchmark
  def benchmarkApp(): Unit = {
    val _ = Cookie.decodeResponseCookie(cookie)
    ()
  }
}
