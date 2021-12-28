package zhttp.internal

import zhttp.http._
import zio._
import zio.blocking.Blocking
import zio.console.Console

import java.util.UUID

object AppCollection {

  def deploy(app: HttpApp[HttpEnv, Throwable]): ZIO[HttpAppCollection, Nothing, String] =
    ZIO.accessM[HttpAppCollection](_.get.add(app))

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

  def get(id: Id): ZIO[HttpAppCollection, Nothing, Option[HttpApp[HttpEnv, Throwable]]] =
    ZIO.accessM[HttpAppCollection](_.get.get(id))

  def setPort(n: Int): ZIO[HttpAppCollection, Nothing, Boolean] = ZIO.accessM[HttpAppCollection](_.get.setPort(n))
  def getPort: ZIO[HttpAppCollection, Nothing, Int]             = ZIO.accessM[HttpAppCollection](_.get.getPort)

  def live: ZLayer[Any, Nothing, HttpAppCollection] = {
    for {
      ref <- Ref.make(Map.empty[Id, HttpApp[HttpEnv, Throwable]])
      pr  <- Promise.make[Nothing, Int]
    } yield new Live(ref, pr)
  }.toLayer

  type Id          = String
  type HttpEnv     = HttpAppCollection with Console with Blocking
  type HttpAppTest = HttpApp[HttpEnv, Throwable]
  val APP_ID = "X-APP_ID"

  sealed trait Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]]
    def setPort(n: Int): UIO[Boolean]
    def getPort: IO[Nothing, Int]
  }

  final class Live(ref: Ref[Map[Id, HttpApp[HttpEnv, Throwable]]], pr: Promise[Nothing, Int]) extends Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]        = for {
      id <- UIO(UUID.randomUUID().toString)
      _  <- ref.update(map => map + (id -> app))
    } yield id
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]] = ref.get.map(_.get(id))
    def setPort(n: Int): UIO[Boolean]                         = pr.complete(ZIO(n).orDie)
    def getPort: IO[Nothing, Int]                             = pr.await
  }
}
