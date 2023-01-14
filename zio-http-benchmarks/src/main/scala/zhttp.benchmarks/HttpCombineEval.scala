package zio.benchmarks

import org.openjdk.jmh.annotations._
import zio.Unsafe
import zio.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCombineEval {
  private val MAX  = 1000
  private val app  = Http.collect[Int] { case 0 => 1 }
  private val spec = (0 to MAX).foldLeft(app)((a, _) => a ++ app)

  @Benchmark
  def empty(): Unit = {
    spec.runHExitOrNull(-1)(Unsafe.unsafe)
    ()
  }

  @Benchmark
  def ok(): Unit = {
    spec.runHExitOrNull(0)(Unsafe.unsafe)
    ()
  }
}
