package benchmark

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{ConnectException, URI}
import java.util.concurrent.TimeUnit

import zio._

import zio.http._

import org.openjdk.jmh.annotations._

@State(org.openjdk.jmh.annotations.Scope.Thread)
@BenchmarkMode(Array(org.openjdk.jmh.annotations.Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class RoundtripBenchmark {

  val routes = Method.GET / "test" -> Handler.ok

  val request = Request(
    url = url"http://localhost:8080/test",
  )

  val server = Server.serve(routes).provide(Server.defaultWithPort(8080), ErrorResponseConfig.debugLayer)

  unsafeRun(server.forkDaemon)

  val runRequestJavaClientAPI =
    ZIO.attempt {
      val client  = HttpClient.newHttpClient()
      val request = HttpRequest
        .newBuilder()
        .uri(URI.create("http://localhost:8080/test"))
        .version(HttpClient.Version.HTTP_1_1)
        .GET()
        .build()
      var i       = 0
      while (i < 1000) {
        try client.send(request, HttpResponse.BodyHandlers.discarding())
        // client starts requesting before server is ready, ignore errors while server is starting
        catch { case _: ConnectException => () }
        i += 1
      }
    }

  def unsafeRun[E, A](zio: ZIO[Any, E, A]): Unit = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(zio.unit)
      .getOrThrowFiberFailure()
  }

  @Benchmark
  def roundtripBenchmarkJavaClientAPI(): Unit =
    unsafeRun(runRequestJavaClientAPI)

}
