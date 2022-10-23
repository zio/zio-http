package zio.http.api

import zio._
import zio.http._
import zio.http.model.HttpError
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Represents a collection of API endpoints that all have handlers.
 */
sealed trait Endpoints[-R, +E, AllIds] { self =>

  /**
   * Combines this service and the specified service into a single service,
   * which contains all endpoints and their associated handlers.
   */
  def ++[R1 <: R, E1 >: E, AllIds2](that: Endpoints[R1, E1, AllIds2]): Endpoints[R1, E1, AllIds with AllIds2] =
    Endpoints.Concat(self, that).withAllIds[AllIds with AllIds2]

  /**
   * Converts this service into a [[zio.http.HttpApp]], which can then be served
   * via [[zio.http.Server.serve]].
   */
  def toHttpApp: HttpApp[R, E] = {
    import zio.http.api.internal._

    val handlerTree     = HandlerTree.fromService(self)
    val requestHandlers = Memoized[Endpoints.HandledEndpoint[R, E, _, _, _], EndpointServer[R, E, _, _]] { handledApi =>
      EndpointServer(handledApi)
    }

    Http
      .collectZIO[Request]
      .apply[R, E, Response] { case request =>
        val handler = handlerTree.lookup(request)

        handler match {
          case None               =>
            ZIO.succeedNow(Response.fromHttpError(HttpError.NotFound(handlerTree.generateError(request))))
          case Some(handlerMatch) =>
            requestHandlers.get(handlerMatch.handledApi).handle(handlerMatch.routeInputs, request)(Trace.empty)
        }
      }(Trace.empty)
  }

  private[api] def withAllIds[AllIds0]: Endpoints[R, E, AllIds0] =
    self.asInstanceOf[Endpoints[R, E, AllIds0]]
}

object Endpoints {
  // How to integrate middlewarespec's handlers in here ?
  final case class HandledEndpoint[-R, +E, In0, Out0, Id](
    endpointSpec: EndpointSpec[In0, Out0],
    handler: In0 => ZIO[R, E, Out0],
  ) extends Endpoints[R, E, Id] { self =>
    def flatten: Iterable[Endpoints.HandledEndpoint[R, E, _, _, Id]] = Chunk(self)
  }

  final case class Concat[-R, +E, Ids1, Ids2](left: Endpoints[R, E, Ids1], right: Endpoints[R, E, Ids2])
      extends Endpoints[R, E, Ids1 with Ids2]

  def flatten[R, E](service: Endpoints[R, E, _]): Chunk[Endpoints.HandledEndpoint[R, E, _, _, _]] =
    service match {
      case api @ HandledEndpoint(_, _) => Chunk(api.asInstanceOf[HandledEndpoint[R, E, _, _, _]])
      case Concat(left, right)         => flatten(left) ++ flatten(right)
    }
}
