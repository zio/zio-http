package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class URLParserBenchmark {

  @Benchmark
  def benchmarkURLParser(): Unit  = {
    val _ = URL.fromString("http://yourdomain.com/list/users")
    ()
  }
  @Benchmark
  def benchmarkURLParser2(): Unit = {
    val _ = URL.fromString("http://yourdomain.com/list/users")
    ()
  }

  @Benchmark
  def benchmarkURLParser3(): Unit = {
    val _ = URL.fromString("http://yourdomain.com/list/users")
    ()
  }

}
