package zio.web.http.internal

import zio.{ UIO, ULayer, ZIO }
import zio.web.{ Endpoint, Endpoints }
import zio.web.http.model.{ Method, Uri, Version }
import zio.ZLayer

object HttpRouter {

  trait Service {
    def route(method: Method, uri: Uri, version: Version): UIO[Option[Endpoint[_, _, _]]]
  }

  def basic(endpoints: Endpoints[_, _]): ULayer[HttpRouter] =
    ZLayer.succeed(new Service {

      def route(method: Method, uri: Uri, version: Version): UIO[Option[Endpoint[_, _, _]]] = ZIO.effectTotal {
        import Endpoints.{ Cons, Empty }
        val _              = (method, version)
        val calledEndpoint = uri.toString.stripPrefix("/")

        def loop(search: Endpoints[_, _]): Option[Endpoint[_, _, _]] = search match {
          case Empty                                                        => None
          case Cons(endpoint, _) if endpoint.endpointName == calledEndpoint => Some(endpoint)
          case Cons(_, tail)                                                => loop(tail)
        }

        loop(endpoints)
      }
    })

  def route(method: Method, uri: Uri, version: Version): ZIO[HttpRouter, Nothing, Option[Endpoint[_, _, _]]] =
    ZIO.accessM(_.get[Service].route(method, uri, version))
}
