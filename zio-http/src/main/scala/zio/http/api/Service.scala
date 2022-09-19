package zio.http.api

import zio._
import zio.http._
import zio.http.model.HttpError
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * Represents a collection of API endpoints that all have handlers.
 */
sealed trait Service[-R, +E] { self =>
  type AllIds

  /**
   * Combines this service and the specified service into a single service,
   * which contains all endpoints and their associated handlers.
   */
  def ++[R1 <: R, E1 >: E](that: Service[R1, E1]): Service.WithAllIds[R1, E1, AllIds with that.AllIds] =
    Service.Concat(self, that).withAllIds[AllIds with that.AllIds]

  /**
   * Converts this service into a [[zio.http.HttpApp]], which can then be served
   * via [[zio.http.Server.serve]].
   */
  def toHttpApp(implicit trace: Trace): HttpApp[R, E] = {
    import zio.http.api.internal._

    val handlerTree     = HandlerTree.fromService(self)
    val requestHandlers = Memoized[Service.HandledAPI[R, E, _, _], APIServer[R, E, _, _]] { handledApi =>
      APIServer(handledApi)
    }

    Http.collectZIO[Request].apply[R, E, Response] { case request =>
      val handler = handlerTree.lookup(request)

      handler match {
        case None => ZIO.succeedNow(Response.fromHttpError(HttpError.NotFound(handlerTree.generateError(request))))
        case Some(handlerMatch) =>
          requestHandlers.get(handlerMatch.handledApi).handle(handlerMatch.routeInputs, request)
      }
    }
  }

  private[api] def withAllIds[AllIds0]: Service.WithAllIds[R, E, AllIds0] =
    self.asInstanceOf[Service.WithAllIds[R, E, AllIds0]]
}
object Service               {
  type WithAllIds[-R, +E, AllIds0] = Service[R, E] { type AllIds = AllIds0 }

  final case class HandledAPI[-R, +E, In0, Out0](
    api: API[In0, Out0],
    handler: In0 => ZIO[R, E, Out0],
  ) extends Service[R, E] { self =>
    type AllIds = api.Id

    def flatten: Iterable[Service.HandledAPI[R, E, _, _]] = Chunk(self)
  }

  final case class Concat[-R, +E](left: Service[R, E], right: Service[R, E]) extends Service[R, E] {
    type Id = left.AllIds with right.AllIds
  }

  def flatten[R, E](service: Service[R, E]): Chunk[Service.HandledAPI[R, E, _, _]] =
    service match {
      case api @ HandledAPI(_, _) => Chunk(api.asInstanceOf[HandledAPI[R, E, _, _]])
      case Concat(left, right)    => flatten(left) ++ flatten(right)
    }
}
