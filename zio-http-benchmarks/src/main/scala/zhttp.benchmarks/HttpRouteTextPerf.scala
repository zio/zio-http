package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._
import zio._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpRouteTextPerf {

  private val runtime = Runtime.default

  private val res          = Response.text("HELLO WORLD")
  private val app          = Http.succeed(res)
  private val req: Request = Request(Method.GET, URL(!!))
  private val httpProgram  = ZIO.foreachDiscard(0 to 1000) { _ => app.execute(req).toZIO }
  private val UIOProgram   = ZIO.foreachDiscard(0 to 1000) { _ => UIO(res) }

  @Benchmark
  def benchmarkHttpProgram(): Unit = {
    runtime.unsafeRun(httpProgram)
    ()
  }

  @Benchmark
  def benchmarkUIOProgram(): Unit = {
    runtime.unsafeRun(UIOProgram)
    ()
  }
}
