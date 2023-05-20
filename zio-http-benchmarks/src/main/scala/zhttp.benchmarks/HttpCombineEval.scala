package zio.benchmarks

import java.util.concurrent.TimeUnit

import zio.{Trace, Unsafe}

import zio.http._

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpCombineEval {
  private val MAX  = 1000
  private val app  = Http.collect[Int] { case 0 => 1 }
  private val spec = (0 to MAX).foldLeft(app)((a, _) => a ++ app)

  @Benchmark
  def empty(): Unit = {
    spec.runZIOOrNull(-1)(Unsafe.unsafe, zio.http.Trace.trace)
    ()
  }

  @Benchmark
  def ok(): Unit = {
    spec.runZIOOrNull(0)(Unsafe.unsafe, zio.http.Trace.trace)
    ()
  }
}
