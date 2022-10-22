package zio.http.api

import zio.ZIO
import zio.http._
import zio.http.model.Cookie
import zio.schema.codec.JsonCodec

import zio.http.api.internal.BodyCodec

/**
 * A `Middleware` represents the implementation of a `MiddlewareSpec`,
 * intercepting parts of the request, and appending to the response.
 */
sealed trait Middleware[-R, +E, I, O] { self =>
  def ++[R1 <: R, E1 >: E, I2, O2](that: Middleware[R1, E1, I2, O2])(implicit
    inCombiner: Combiner[I, I2],
    outCombiner: Combiner[O, O2],
  ): Middleware[R1, E1, inCombiner.Out, outCombiner.Out] =
    Middleware.Concat[R1, E1, I, O, I2, O2, inCombiner.Out, outCombiner.Out](self, that, inCombiner, outCombiner)

  def toHttpTransformer[R1 <: R, E1 >: E]: HttpApp[R1, E1] => HttpApp[R1, E1] =
    Middleware.toHttpMiddleware(self)

}

object Middleware {

  /**
   * Sets cookie in response headers
   */
  def addCookie(cookie: Cookie[Response]): Middleware[Any, Nothing, Unit, Cookie[Response]] =
    fromFunction(MiddlewareSpec.addCookie, _ => cookie)

  def fromFunction[A, B](
    middlewareSpec: MiddlewareSpec[A, B],
    f: A => B,
  ): Middleware[Any, Nothing, A, B] =
    Handler(middlewareSpec, f)

  def fromFunctionZIO[R, E, A, B](
    middlewareSpec: MiddlewareSpec[A, B],
    f: A => ZIO[R, E, B],
  ): Middleware[R, E, A, B] =
    HandlerZIO(middlewareSpec, f)

  val none: Middleware[Any, Nothing, Unit, Unit] =
    fromFunction(MiddlewareSpec.none, _ => ())

  private[api] final case class HandlerZIO[-R, +E, I, O](
    middlewareSpec: MiddlewareSpec[I, O],
    handler: I => ZIO[R, E, O],
  ) extends Middleware[R, E, I, O]

  private[api] final case class PeekRequest[-R, +E, I, O](
    middleware: Middleware[R, E, I, O],
  ) extends Middleware[R, E, (I, Request), O]

  private[api] final case class Concat[-R, +E, I1, O1, I2, O2, I3, O3](
    left: Middleware[R, E, I1, O1],
    right: Middleware[R, E, I2, O2],
    inCombiner: Combiner.WithOut[I1, I2, I3],
    outCombiner: Combiner.WithOut[O1, O2, O3],
  ) extends Middleware[R, E, I3, O3]

  private[api] final case class Handler[I, O](middlewareSpec: MiddlewareSpec[I, O], handler: I => O)
      extends Middleware[Any, Nothing, I, O]

  private[api] def toHttpMiddleware[R, E, I, O](
    middleware: Middleware[R, E, I, O],
  ): HttpApp[R, E] => HttpApp[R, E] = {
    var optionalState: Option[I] = None

    middleware match {
      case Middleware.HandlerZIO(middlewareSpec, handler) =>
        applyHandler(middlewareSpec)(
          handler,
          input => { optionalState = Some(input) },
          optionalState.getOrElse(().asInstanceOf[I]),
        )

      case concat: Middleware.Concat[R, E, _, _, _, _, _, _] =>
        http => toHttpMiddleware(concat.right)(toHttpMiddleware(concat.left)(http))

      case Middleware.Handler(spec, handler) =>
        applyHandler(spec)(
          i => ZIO.succeed(handler(i)),
          input => { optionalState = Some(input) },
          optionalState.getOrElse(().asInstanceOf[I]),
        )

      case peek: Middleware.PeekRequest[_, _, _, _] =>
        toHttpMiddleware(peek.middleware)
    }
  }

  private[api] def applyHandler[R, E, I, O](middlewareSpec: MiddlewareSpec[I, O])(
    handler: I => ZIO[R, E, O],
    updateState: I => Unit,
    state: I,
  ): HttpApp[R, E] => HttpApp[R, E] = {
    if (middlewareSpec.middlewareOut.isEmpty)
      http => {
        val incomingFunction =
          loopMiddlewareIn(middlewareSpec.middlewareIn)

        Http.fromOptionFunction[Request] { request =>
          for {
            input <- incomingFunction(request)
            _     <- input match {
              case Some(input) =>
                updateState(input)
                handler(input).mapError(Some(_))
              case None        =>
                ZIO.unit
            }
            b     <- http(request)
          } yield b
        }
      }
    else { http =>
      {
        val outgoingFn =
          loopMiddlewareOut(middlewareSpec.middlewareOut, handler)

        Http.fromOptionFunction[Request] { request =>
          for {
            response <- http(request)
            response <- outgoingFn(response, state).mapError(Some(_))
          } yield response
        }
      }
    }
  }

  private[api] def loopMiddlewareIn[R, E, I1](
    in: HttpCodec[CodecType.Header with CodecType.Query, I1],
  ): Request => ZIO[R, Option[E], Option[I1]] = {
    in match {
      case atom: HttpCodec.Atom[CodecType.Header with CodecType.Query, _] =>
        atom match {
          case HttpCodec.Header(name, codec) =>
            (request: Request) => ZIO.fromOption(request.headers.get(name).flatMap(codec.decode)).map(Some(_))

          case HttpCodec.Query(key, codec) =>
            (request: Request) =>
              ZIO
                .fromOption(
                  request.url.queryParams.get(key).flatMap(_.headOption).flatMap(codec.decode),
                )
                .map(Some(_))

          case HttpCodec.Empty =>
            _ => ZIO.succeed(None)

          case _ =>
            _ => ZIO.fail(None)
        }

      case HttpCodec.WithDoc(in, _) =>
        loopMiddlewareIn(in)

      case HttpCodec.TransformOrFail(api, f, _) =>
        request =>
          loopMiddlewareIn(api)(request).map(
            _.map(value =>
              f(value).getOrElse(throw new Exception("Failed to transform the input retrieved in middleware")),
            ),
          )

      case HttpCodec.Combine(left, right, inputCombiner) =>
        request =>
          loopMiddlewareIn(left)(request).zip(loopMiddlewareIn(right)(request)).map { a =>
            for {
              a1 <- a._1
              a2 <- a._2
            } yield inputCombiner.combine(a1, a2)
          }
    }
  }

  private[api] def loopMiddlewareOut[R, E, I, O](
    out: HttpCodec[CodecType.ResponseType, O],
    handler: I => ZIO[R, E, O],
  ): (Response, I) => ZIO[R, E, Response] = {
    out match {
      case atom: HttpCodec.Atom[CodecType.ResponseType, O] =>
        atom match {
          case HttpCodec.Header(name, codec) =>
            (response, state) => handler(state).map(out => response.addHeader(name, codec.encode(out)))

          case HttpCodec.Body(schema) =>
            (response, state) =>
              handler(state).map(o => response.copy(body = BodyCodec.Single(schema).encodeToBody(o, JsonCodec)))

          case _ =>
            (response, _) => ZIO.succeed(response)
        }

      case HttpCodec.WithDoc(in, _) =>
        loopMiddlewareOut(in, handler)

      case HttpCodec.TransformOrFail(api, f, _) =>
        (response, state) =>
          loopMiddlewareOut(
            api,
            (i: I) =>
              handler(i).map(o =>
                f(o) match {
                  case Left(value)  =>
                    throw new Exception(
                      s"Failed to convert. ${value}",
                    ) // Good to transformOrFail have polymorphic error type and use ZIO.fromEither
                  case Right(value) => value
                },
              ),
          )(
            response,
            state,
          )

      case HttpCodec.Combine(left, right, _) =>
        (response, status) =>
          loopMiddlewareOut(left, handler)(response, status).flatMap(response =>
            loopMiddlewareOut(right, handler)(response, status),
          )
    }
  }
}
