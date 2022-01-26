package zhttp.internal

import zhttp.http._
import zhttp.service.Server.Start
import zio._
import zio.blocking.Blocking
import zio.console.Console

import java.util.UUID

object DynamicServer {

  type Id          = String
  type HttpEnv     = DynamicServer with Console with Blocking
  type HttpAppTest = HttpApp[HttpEnv, Throwable]
  val APP_ID = "X-APP_ID"

  def app: HttpApp[HttpEnv, Throwable] = Http
    .fromOptionFunction[Request] { case req =>
      for {
        id  <- req.getHeaderValue(APP_ID) match {
          case Some(id) => UIO(id)
          case None     => ZIO.fail(None)
        }
        app <- get(id)
        res <- app match {
          case Some(app) => app(req)
          case None      => ZIO.fail(None)
        }
      } yield res
    }

  def deploy(app: HttpApp[HttpEnv, Throwable]): ZIO[DynamicServer, Nothing, String] =
    ZIO.accessM[DynamicServer](_.get.add(app))

  def get(id: Id): ZIO[DynamicServer, Nothing, Option[HttpApp[HttpEnv, Throwable]]] =
    ZIO.accessM[DynamicServer](_.get.get(id))

  def baseURL: ZIO[DynamicServer, Nothing, String] = getPort.map(port => s"http://localhost:$port")

  def getPort: ZIO[DynamicServer, Nothing, Int] = ZIO.accessM[DynamicServer](_.get.getPort)

  def getStart: ZIO[DynamicServer, Nothing, Start] = ZIO.accessM[DynamicServer](_.get.getStart)

  def live: ZLayer[Any, Nothing, DynamicServer] = {
    for {
      ref <- Ref.make(Map.empty[Id, HttpApp[HttpEnv, Throwable]])
      pr  <- Promise.make[Nothing, Start]
    } yield new Live(ref, pr)
  }.toLayer

  def setStart(s: Start): ZIO[DynamicServer, Nothing, Boolean] = ZIO.accessM[DynamicServer](_.get.setStart(s))

  sealed trait Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]]

    def getPort: ZIO[Any, Nothing, Int]

    def getStart: IO[Nothing, Start]

    def setStart(n: Start): UIO[Boolean]
  }

  final class Live(ref: Ref[Map[Id, HttpApp[HttpEnv, Throwable]]], pr: Promise[Nothing, Start]) extends Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]        = for {
      id <- UIO(UUID.randomUUID().toString)
      _  <- ref.update(map => map + (id -> app))
    } yield id
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]] = ref.get.map(_.get(id))

    def getPort: ZIO[Any, Nothing, Int] = getStart.map(_.port)

    def getStart: IO[Nothing, Start] = pr.await

    def setStart(s: Start): UIO[Boolean] = pr.complete(ZIO(s).orDie)
  }
}
