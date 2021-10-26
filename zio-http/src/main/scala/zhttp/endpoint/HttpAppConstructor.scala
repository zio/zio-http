package zhttp.endpoint

import zhttp.http.{HttpApp, HttpError, Request, Response}
import zio.{UIO, ZIO}

/**
 * Domain to create HttpApp from Route[A] and a partial function from Request[A] => B
 */
sealed trait HttpAppConstructor[A, B] {
  type ROut
  type EOut
  def make(route: Endpoint[A], f: Request.ParameterizedRequest[A] => B): HttpApp[ROut, EOut]
}

object HttpAppConstructor {
  type Aux[R, E, A, B] = HttpAppConstructor[A, B] {
    type ROut = R
    type EOut = E
  }

  implicit def f1[R, E, A]: Aux[R, E, A, Response[R, E]] = new HttpAppConstructor[A, Response[R, E]] {
    override type ROut = R
    override type EOut = E

    override def make(route: Endpoint[A], f: Request.ParameterizedRequest[A] => Response[R, E]): HttpApp[R, E] =
      HttpApp.collect { case req =>
        route.extract(req) match {
          case Some(value) => f(Request.ParameterizedRequest(req, value))
          case None        => Response.fromHttpError(HttpError.NotFound(req.url.path))
        }
      }
  }

  implicit def f2[R, E, A]: Aux[R, E, A, ZIO[R, E, Response[R, E]]] =
    new HttpAppConstructor[A, ZIO[R, E, Response[R, E]]] {
      override type ROut = R
      override type EOut = E

      override def make(
        route: Endpoint[A],
        f: Request.ParameterizedRequest[A] => ZIO[R, E, Response[R, E]],
      ): HttpApp[R, E] =
        HttpApp.collectM { case req =>
          route.extract(req) match {
            case Some(value) => f(Request.ParameterizedRequest(req, value))
            case None        => UIO(Response.fromHttpError(HttpError.NotFound(req.url.path)))
          }
        }
    }
}
