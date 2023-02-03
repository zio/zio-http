package zio.http.api

import zio._
import zio.http._
import zio.http.model.HttpError
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Represents a collection of API endpoints that all have handlers.
 */
sealed trait Routes[-R, +E, M <: EndpointMiddleware] { self =>

  /**
   * Combines this service and the specified service into a single service,
   * which contains all endpoints and their associated handlers.
   */
  def ++[R1 <: R, E1 >: E, AllIds2](that: Routes[R1, E1, M]): Routes[R1, E1, M] =
    Routes.Concat(self, that)

  /**
   * Converts this service into a [[zio.http.HttpApp]], which can then be served
   * via [[zio.http.Server.serve]].
   */
  def toHttpApp: HttpApp[R, E] = {
    import zio.http.api.internal._

    val handlerTree     = HandlerTree.fromService(self)
    val requestHandlers = Memoized[Routes.Single[R, _ <: E, _, _, M], EndpointServer[R, _ <: E, _, _, M]] {
      handledApi =>
        EndpointServer(handledApi)
    }

    Http
      .collectZIO[Request] { case (request: Request) =>
        val handler = handlerTree.lookup(request)

        handler match {
          case None               =>
            ZIO.succeedNow(Response.fromHttpError(HttpError.NotFound(handlerTree.generateError(request))))
          case Some(handlerMatch) =>
            requestHandlers.get(handlerMatch.handledApi).handle(handlerMatch.routeInputs, request)(Trace.empty)
        }
      }
  }
}

object Routes {
  final case class Single[-R, E, In0, Out0, M <: EndpointMiddleware](
    endpoint: Endpoint[In0, E, Out0, M],
    handler: In0 => ZIO[R, E, Out0],
  ) extends Routes[R, E, M] { self =>
    def flatten: Iterable[Routes.Single[R, E, _, _, M]] = Chunk(self)
  }

  final case class Concat[-R, +E, M <: EndpointMiddleware](left: Routes[R, E, M], right: Routes[R, E, M])
      extends Routes[R, E, M]

  def flatten[R, E, M <: EndpointMiddleware](service: Routes[R, E, M]): Chunk[Routes.Single[R, E, _, _, M]] =
    service match {
      case api @ Single(_, _)  => Chunk(api.asInstanceOf[Single[R, E, _, _, M]])
      case Concat(left, right) => flatten(left) ++ flatten(right)
    }
}
