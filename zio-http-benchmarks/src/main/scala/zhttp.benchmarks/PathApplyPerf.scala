package zhttp.benchmarks

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zhttp.http._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class PathApplyPerf {
  private val MAX = 1000

  @Benchmark
  def benchmarkSlash(): Unit = {
    (0 to MAX).foreach(_ => !! / "text")
    ()
  }

  @Benchmark
  def benchmarkPath(): Unit = {
    (0 to MAX).foreach(_ => Path("/text"))
    ()
  }
  @Benchmark
  def benchmarkPathArray(): Unit = {
    (0 to MAX).foreach(_ => Path(List("text")))
    ()
  }

}
