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

  private val res                     = Response.text("HELLO WORLD")
  private val app: Http[Any, Nothing] = Http.text("HELLO WORLD")
  private val req: Request            =
    Request(Method.GET -> URL(Root), headers = List.empty, content = HttpData.empty)
  private val httpProgram             = ZIO.foreach_(0 to 1000) { _ => app(req) }
  private val UIOProgram              = ZIO.foreach_(0 to 1000) { _ => UIO(res) }

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
