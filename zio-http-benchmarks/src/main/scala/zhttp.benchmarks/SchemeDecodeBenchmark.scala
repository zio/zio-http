package zio.benchmarks
import java.util.concurrent.TimeUnit

import zio.http.Scheme

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class SchemeDecodeBenchmark {
  private val MAX = 1000000

  @Benchmark
  def benchmarkSchemeDecode(): Unit = {
    (0 to MAX).foreach(_ => Scheme.decode("HTTP"))
    ()
  }
}
