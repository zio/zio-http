package zhttp.internal

import zhttp.http._
import zhttp.service.Server.Start
import zio._

import java.util.UUID

object DynamicServer {

  type Id = String

  val APP_ID = "X-APP_ID"

  def app: HttpApp[DynamicServer, Throwable] = Http
    .fromOptionFunction[Request] { req =>
      for {
        id  <- req.headerValue(APP_ID) match {
          case Some(id) => ZIO.succeed(id)
          case None     => ZIO.fail(None)
        }
        app <- get(id)
        res <- app match {
          case Some(app) => app(req)
          case None      => ZIO.fail(None)
        }
      } yield res
    }

  def baseURL(scheme: Scheme): ZIO[DynamicServer, Nothing, String] =
    port.map(port => s"${scheme.encode}://localhost:$port")

  def deploy[R](app: HttpApp[R, Throwable]): ZIO[DynamicServer with R, Nothing, String] =
    for {
      env <- ZIO.environment[R]
      id  <- ZIO.environmentWithZIO[DynamicServer](_.get.add(app.provideEnvironment(env)))
    } yield id

  def get(id: Id): ZIO[DynamicServer, Nothing, Option[HttpApp[Any, Throwable]]] =
    ZIO.environmentWithZIO[DynamicServer](_.get.get(id))

  def httpURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.HTTP)

  def live: ZLayer[Any, Nothing, DynamicServer] =
    ZLayer {
      for {
        ref <- Ref.make(Map.empty[Id, HttpApp[Any, Throwable]])
        pr  <- Promise.make[Nothing, Start]
      } yield new Live(ref, pr)
    }

  def port: ZIO[DynamicServer, Nothing, Int] = ZIO.environmentWithZIO[DynamicServer](_.get.port)

  def setStart(s: Start): ZIO[DynamicServer, Nothing, Boolean] =
    ZIO.environmentWithZIO[DynamicServer](_.get.setStart(s))

  def start: ZIO[DynamicServer, Nothing, Start] = ZIO.environmentWithZIO[DynamicServer](_.get.start)

  def wsURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.WS)

  sealed trait Service {
    def add(app: HttpApp[Any, Throwable]): UIO[Id]

    def get(id: Id): UIO[Option[HttpApp[Any, Throwable]]]

    def port: ZIO[Any, Nothing, Int]

    def setStart(n: Start): UIO[Boolean]

    def start: IO[Nothing, Start]
  }

  final class Live(ref: Ref[Map[Id, HttpApp[Any, Throwable]]], pr: Promise[Nothing, Start]) extends Service {
    def add(app: HttpApp[Any, Throwable]): UIO[Id] = for {
      id <- ZIO.succeed(UUID.randomUUID().toString)
      _  <- ref.update(map => map + (id -> app))
    } yield id

    def get(id: Id): UIO[Option[HttpApp[Any, Throwable]]] = ref.get.map(_.get(id))

    def port: ZIO[Any, Nothing, Int] = start.map(_.port)

    def setStart(s: Start): UIO[Boolean] = pr.complete(ZIO.attempt(s).orDie)

    def start: IO[Nothing, Start] = pr.await
  }
}
