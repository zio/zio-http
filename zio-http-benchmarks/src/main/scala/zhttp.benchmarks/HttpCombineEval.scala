package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCombineEval {
  private val MAX  = 1000
  private val app  = Http.collect[Int]({ case 0 => 1 })
  private val spec = (0 to MAX).foldLeft(app)((a, _) => a +++ app)

  @Benchmark
  def benchmarkNotFound(): Unit = {
    spec.evaluate(-1).asOut
    ()
  }

  @Benchmark
  def benchmarkOk(): Unit = {
    spec.evaluate(0).asOut
    ()
  }
}
