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

  implicit def response[R, E, A]: Aux[R, E, A, Response[R, E]] = new CanConstruct[A, Response[R, E]] {
    override type ROut = R
    override type EOut = E

    override def make(route: Endpoint[A], f: Request.ParameterizedRequest[A] => Response[R, E]): HttpApp[R, E] =
      Http
        .collect[Request] { case req =>
          route.extract(req) match {
            case Some(value) => Http.succeed(f(Request.ParameterizedRequest(req, value)))
            case None        => Http.empty
          }
        }
        .flatten
  }

  implicit def responseM[R, E, A]: Aux[R, E, A, ZIO[R, E, Response[R, E]]] =
    new CanConstruct[A, ZIO[R, E, Response[R, E]]] {
      override type ROut = R
      override type EOut = E

      override def make(
        route: Endpoint[A],
        f: Request.ParameterizedRequest[A] => ZIO[R, E, Response[R, E]],
      ): HttpApp[R, E] = {
        Http
          .collect[Request] { case req =>
            route.extract(req) match {
              case Some(value) => Http.fromEffect(f(Request.ParameterizedRequest(req, value)))
              case None        => Http.empty
            }
          }
          .flatten
      }
    }
}
