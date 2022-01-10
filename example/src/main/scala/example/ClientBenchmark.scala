package example

import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.{App, ExitCode, URIO, ZIO}

import java.util.concurrent.TimeUnit

object ClientBenchmark extends App {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url = "http://localhost:7777"

  def program(n: Int = 1000) = for {
    _         <- ZIO(println("Starting the benchmarking!"))
    _         <- ZIO(println(s"Number of requests: ${n}"))
    startTime <- ZIO(System.nanoTime())
    _         <- (for {
      _ <- Client.request(url).map(_.status)
    } yield ()).repeatN(n)

    duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
    _        = println(s"Requests/sec: ${(n * 1000 / duration)}")
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program().provideCustomLayer(env).exitCode

}
