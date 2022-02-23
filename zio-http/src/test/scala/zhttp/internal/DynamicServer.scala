package zhttp.internal

import zhttp.http._
import zhttp.internal.DynamicServer.{HttpEnv, Id}
import zhttp.service.Server.Start
import zio._

import java.util.UUID

sealed trait DynamicServer {
  def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]
  def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]]

  def port: ZIO[Any, Nothing, Int]

  def start: IO[Nothing, Start]

  def setStart(n: Start): UIO[Boolean]
}
object DynamicServer       {

  type Id          = String
  type HttpEnv     = DynamicServer with Console
  type HttpAppTest = HttpApp[HttpEnv, Throwable]
  val APP_ID = "X-APP_ID"

  def app: HttpApp[HttpEnv, Throwable] = Http
    .fromOptionFunction[Request] { case req =>
      for {
        id  <- req.headerValue(APP_ID) match {
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

  def baseURL(scheme: Scheme): ZIO[DynamicServer, Nothing, String] =
    port.map(port => s"${scheme.encode}://localhost:$port")

  def deploy(app: HttpApp[HttpEnv, Throwable]): ZIO[DynamicServer, Nothing, String] =
    ZIO.serviceWithZIO[DynamicServer](_.add(app))

  def get(id: Id): ZIO[DynamicServer, Nothing, Option[HttpApp[HttpEnv, Throwable]]] =
    ZIO.serviceWithZIO[DynamicServer](_.get(id))

  def httpURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.HTTP)

  def live: ZLayer[Any, Nothing, DynamicServer] = {
    for {
      ref <- Ref.make(Map.empty[Id, HttpApp[HttpEnv, Throwable]])
      pr  <- Promise.make[Nothing, Start]
    } yield new Live(ref, pr)
  }.toLayer

  def port: ZIO[DynamicServer, Nothing, Int] = ZIO.serviceWithZIO[DynamicServer](_.port)

  def setStart(s: Start): ZIO[DynamicServer, Nothing, Boolean] = ZIO.serviceWithZIO[DynamicServer](_.setStart(s))

  def start: ZIO[DynamicServer, Nothing, Start] = ZIO.serviceWithZIO[DynamicServer](_.start)

  def wsURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.WS)

  sealed trait Service {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]]

    def port: ZIO[Any, Nothing, Int]

    def start: IO[Nothing, Start]

    def setStart(n: Start): UIO[Boolean]
  }

  final class Live(ref: Ref[Map[Id, HttpApp[HttpEnv, Throwable]]], pr: Promise[Nothing, Start]) extends DynamicServer {
    def add(app: HttpApp[HttpEnv, Throwable]): UIO[Id]        = for {
      id <- UIO(UUID.randomUUID().toString)
      _  <- ref.update(map => map + (id -> app))
    } yield id
    def get(id: Id): UIO[Option[HttpApp[HttpEnv, Throwable]]] = ref.get.map(_.get(id))

    def port: ZIO[Any, Nothing, Int] = start.map(_.port)

    def start: IO[Nothing, Start] = pr.await

    def setStart(s: Start): UIO[Boolean] = pr.complete(ZIO(s).orDie)
  }
}
