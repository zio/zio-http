package zhttp.internal

import zhttp.http._
import zhttp.service.Server.Start
import zio._
import zio.blocking.Blocking
import zio.console.Console

import java.util.UUID

object ReusableServer {

  def deploy(app: HttpApp[HttpEnv, Throwable]): ZIO[ReusableServer, Nothing, String] =
    ZIO.accessM[ReusableServer](_.get.add(app))

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

  def get(id: Id): ZIO[ReusableServer, Nothing, Option[HttpApp[HttpEnv, Throwable]]] =
    ZIO.accessM[ReusableServer](_.get.get(id))

  def setStart(s: Start): ZIO[ReusableServer, Nothing, Boolean] = ZIO.accessM[ReusableServer](_.get.setStart(s))
  def getStart: ZIO[ReusableServer, Nothing, Start]             = ZIO.accessM[ReusableServer](_.get.getStart)
  def getPort: ZIO[ReusableServer, Nothing, Int]                = ZIO.accessM[ReusableServer](_.get.getPort)

  def live: ZLayer[Any, Nothing, ReusableServer] = {
    for {
      ref <- Ref.make(Map.empty[Id, HttpApp[HttpEnv, Throwable]])
      pr  <- Promise.make[Nothing, Start]
    } yield new Live(ref, pr)
  }.toLayer

  type Id          = String
  type HttpEnv     = ReusableServer with Console with Blocking
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
