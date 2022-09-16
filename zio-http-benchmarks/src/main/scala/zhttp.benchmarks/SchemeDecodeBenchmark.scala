package zio.benchmarks
import org.openjdk.jmh.annotations._
import zio.http.model.Scheme

import java.util.concurrent.TimeUnit

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
