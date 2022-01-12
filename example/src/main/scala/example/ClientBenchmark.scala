package example

import zhttp.http.{Headers, HttpData, Method, URL}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, URIO, ZIO}

import java.util.concurrent.TimeUnit

object ClientBenchmark extends App {
  val env  = ChannelFactory.auto ++ EventLoopGroup.auto()
  val gurl = "http://localhost:7777/get"
  val purl = "http://localhost:7777/post"

  def program(n: Int) = for {
    _         <- ZIO(println("Warming up the server with 2000 requests"))
    _         <- Client.request(gurl).map(_.status).repeatN(2000)
    _         <- ZIO(println("Starting the benchmarking for GET requests!"))
    _         <- ZIO(println(s"Number of GET requests: ${n}"))
    startTime <- ZIO(System.nanoTime())
    _         <- Client.request(gurl).map(_.status).repeatN(n)
    duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
    _        = println(s"GET requests/sec: ${(n * 1000 / duration)}")

    _         <- ZIO(println("Starting the benchmarking for POST requests!"))
    _         <- ZIO(println(s"Number of POST requests: ${n}"))
    url       <- ZIO.fromEither(URL.fromString(purl))
    startTime <- ZIO(System.nanoTime())
    _ <- Client.request(Method.POST, url, Headers.empty, HttpData.fromString("Sample content")).map(_.status).repeatN(n)
    duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
    _        = println(s"POST requests/sec: ${(n * 1000 / duration)}")
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program(3000).provideCustomLayer(env).exitCode

}
