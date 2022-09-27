package zio.http.api

import zio._
import zio.http._
import zio.http.model.HttpError
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Represents a collection of API endpoints that all have handlers.
 */
sealed trait Service[-R, +E, AllIds] { self =>

  /**
   * Combines this service and the specified service into a single service,
   * which contains all endpoints and their associated handlers.
   */
  def ++[R1 <: R, E1 >: E, AllIds2](that: Service[R1, E1, AllIds2]): Service[R1, E1, AllIds with AllIds2] =
    Service.Concat(self, that).withAllIds[AllIds with AllIds2]

  /**
   * Converts this service into a [[zio.http.HttpApp]], which can then be served
   * via [[zio.http.Server.serve]].
   */
  def toHttpApp: HttpApp[R, E] = {
    import zio.http.api.internal._

    val handlerTree     = HandlerTree.fromService(self)
    val requestHandlers = Memoized[Service.HandledAPI[R, E, _, _, _], APIServer[R, E, _, _]] { handledApi =>
      APIServer(handledApi)
    }

    Http
      .collectZIO[Request]
      .apply[R, E, Response] { case request =>
        val handler = handlerTree.lookup(request)

        handler match {
          case None => ZIO.succeedNow(Response.fromHttpError(HttpError.NotFound(handlerTree.generateError(request))))
          case Some(handlerMatch) =>
            requestHandlers.get(handlerMatch.handledApi).handle(handlerMatch.routeInputs, request)(Trace.empty)
        }
      }(Trace.empty)
  }

  private[api] def withAllIds[AllIds0]: Service[R, E, AllIds0] =
    self.asInstanceOf[Service[R, E, AllIds0]]
}

object Service {
  final case class HandledAPI[-R, +E, In0, Out0, Id](
    api: API.WithId[In0, Out0, Id],
    handler: In0 => ZIO[R, E, Out0],
  ) extends Service[R, E, Id] { self =>
    def flatten: Iterable[Service.HandledAPI[R, E, _, _, Id]] = Chunk(self)
  }

  final case class Concat[-R, +E, Ids1, Ids2](left: Service[R, E, Ids1], right: Service[R, E, Ids2])
      extends Service[R, E, Ids1 with Ids2]

  def flatten[R, E](service: Service[R, E, _]): Chunk[Service.HandledAPI[R, E, _, _, _]] =
    service match {
      case api @ HandledAPI(_, _) => Chunk(api.asInstanceOf[HandledAPI[R, E, _, _, _]])
      case Concat(left, right)    => flatten(left) ++ flatten(right)
    }
}
