package zio.http.internal

import zio._
import zio.http._
import zio.http.internal.DynamicServer.Id
import zio.http.model.Scheme

import java.util.UUID

sealed trait DynamicServer {
  def add(app: App[Any]): UIO[Id]

  def get(id: Id): UIO[Option[App[Any]]]

  def port: ZIO[Any, Nothing, Int]

  def setStart(n: Server): UIO[Boolean]

  def start: IO[Nothing, Server]
}

object DynamicServer {

  type Id = String

  val APP_ID = "X-APP_ID"

  def app: App[DynamicServer] =
    Http.fromHttpZIO { (req: Request) =>
      req.headerValue(APP_ID) match {
        case Some(id) =>
          get(id).map(_.getOrElse(Handler.notFound.toHttp))
        case None     =>
          ZIO.succeed(Handler.notFound.toHttp)
      }
    }

  def baseURL(scheme: Scheme): ZIO[DynamicServer, Nothing, String] =
    port.map(port => s"${scheme.encode}://localhost:$port")

  def deploy[R](app: App[R]): ZIO[DynamicServer with R, Nothing, String] =
    for {
      env <- ZIO.environment[R]
      id  <- ZIO.environmentWithZIO[DynamicServer](_.get.add(app.provideEnvironment(env)))
    } yield id

  def get(id: Id): ZIO[DynamicServer, Nothing, Option[App[Any]]] =
    ZIO.environmentWithZIO[DynamicServer](_.get.get(id))

  def httpURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.HTTP)

  def live: ZLayer[Any, Nothing, DynamicServer] =
    ZLayer {
      for {
        ref <- Ref.make(Map.empty[Id, App[Any]])
        pr  <- Promise.make[Nothing, Server]
      } yield new Live(ref, pr)
    }

  def port: ZIO[DynamicServer, Nothing, Int] = ZIO.environmentWithZIO[DynamicServer](_.get.port)

  def setStart(s: Server): ZIO[DynamicServer, Nothing, Boolean] =
    ZIO.environmentWithZIO[DynamicServer](_.get.setStart(s))

  def start: ZIO[DynamicServer, Nothing, Server] = ZIO.environmentWithZIO[DynamicServer](_.get.start)

  def wsURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.WS)

  final class Live(ref: Ref[Map[Id, App[Any]]], pr: Promise[Nothing, Server]) extends DynamicServer {
    def add(app: App[Any]): UIO[Id] = for {
      id <- ZIO.succeed(UUID.randomUUID().toString)
      _  <- ref.update(map => map + (id -> app))
    } yield id

    def get(id: Id): UIO[Option[App[Any]]] = ref.get.map(_.get(id))

    def port: ZIO[Any, Nothing, Int] = start.map(_.port)

    def setStart(s: Server): UIO[Boolean] = pr.complete(ZIO.attempt(s).orDie)

    def start: IO[Nothing, Server] = pr.await
  }
}
