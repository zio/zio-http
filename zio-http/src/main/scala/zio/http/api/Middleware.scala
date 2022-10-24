package zio.http.api

import zio._
import zio.http._
import zio.http.api.MiddlewareSpec.CsrfValidate
import zio.http.model.{Cookie, Status}
import zio.schema.codec.JsonCodec

import java.util.UUID

/**
 * A `Middleware` represents the implementation of a `MiddlewareSpec`,
 * intercepting parts of the request, and appending to the response.
 */
sealed trait Middleware[-R, I, O] { self =>
  type State

  /**
   * Processes an incoming request, whose relevant parts are encoded into `I`,
   * the middleware input, and then returns an effect that will produce both
   * middleware-specific state (which will be passed to the outgoing handlerr),
   * together with a decision about whether to continue or abort the handling of
   * the request.
   */
  def incoming(in: I): ZIO[R, Nothing, Middleware.Control[State]]

  /**
   * Processes an outgoing response together with the middleware state (produced
   * by the incoming handler), returning an effect that will produce `O`, which
   * will in turn be used to patch the response.
   */
  def outgoing(state: State, response: Response): ZIO[R, Nothing, O]

  /**
   * Applies the middleware to an `HttpApp`, returning a new `HttpApp` with the
   * middleware fully installed.
   */
  def apply[R1 <: R, E](httpApp: HttpApp[R1, E]): HttpApp[R1, E] =
    Http.fromOptionFunction[Request] { request =>
      for {
        in       <- spec.middlewareIn.decodeRequest(JsonCodec)(request).orDie
        control  <- incoming(in)
        response <- control match {
          case Middleware.Control.Continue(state)     =>
            for {
              response1 <- httpApp(request)
              mo        <- outgoing(state, response1)
              patch = spec.middlewareOut.encodeResponsePatch(mo)
            } yield response1.patch(patch)
          case Middleware.Control.Abort(state, patch) =>
            val response = patch(Response.ok)

            outgoing(state, response)
              .map(out => response.patch(spec.middlewareOut.encodeResponsePatch(out)))

        }
      } yield response
    }

  def ++[R1 <: R, I2, O2](that: Middleware[R1, I2, O2])(implicit
    inCombiner: Combiner[I, I2],
    outCombiner: Combiner[O, O2],
  ): Middleware[R1, inCombiner.Out, outCombiner.Out] =
    Middleware.Concat[R1, I, O, I2, O2, inCombiner.Out, outCombiner.Out](self, that, inCombiner, outCombiner)

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

  def intercept[S, R, I, O](spec: MiddlewareSpec[I, O])(incoming: I => Control[S])(
    outgoing: (S, Response) => O,
  ): Middleware[R, I, O] =
    interceptZIO(spec)(i => ZIO.succeedNow(incoming(i)))((s, r) => ZIO.succeedNow(outgoing(s, r)))

  def interceptZIO[S]: Interceptor1[S] = new Interceptor1[S]

  /**
   * Sets cookie in response headers
   */
  def addCookie(cookie: Cookie[Response]): Middleware[Any, Unit, Cookie[Response]] =
    fromFunction(MiddlewareSpec.addCookie, _ => cookie)

  def addCookieZIO[R, E](cookie: ZIO[R, E, Cookie[Response]]): Middleware[R, E, Unit, Cookie[Response]] =
    fromFunctionZIO(MiddlewareSpec.addCookie)(_ => cookie)

  /**
   * Generates a new CSRF token that can be validated using the csrfValidate
   * middleware.
   *
   * CSRF middlewares: To prevent Cross-site request forgery attacks. This
   * middleware is modeled after the double submit cookie pattern. Used in
   * conjunction with [[#csrfValidate]] middleware.
   *
   * https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
   */
  final def csrfGenerate[R, E](
    tokenName: String = "x-csrf-token",
    tokenGen: ZIO[R, Nothing, String] = ZIO.succeed(UUID.randomUUID.toString)(Trace.empty),
  )(implicit trace: Trace): api.Middleware[R, Nothing, Unit, Cookie[Response]] = {
    api.Middleware.addCookieZIO(tokenGen.map(Cookie(tokenName, _)))
  }

  /**
   * Validates the CSRF token appearing in the request headers. Typically the
   * token should be set using the `csrfGenerate` middleware.
   *
   * CSRF middlewares : To prevent Cross-site request forgery attacks. This
   * middleware is modeled after the double submit cookie pattern. Used in
   * conjunction with [[#csrfGenerate]] middleware
   *
   * https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
   */
  def csrfValidate(tokenName: String = "x-csrf-token"): Middleware[Any, Nothing, CsrfValidate, Unit] =
    MiddlewareSpec
      .csrfValidate(tokenName)
      .implement {
        case state @ CsrfValidate(Some(cookieValue), Some(tokenValue)) if cookieValue.content == tokenValue =>
          Control.Continue(state)

        case state =>
          Control.Abort(state, _ => Response.status(Status.Forbidden))
      }((_, _) => ())

  def fromFunction[A, B](
    spec: MiddlewareSpec[A, B],
    f: A => B,
  ): Middleware[Any, A, B] =
    intercept(spec)((a: A) => Control.Continue(a))((a, _) => f(a))

  def fromFunctionZIO[R, A, B](
    spec: MiddlewareSpec[A, B],
    f: A => ZIO[R, Nothing, B],
  ): Middleware[R, A, B] =
    interceptZIO(spec)((a: A) => ZIO.succeedNow(Control.Continue(a)))((a, _) => f(a))

  val none: Middleware[Any, Unit, Unit] =
    fromFunction(MiddlewareSpec.none, _ => ())

  class Interceptor1[S](val dummy: Boolean = true) extends AnyVal {
    def apply[R, I, O](spec: MiddlewareSpec[I, O])(
      incoming: I => ZIO[R, Nothing, Control[S]],
    ): Interceptor2[S, R, I, O] =
      new Interceptor2[S, R, I, O](spec, incoming)
  }

  class Interceptor2[S, R, I, O](spec: MiddlewareSpec[I, O], incoming: I => ZIO[R, Nothing, Control[S]]) {
    def apply[R1 <: R, E](outgoing: (S, Response) => ZIO[R1, Nothing, O]): Middleware[R1, I, O] =
      InterceptZIO(spec, incoming, outgoing)
  }

  private[api] final case class InterceptZIO[S, R, I, O](
    spec: MiddlewareSpec[I, O],
    incoming0: I => ZIO[R, Nothing, Control[S]],
    outgoing0: (S, Response) => ZIO[R, Nothing, O],
  ) extends Middleware[R, I, O] {
    type State = S

    def incoming(in: I): ZIO[R, Nothing, Middleware.Control[State]] = incoming0(in)

    def outgoing(state: State, response: Response): ZIO[R, Nothing, O] = outgoing0(state, response)
  }
  private[api] final case class Concat[-R, I1, O1, I2, O2, I3, O3](
    left: Middleware[R, I1, O1],
    right: Middleware[R, I2, O2],
    inCombiner: Combiner.WithOut[I1, I2, I3],
    outCombiner: Combiner.WithOut[O1, O2, O3],
  ) extends Middleware[R, I3, O3] {
    type State = (left.State, right.State)

    def incoming(in: I3): ZIO[R, Nothing, Middleware.Control[State]] = {
      val (l, r) = inCombiner.separate(in)

      for {
        leftControl  <- left.incoming(l)
        rightControl <- right.incoming(r)
      } yield leftControl ++ rightControl
    }

    def outgoing(state: State, response: Response): ZIO[R, Nothing, O3] =
      for {
        l <- left.outgoing(state._1, response)
        r <- right.outgoing(state._2, response)
      } yield outCombiner.combine(l, r)

    def spec: MiddlewareSpec[I3, O3] =
      left.spec.++(right.spec)(inCombiner, outCombiner)
  }
}
