package zhttp.internal

import zhttp.http._

import java.util.UUID
import zio.{Ref, UIO, ZIO, ZLayer}
import zio.console.Console

object AppCollection {

  def add(app: HttpApp[HttpEnv, Throwable]): ZIO[HttpAppCollection, Nothing, String] =
    ZIO.accessM[HttpAppCollection](_.get.add(app))

  def app: HttpApp[HttpEnv, Throwable] = Http
    .fromPartialFunction[Request] { case req =>
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

  def live: ZLayer[Any, Nothing, HttpAppCollection] = Ref
    .make(Map.empty[Id, HttpApp[HttpEnv, Throwable]])
    .map(ref => new Live(ref))
    .toLayer

  type Id          = String
  type HttpEnv     = HttpAppCollection with Console
  type HttpAppTest = HttpApp[HttpEnv, Throwable]
  val APP_ID = "X-APP_ID"

  sealed trait Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]]
  }

  final class Live(ref: Ref[Map[Id, HttpApp[HttpEnv, Throwable]]]) extends Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]        = for {
      id <- UIO(UUID.randomUUID().toString)
      _  <- ref.update(map => map + (id -> app))
    } yield id
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]] = ref.get.map(_.get(id))
  }
}
