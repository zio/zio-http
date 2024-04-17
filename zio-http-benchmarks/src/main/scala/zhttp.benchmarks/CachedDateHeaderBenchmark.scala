package zio.http.netty

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import zio.http.internal.DateEncoding

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(16)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
class CachedDateHeaderBenchmark {
  private val dateHeaderCache = new CachedDateHeader()

  @Benchmark
  def benchmarkCached() =
    dateHeaderCache.get()

  @Benchmark
  def benchmarkFresh() =
    DateEncoding.default.encodeDate(ZonedDateTime.now())
}
