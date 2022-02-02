package zhttp.service.client.experimental

import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.stream.ZStream
import zio.{App, ExitCode, Queue, URIO, ZIO}

import scala.concurrent.duration.FiniteDuration


object ZioQTest extends App{

  FiniteDuration
  // c.f. ZIO type aliases https://zio.dev/docs/overview/overview_index#type-aliases
  val result: URIO[Clock with Console, Unit] = for {
    queue <- Queue.bounded[Int](100)
    consumeQueue = ZStream.fromQueue(queue).foreach(e => putStrLn(e.toString))
    //Sleep without blocking threads thanks to ZIO fibers
    feedQueue = ZIO.foreach(Range(1,1000))(e => ZIO.sleep(10.millis) *> queue.offer(e))
    //run consume and feed in parallel
    _ <- consumeQueue.zipPar(feedQueue).catchAll(_ => ZIO.succeed(""))
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    result.map(_ => 1).exitCode
}
