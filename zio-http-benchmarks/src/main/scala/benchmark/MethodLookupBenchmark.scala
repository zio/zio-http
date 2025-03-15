package benchmark

import java.util.concurrent.TimeUnit

import scala.util.Random

import zio.http._
import zio.http.endpoint.Endpoint

import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class MethodLookupBenchmark {

  val REPEAT_N = 1000

  val paths   = ('a' to 'z').inits.map(_.mkString).toList.reverse.tail
  val methods =
    List(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.HEAD, Method.OPTIONS, Method.PATCH, Method.TRACE)
  val routes  =
    Routes.fromIterable(paths.flatMap(p => methods.map(m => Endpoint(m / p).out[Unit].implementHandler(Handler.unit))))

  val requests = paths.flatMap(p => methods.map(m => Request(method = m, url = url"$p")))

  def request: Request       = requests(Random.nextInt(requests.size))
  val defaultMethodsRequests = Array.fill(REPEAT_N)(request)

  @Benchmark
  def defaultMethods(): Unit =
    for (request <- defaultMethodsRequests) routes.isDefinedAt(request)

  val methodsCustom = List(
    Method.GET,
    Method.POST,
    Method.PUT,
    Method.DELETE,
    Method.HEAD,
    Method.OPTIONS,
    Method.PATCH,
    Method.TRACE,
    Method.CUSTOM("CUSTOM"),
    Method.CUSTOM("CUSTOM2"),
  )
  val routesCustom  = Routes.fromIterable(
    paths.flatMap(p => methodsCustom.map(m => Endpoint(m / p).out[Unit].implementHandler(Handler.unit))),
  )

  val requestsCustom = paths.flatMap(p => methodsCustom.map(m => Request(method = m, url = url"$p")))

  def requestCustom: Request = requestsCustom(Random.nextInt(requestsCustom.size))
  val customMethodsRequests  = Array.fill(REPEAT_N)(requestCustom)

  @Benchmark
  def customMethods(): Unit =
    for (request <- customMethodsRequests) routesCustom.isDefinedAt(request)

}
