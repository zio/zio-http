package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class URLParserBenchmark {
  private val MAX = 1000
  private val str =
    "http://yourdomain.com/list/users/cusers/dusers/eusers/fusers/gusers?q=1&q=2&q=26&q=227828&q=217718&t=26728&y=1672882&j=28828&k=3767387"

  @Benchmark
  def benchmarkURLParser(): Unit    = {
    (0 to MAX).foreach(_ => URL(str).host)
    ()
  }
  @Benchmark
  def benchmarkURLOldParser(): Unit = {
    (0 to MAX).foreach(_ =>
      URLOld.fromString(str) match {
        case Left(_)      => "error"
        case Right(value) => value.host
      },
    )
    ()
  }
}
