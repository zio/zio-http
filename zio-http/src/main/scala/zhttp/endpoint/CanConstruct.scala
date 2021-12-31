package zhttp.endpoint

import zhttp.http.{Http, HttpApp, HttpError, Request, Response}
import zio.{UIO, ZIO}

/**
 * Constructors to make an HttpApp using an Endpoint
 */
sealed trait CanConstruct[A, B] {
  type ROut
  type EOut
  def make(route: Endpoint[A], f: Request.ParameterizedRequest[A] => B): HttpApp[ROut, EOut]
}

object CanConstruct {
  type Aux[R, E, A, B] = CanConstruct[A, B] {
    type ROut = R
    type EOut = E
  }

  implicit def response[R, E, A]: Aux[R, E, A, Response[R, E]] = new CanConstruct[A, Response[R, E]] {
    override type ROut = R
    override type EOut = E

    override def make(route: Endpoint[A], f: Request.ParameterizedRequest[A] => Response[R, E]): HttpApp[R, E] =
      Http.collect[Request] { case req =>
        route.extract(req) match {
          case Some(value) => f(Request.ParameterizedRequest(req, value))
          case None        => Response.fromHttpError(HttpError.NotFound(req.url.path))
        }
      }
  }

  implicit def responseZIO[R, E, A]: Aux[R, E, A, ZIO[R, E, Response[R, E]]] =
    new CanConstruct[A, ZIO[R, E, Response[R, E]]] {
      override type ROut = R
      override type EOut = E

      override def make(
        route: Endpoint[A],
        f: Request.ParameterizedRequest[A] => ZIO[R, E, Response[R, E]],
      ): HttpApp[R, E] =
        Http.collectZIO[Request] { case req =>
          route.extract(req) match {
            case Some(value) => f(Request.ParameterizedRequest(req, value))
            case None        => UIO(Response.fromHttpError(HttpError.NotFound(req.url.path)))
          }
        }
    }
}
