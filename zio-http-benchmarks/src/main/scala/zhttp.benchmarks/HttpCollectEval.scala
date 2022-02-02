package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCollectEval {
  private val MAX                 = 1000000
  private val app                 = Http.collect[Int] { case 0 => 1 }
  private val base: Int => Int    = _ => 1
  private val convert: Int => Int = a => a

  @Benchmark
  def benchmarkApp(): Unit = {
    (0 to MAX).foreach(_ => app.execute(0, convert))
    ()
  }

  @Benchmark
  def benchmarkBase(): Unit = {
    (0 to MAX).foreach(_ => base(0))
    ()
  }
}
