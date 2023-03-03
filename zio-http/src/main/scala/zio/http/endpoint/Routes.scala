/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.endpoint

import scala.annotation.tailrec

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http._
import zio.http.codec.HttpCodecError
import zio.http.model.HttpError

/**
 * Represents a collection of endpoints that all have handlers.
 */
sealed trait Routes[-R, +E, M <: EndpointMiddleware] { self =>

  /**
   * Returns a new collection that contains all of these routes, plus the
   * specified routes.
   */
  def ++[R1 <: R, E1 >: E, AllIds2](that: Routes[R1, E1, M]): Routes[R1, E1, M] =
    Routes.Concat(self, that)

  /**
   * Converts the collection of routes into a [[zio.http.App]], which can be
   * executed by a server. This method may be used when the routes are not using
   * any middleware.
   */
  def toApp[R1 <: R](implicit ev: EndpointMiddleware.None <:< M, trace: Trace): App[R1] = {
    toApp[R1, Unit](RoutesMiddleware.none.asInstanceOf[RoutesMiddleware[R1, Unit, M]])
  }

  /**
   * Converts the collection of routes into a [[zio.http.App]], which can be
   * executed by a server. This method may be used when the routes are using
   * middleware. You must provide a [[zio.http.endpoint.RoutesMiddleware]] that
   * can properly provide the required middleware for the routes.
   */
  def toApp[R1 <: R, S](mh: RoutesMiddleware[R1, S, M])(implicit trace: Trace): App[R1] = {
    import zio.http.endpoint.internal._

    val routingTree     = RoutingTree.fromRoutes(self)
    val requestHandlers = Memoized[Routes.Single[R, _ <: E, _, _, M], EndpointServer[R, _ <: E, _, _, M]] {
      handledApi =>
        EndpointServer(handledApi)
    }

    def dispatch(
      request: Request,
      alternatives: Chunk[Routes.Single[R, E, _, _, M]],
      index: Int,
      cause: Cause[Nothing],
    )(implicit trace: Trace): ZIO[R, Nothing, Response] =
      if (index >= alternatives.length) ZIO.refailCause(cause)
      else {
        val alternative = alternatives(index)

        requestHandlers
          .get(alternative)
          .handle(request)
          .foldCauseZIO(
            cause2 =>
              if (HttpCodecError.isHttpCodecError(cause2)) dispatch(request, alternatives, index + 1, cause ++ cause2)
              else ZIO.refailCause(cause),
            ZIO.succeed(_),
          )
      }

    Http
      .fromOptionalHandler[Request] { request =>
        val handlers = routingTree.lookup(request)

        if (handlers.isEmpty) None
        else Some(Handler.fromZIO(dispatch(request, handlers, 0, Cause.empty)))
      } @@ mh.toHandlerAspect.toMiddleware
  }

}

object Routes {
  private[zio] final case class Single[-R, E, In0, Out0, M <: EndpointMiddleware](
    endpoint: Endpoint[In0, E, Out0, M],
    handler: In0 => ZIO[R, E, Out0],
  ) extends Routes[R, E, M] { self =>
    def flatten: Iterable[Routes.Single[R, E, _, _, M]] = Chunk(self)
  }

  private[zio] final case class Concat[-R, +E, M <: EndpointMiddleware](left: Routes[R, E, M], right: Routes[R, E, M])
      extends Routes[R, E, M]

  private[zio] def flatten[R, E, M <: EndpointMiddleware](
    service: Routes[R, E, M],
  ): Chunk[Routes.Single[R, E, _, _, M]] =
    service match {
      case api @ Single(_, _)  => Chunk(api.asInstanceOf[Single[R, E, _, _, M]])
      case Concat(left, right) => flatten(left) ++ flatten(right)
    }
}
