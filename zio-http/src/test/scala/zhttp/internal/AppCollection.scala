package zhttp.internal

import zhttp.http._
import zio.{Console, Ref, UIO, ZIO, ZLayer}

import java.util.UUID

sealed trait AppCollection {
  import AppCollection._

  def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]
  def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]]
}

object AppCollection {

  def deploy(app: HttpApp[HttpEnv, Throwable]): ZIO[AppCollection, Nothing, String] =
    ZIO.environmentWithZIO[AppCollection](_.get.add(app))

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

  def get(id: Id): ZIO[AppCollection, Nothing, Option[HttpApp[HttpEnv, Throwable]]] =
    ZIO.environmentWithZIO[AppCollection](_.get.get(id))

  def live: ZLayer[Any, Nothing, AppCollection] = Ref
    .make(Map.empty[Id, HttpApp[HttpEnv, Throwable]])
    .map(ref => new Live(ref))
    .toLayer

  type Id          = String
  type HttpEnv     = AppCollection with Console
  type HttpAppTest = HttpApp[HttpEnv, Throwable]
  val APP_ID = "X-APP_ID"

  final class Live(ref: Ref[Map[Id, HttpApp[HttpEnv, Throwable]]]) extends AppCollection {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]        = for {
      id <- UIO(UUID.randomUUID().toString)
      _  <- ref.update(map => map + (id -> app))
    } yield id
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]] = ref.get.map(_.get(id))
  }
}
