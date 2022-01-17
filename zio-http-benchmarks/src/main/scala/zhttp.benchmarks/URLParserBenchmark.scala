package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class URLParserBenchmark {
  private val MAX = 1000

  @Benchmark
  def benchmarkURLParser(): Unit  = {
    (0 to MAX).foreach(_ => URL.fromString("http://yourdomain.com/list/users"))
    ()
  }
  @Benchmark
  def benchmarkURLParser2(): Unit = {
    (0 to MAX).foreach(_ => URL.fromString("http://yourdomain.com/list/users"))
    ()
  }

  @Benchmark
  def benchmarkURLParser3(): Unit = {
    (0 to MAX).foreach(_ => URL.fromString("http://yourdomain.com/list/users"))
    ()
  }

}
