package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpExecuteBenchmark {

  private val msg          = "HELLO WORLD"
  private val app          = Http.succeed(msg)
  private val req: Request = Request(Method.GET, URL(!!))

  @Benchmark
  def benchmarkHttpProgram(): Unit = {
    app.execute(req)
    ()
  }
}
