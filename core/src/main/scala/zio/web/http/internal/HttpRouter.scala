package zio.web.http.internal

import zio.{ UIO, ULayer, ZIO }
import zio.web.{ AnyF, Endpoint, Endpoints }
import zio.web.http.model.{ Method, Uri, Version }
import zio.ZLayer

object HttpRouter {

  trait Service {
    def route[M[_]](method: Method, uri: Uri, version: Version): UIO[Option[Endpoint[AnyF, _, _, _]]]
  }

  def basic[M[_]](endpoints: Endpoints[M, _]): ULayer[HttpRouter] =
    ZLayer.succeed(new Service {

      def route[M[_]](method: Method, uri: Uri, version: Version): UIO[Option[Endpoint[AnyF, _, _, _]]] =
        ZIO.effectTotal {
          import Endpoints.{ Cons, Empty }
          val _              = (method, version)
          val calledEndpoint = uri.toString.stripPrefix("/")

          def loop(search: Endpoints[AnyF, _]): Option[Endpoint[AnyF, _, _, _]] = search match {
            case Empty                                                        => None
            case Cons(endpoint, _) if endpoint.endpointName == calledEndpoint => Some(endpoint)
            case Cons(_, tail)                                                => loop(tail)
          }

          loop(endpoints.asInstanceOf[Endpoints[AnyF, _]])
        }
    })

  def route(method: Method, uri: Uri, version: Version): ZIO[HttpRouter, Nothing, Option[Endpoint[AnyF, _, _, _]]] =
    ZIO.accessM(_.get[Service].route(method, uri, version))
}
