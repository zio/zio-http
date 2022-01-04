package zhttp.internal

import zhttp.http._
import zhttp.service.Server.Start
import zio._
import zio.blocking.Blocking
import zio.console.Console

import java.util.UUID

object DynamicServer {

  def deploy(app: HttpApp[HttpEnv, Throwable]): ZIO[DynamicServer, Nothing, String] =
    ZIO.accessM[DynamicServer](_.get.add(app))

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

  def get(id: Id): ZIO[DynamicServer, Nothing, Option[HttpApp[HttpEnv, Throwable]]] =
    ZIO.accessM[DynamicServer](_.get.get(id))

  def setStart(s: Start): ZIO[DynamicServer, Nothing, Boolean] = ZIO.accessM[DynamicServer](_.get.setStart(s))
  def getStart: ZIO[DynamicServer, Nothing, Start]             = ZIO.accessM[DynamicServer](_.get.getStart)
  def getPort: ZIO[DynamicServer, Nothing, Int]                = ZIO.accessM[DynamicServer](_.get.getPort)

  def live: ZLayer[Any, Nothing, DynamicServer] = {
    for {
      ref <- Ref.make(Map.empty[Id, HttpApp[HttpEnv, Throwable]])
      pr  <- Promise.make[Nothing, Start]
    } yield new Live(ref, pr)
  }.toLayer

  type Id          = String
  type HttpEnv     = DynamicServer with Console with Blocking
  type HttpAppTest = HttpApp[HttpEnv, Throwable]
  val APP_ID = "X-APP_ID"

  sealed trait Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]]
    def setStart(n: Start): UIO[Boolean]
    def getStart: IO[Nothing, Start]
    def getPort: ZIO[Any, Nothing, Int]
  }

  final class Live(ref: Ref[Map[Id, HttpApp[HttpEnv, Throwable]]], pr: Promise[Nothing, Start]) extends Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]        = for {
      id <- UIO(UUID.randomUUID().toString)
      _  <- ref.update(map => map + (id -> app))
    } yield id
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]] = ref.get.map(_.get(id))
    def setStart(s: Start): UIO[Boolean]                      = pr.complete(ZIO(s).orDie)
    def getStart: IO[Nothing, Start]                          = pr.await
    def getPort: ZIO[Any, Nothing, Int]                       = getStart.map(_.port)
  }
}
