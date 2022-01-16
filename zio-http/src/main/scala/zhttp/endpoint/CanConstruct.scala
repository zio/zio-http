package zhttp.endpoint

import zhttp.http.{Http, HttpApp, Request, Response}
import zio.ZIO

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

  implicit def response[A]: Aux[Any, Nothing, A, Response] = new CanConstruct[A, Response] {
    override type ROut = Any
    override type EOut = Nothing

    override def make(route: Endpoint[A], f: Request.ParameterizedRequest[A] => Response): HttpApp[Any, Nothing] =
      Http
        .collectHttp[Request] { case req =>
          route.extract(req) match {
            case Some(value) => Http.succeed(f(Request.ParameterizedRequest(req, value)))
            case None        => Http.empty
          }
        }
  }

  implicit def responseZIO[R, E, A]: Aux[R, E, A, ZIO[R, E, Response]] =
    new CanConstruct[A, ZIO[R, E, Response]] {
      override type ROut = R
      override type EOut = E

      override def make(
        route: Endpoint[A],
        f: Request.ParameterizedRequest[A] => ZIO[R, E, Response],
      ): HttpApp[R, E] = {
        Http
          .collectHttp[Request] { case req =>
            route.extract(req) match {
              case Some(value) => Http.fromZIO(f(Request.ParameterizedRequest(req, value)))
              case None        => Http.empty
            }
          }
      }
    }
}
