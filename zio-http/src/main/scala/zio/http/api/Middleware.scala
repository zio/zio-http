package zio.http.api

import zio._
import zio.http._
import zio.http.model.Cookie
import zio.schema.codec.JsonCodec

/**
 * A `Middleware` represents the implementation of a `MiddlewareSpec`,
 * intercepting parts of the request, and appending to the response.
 */
sealed trait Middleware[-R, +E, I, O] { self =>
  type State

  /**
   * Processes an incoming request, whose relevant parts are encoded into `I`,
   * the middleware input, and then returns an effect that will produce both
   * state, together with a decision about whether to continue or abort the
   * handling of the request.
   */
  def incoming(in: I): ZIO[R, E, Middleware.Control[State]]

  /**
   * Processes an outgoing response together with the middleware state,
   * returning an effect that will produce `O`, which will in turn be used to
   * modify the response.
   */
  def outgoing(state: State, response: Response): ZIO[R, E, O]

  /**
   * Applies the middleware to an `HttpApp`, returning a new `HttpApp` with the
   * middleware fully installed.
   */
  def apply[R1 <: R, E1 >: E](httpApp: HttpApp[R1, E1]): HttpApp[R1, E1] =
    Http.fromOptionFunction[Request] { request =>
      for {
        in       <- spec.middlewareIn.decodeRequest(JsonCodec)(request).orDie
        control  <- incoming(in).mapError(Some(_))
        response <- control match {
          case Middleware.Control.Continue(state)     =>
            for {
              response1 <- httpApp(request)
              mo        <- outgoing(state, response1).mapError(Some(_))
              patch = spec.middlewareOut.encodeResponsePatch(mo)
            } yield response1.patch(patch)
          case Middleware.Control.Abort(state, patch) =>
            outgoing(state, patch(Response.ok)).mapError(Some(_)).map(spec.middlewareOut.encodeResponse(null)(_))
        }
      } yield response
    }

  def ++[R1 <: R, E1 >: E, I2, O2](that: Middleware[R1, E1, I2, O2])(implicit
    inCombiner: Combiner[I, I2],
    outCombiner: Combiner[O, O2],
  ): Middleware[R1, E1, inCombiner.Out, outCombiner.Out] =
    Middleware.Concat[R1, E1, I, O, I2, O2, inCombiner.Out, outCombiner.Out](self, that, inCombiner, outCombiner)

  def spec: MiddlewareSpec[I, O]
}

object Middleware {
  sealed trait Control[+State] { self =>
    def ++[State2](that: Control[State2])(implicit zippable: Zippable[State, State2]): Control[zippable.Out] =
      (self, that) match {
        case (Control.Continue(l), Control.Continue(r))           => Control.Continue(zippable.zip(l, r))
        case (Control.Continue(l), Control.Abort(r, rpatch))      => Control.Abort(zippable.zip(l, r), rpatch)
        case (Control.Abort(l, lpatch), Control.Continue(r))      => Control.Abort(zippable.zip(l, r), lpatch)
        case (Control.Abort(l, lpatch), Control.Abort(r, rpatch)) =>
          Control.Abort(zippable.zip(l, r), lpatch.andThen(rpatch))
      }

    def map[State2](f: State => State2): Control[State2] =
      self match {
        case Control.Continue(state)     => Control.Continue(f(state))
        case Control.Abort(state, patch) => Control.Abort(f(state), patch)
      }
  }
  object Control               {
    final case class Continue[State](state: State)                           extends Control[State]
    final case class Abort[State](state: State, patch: Response => Response) extends Control[State]
  }

  def intercept[S, R, E, I, O](spec: MiddlewareSpec[I, O])(incoming: I => Control[S])(
    outgoing: (S, Response) => O,
  ): Middleware[R, E, I, O] =
    interceptZIO(spec)(i => ZIO.succeedNow(incoming(i)))((s, r) => ZIO.succeedNow(outgoing(s, r)))

  def interceptZIO[S]: Interceptor1[S] = new Interceptor1[S]

  /**
   * Sets cookie in response headers
   */
  def addCookie(cookie: Cookie[Response]): Middleware[Any, Nothing, Unit, Cookie[Response]] =
    fromFunction(MiddlewareSpec.addCookie, _ => cookie)

  def addCookieZIO[R, E](cookie: ZIO[R, E, Cookie[Response]]): Middleware[R, E, Unit, Cookie[Response]] =
    fromFunctionZIO(MiddlewareSpec.addCookie)(_ => cookie)

  def fromFunction[A, B](
    spec: MiddlewareSpec[A, B],
    f: A => B,
  ): Middleware[Any, Nothing, A, B] =
    intercept(spec)((a: A) => Control.Continue(a))((a, _) => f(a))

  def fromFunctionZIO[R, E, A, B](spec: MiddlewareSpec[A, B])(
    f: A => ZIO[R, E, B],
  ): Middleware[R, E, A, B] =
    interceptZIO(spec)((a: A) => ZIO.succeedNow(Control.Continue(a)))((a, _) => f(a))

  val none: Middleware[Any, Nothing, Unit, Unit] =
    fromFunction(MiddlewareSpec.none, _ => ())

  class Interceptor1[S](val dummy: Boolean = true) extends AnyVal {
    def apply[R, E, I, O](spec: MiddlewareSpec[I, O])(
      incoming: I => ZIO[R, E, Control[S]],
    ): Interceptor2[S, R, E, I, O] =
      new Interceptor2[S, R, E, I, O](spec, incoming)
  }

  class Interceptor2[S, R, E, I, O](spec: MiddlewareSpec[I, O], incoming: I => ZIO[R, E, Control[S]]) {
    def apply[R1 <: R, E1 >: E](outgoing: (S, Response) => ZIO[R1, E1, O]): Middleware[R1, E1, I, O] =
      InterceptZIO(spec, incoming, outgoing)
  }

  private[api] final case class InterceptZIO[S, R, E, I, O](
    spec: MiddlewareSpec[I, O],
    incoming0: I => ZIO[R, E, Control[S]],
    outgoing0: (S, Response) => ZIO[R, E, O],
  ) extends Middleware[R, E, I, O] {
    type State = S

    def incoming(in: I): ZIO[R, E, Middleware.Control[State]] = incoming0(in)

    def outgoing(state: State, response: Response): ZIO[R, E, O] = outgoing0(state, response)
  }
  private[api] final case class Concat[-R, +E, I1, O1, I2, O2, I3, O3](
    left: Middleware[R, E, I1, O1],
    right: Middleware[R, E, I2, O2],
    inCombiner: Combiner.WithOut[I1, I2, I3],
    outCombiner: Combiner.WithOut[O1, O2, O3],
  ) extends Middleware[R, E, I3, O3] {
    type State = (left.State, right.State)

    def incoming(in: I3): ZIO[R, E, Middleware.Control[State]] = {
      val (l, r) = inCombiner.separate(in)

      for {
        leftControl  <- left.incoming(l)
        rightControl <- right.incoming(r)
      } yield leftControl ++ rightControl
    }

    def outgoing(state: State, response: Response): ZIO[R, E, O3] =
      for {
        l <- left.outgoing(state._1, response)
        r <- right.outgoing(state._2, response)
      } yield outCombiner.combine(l, r)

    def spec: MiddlewareSpec[I3, O3] =
      left.spec.++(right.spec)(inCombiner, outCombiner)
  }
}
