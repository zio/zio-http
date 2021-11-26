package zhttp.internal

import zhttp.http._

import java.util.UUID
import zio.{Has, Ref, UIO, ZIO, ZLayer}

object AppCollection {

  trait Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]]
  }

  type Id          = UUID
  type HttpEnv     = HttpAppCollection
  type HttpAppTest = HttpApp[HttpEnv, Throwable]

  case class Live(ref: Ref[Map[Id, HttpApp[HttpEnv, Throwable]]]) extends Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]        = for {
      id <- UIO(UUID.randomUUID())
      _  <- ref.update(map => map + (id -> app))
    } yield id
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]] = ref.get.map(_.get(id))
  }

  def live: ZLayer[Any, Nothing, Has[Service]] = Ref
    .make(Map.empty[Id, HttpApp[HttpEnv, Throwable]])
    .map(ref => Live(ref))
    .toLayer

  def add(app: HttpApp[HttpEnv, Throwable]): ZIO[HttpAppCollection, Nothing, Id] =
    ZIO.accessM[HttpAppCollection](_.add(app))

  def get(id: Id): ZIO[HttpAppCollection, Nothing, Option[HttpApp[HttpEnv, Throwable]]] =
    ZIO.accessM[HttpAppCollection](_.get(id))

  def app: HttpApp[HttpEnv, Throwable] = Http.fromPartialFunction[Request] {
    case req @ _ -> uuid(id) /: path /: !! =>
      for {
        app <- get(id)
        url <- URL.fromString(path) match {
          case Left(_)      => ZIO.fail(Option(new Throwable(s"Invalid sub-path: ${path}")))
          case Right(value) => UIO(value)
        }
        res <- app match {
          case Some(app) => app(req.copy(url = url))
          case None      => ZIO.fail(Option(new Throwable(s"Invalid app id ${id}")))
        }
      } yield res

    case _ => ZIO.fail(None)
  }
}
