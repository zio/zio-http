package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCollectEval {
  private val MAX = 100_000
  private val app = Http.collect[Int]({ case 0 => 1 })

  @Benchmark
  def benchmark(): Unit = {
    (0 to MAX).foreach(_ => app.evaluate(0).asOut)
    ()
  }
}
