package zio.http.benchmark

import java.util.concurrent.TimeUnit

import scala.util.Random

import zio.http.endpoint.Endpoint
import zio.http.{Handler, Method, Request, Routes}

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class RoutesBenchmark {

  val REPEAT_N = 1000

  val paths = ('a' to 'z').inits.map(_.mkString).toList.reverse.tail

  val routes = Routes.fromIterable(paths.map(p => Endpoint(Method.GET / p).out[Unit].implementHandler(Handler.unit)))

  val requests = paths.map(p => Request.get(p))

  def request: Request  = requests(Random.nextInt(requests.size))
  val smallDataRequests = Array.fill(REPEAT_N)(request)

  val paths2 = ('b' to 'z').inits.map(_.mkString).toList.reverse.tail

  val routes2 = Routes.fromIterable(paths2.map(p => Endpoint(Method.GET / p).out[Unit].implementHandler(Handler.unit)))

  val requests2 = requests ++ paths2.map(p => Request.get(p))

  def request2: Request = requests2(Random.nextInt(requests2.size))

  val smallDataRequests2 = Array.fill(REPEAT_N)(request2)

  val routes3 = Routes(
    Endpoint(Method.GET / "api").out[Unit].implementHandler(Handler.unit),
    Endpoint(Method.GET / "ui").out[Unit].implementHandler(Handler.unit),
  )

  val requests3 = Array.fill(REPEAT_N)(List(Request.get("api"), Request.get("ui"))(Random.nextInt(2)))

  @Benchmark
  def benchmarkSmallDataZioApi(): Unit =
    for (r <- smallDataRequests) routes.isDefinedAt(r)

  @Benchmark
  def benchmarkSmallDataZioApi2(): Unit =
    for (r <- smallDataRequests2) routes2.isDefinedAt(r)

  @Benchmark
  def notFound1(): Unit =
    for (_ <- 1 to REPEAT_N) {
      routes.isDefinedAt(Request.get("not-found"))
    }

  @Benchmark
  def notFound2(): Unit =
    for (_ <- 1 to REPEAT_N) {
      routes2.isDefinedAt(Request.get("not-found"))
    }

  @Benchmark
  def benchmarkSmallDataZioApi3(): Unit =
    for (r <- requests3) routes3.isDefinedAt(r)

}
