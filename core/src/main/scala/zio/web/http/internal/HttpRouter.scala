package zio.web.http.internal

import zio.{ UIO, ZIO, ZManaged }
import zio.web.{ AnyF, Endpoint, Endpoints }
import zio.web.http.model.StartLine

final private[http] class HttpRouter(endpoints: Endpoints[AnyF, _]) {

  def route(startLine: StartLine): UIO[Option[Endpoint[AnyF, _, _, _]]] =
    ZIO.effectTotal {
      import Endpoints.{ Cons, Empty }
      val calledEndpoint = startLine.uri.toString.stripPrefix("/")

      def loop(search: Endpoints[AnyF, _]): Option[Endpoint[AnyF, _, _, _]] = search match {
        case Empty                                                        => None
        case Cons(endpoint, _) if endpoint.endpointName == calledEndpoint => Some(endpoint)
        case Cons(_, tail)                                                => loop(tail)
      }

      loop(endpoints.asInstanceOf[Endpoints[AnyF, _]])
    }
}

object HttpRouter {

  def make[M[_]](endpoints: Endpoints[M, _]): ZManaged[Any, Nothing, HttpRouter] = {
    type Actual   = Endpoints[M, _]
    type Expected = Endpoints[AnyF, _]

    def cast(value: Actual): Expected = value.asInstanceOf[Expected]

    ZManaged.succeed(new HttpRouter(cast(endpoints)))
  }
}
