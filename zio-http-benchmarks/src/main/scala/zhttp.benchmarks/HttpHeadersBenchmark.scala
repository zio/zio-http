package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpHeadersBenchmark {
  @Benchmark
  def benchmarkApp(): Unit = {
    val _ = Headers.empty
      .addHeader("Content-Type", "application/json")
      .addHeader("Content-Length", "0")
      .addHeader("Accepts", "application/json")
      .withContentEncoding("utf-8")
      .removeHeader("Accepts")
    ()
  }
}
