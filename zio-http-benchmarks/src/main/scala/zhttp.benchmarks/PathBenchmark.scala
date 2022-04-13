package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class PathBenchmark {
  private val MAX = 1000

  @Benchmark
  def benchmarkSlash(): Unit = {
    (0 to MAX).foreach(_ => !! / "text")
    ()
  }

  @Benchmark
  def benchmarkPath(): Unit  = {
    (0 to MAX).foreach(_ => Path("/text"))
    ()
  }
  @Benchmark
  def benchmarkArray(): Unit = {
    (0 to MAX).foreach(_ => Path(List("text")))
    ()
  }

}
