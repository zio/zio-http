package zhttp.benchmarks

import org.openjdk.jmh.annotations._
import zhttp.http._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class HttpRouteTextPerf {

  private val res          = Response.text("HELLO WORLD")
  private val app          = Http.fromHExit(HExit.succeed(res)).whenPathEq("/text") composeHttp Http
    .fromHExit(HExit.succeed(res))
    .whenPathEq("/plain")
  private val req: Request = Request(Method.GET, URL(!! / "text"))

  @Benchmark
  def benchmarkHttpProgram(): Unit = {
    val _ = app.execute(req)
    ()
  }

}
