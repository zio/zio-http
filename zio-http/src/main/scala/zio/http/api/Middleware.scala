package zio.http.api

import io.netty.handler.codec.http.HttpHeaderNames
import zio._
import zio.http._
import zio.http.api.MiddlewareSpec.{CsrfValidate, decodeHttpBasic}
import zio.http.middleware.Auth
import zio.http.middleware.Cors.CorsConfig
import zio.http.model.Headers.{BasicSchemeName, BearerSchemeName, Header}
import zio.http.model.headers.values.Origin
import zio.http.model.{Cookie, Headers, Method, Status}

import java.util.{Base64, UUID}

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
        in       <- spec.middlewareIn.decodeRequest(request).orDie
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
    fromFunction(MiddlewareSpec.addCookie)(_ => cookie)

  def addCookieZIO[R](cookie: ZIO[R, Nothing, Cookie[Response]]): Middleware[R, Unit, Cookie[Response]] =
    fromFunctionZIO(MiddlewareSpec.addCookie)(_ => cookie)

  /**
   * Creates a middleware for basic authentication
   */
  final def basicAuth(f: Auth.Credentials => Boolean)(implicit trace: Trace): Middleware[Any, String, Unit] =
    basicAuthZIO(credentials => ZIO.succeed(f(credentials)))

  /**
   * Creates a middleware for basic authentication that checks if the
   * credentials are same as the ones given
   */
  final def basicAuth(u: String, p: String)(implicit trace: Trace): Middleware[Any, String, Unit] =
    basicAuth { credentials => (credentials.uname == u) && (credentials.upassword == p) }

  /**
   * Creates a middleware for basic authentication using an effectful
   * verification function
   */
  def basicAuthZIO[R](f: Auth.Credentials => ZIO[R, Nothing, Boolean])(implicit
    trace: Trace,
  ): Middleware[R, String, Unit] =
    customAuthZIO(HeaderCodec.authorization, Headers(HttpHeaderNames.WWW_AUTHENTICATE, BasicSchemeName)) { encoded =>
      val indexOfBasic = encoded.indexOf(BasicSchemeName)
      if (indexOfBasic != 0 || encoded.length == BasicSchemeName.length) ZIO.succeed(false)
      else {
        // TODO: probably should be part of decodeHttpBasic
        val readyForDecode = new String(Base64.getDecoder.decode(encoded.substring(BasicSchemeName.length + 1)))
        decodeHttpBasic(readyForDecode) match {
          case Some(credentials) => f(credentials)
          case None              => ZIO.succeed(false)
        }
      }
    }

  /**
   * Creates a middleware for bearer authentication that checks the token using
   * the given function
   *
   * @param f
   *   : function that validates the token string inside the Bearer Header
   */
  final def bearerAuth(f: String => Boolean)(implicit trace: Trace): Middleware[Any, String, Unit] =
    bearerAuthZIO(token => ZIO.succeed(f(token)))

  /**
   * Creates a middleware for bearer authentication that checks the token using
   * the given effectful function
   *
   * @param f
   *   : function that effectfully validates the token string inside the Bearer
   *   Header
   */
  final def bearerAuthZIO[R](
    f: String => ZIO[R, Nothing, Boolean],
  )(implicit trace: Trace): Middleware[R, String, Unit] =
    customAuthZIO(
      HeaderCodec.authorization,
      responseHeaders = Headers(HttpHeaderNames.WWW_AUTHENTICATE, BearerSchemeName),
    ) { token =>
      val indexOfBearer = token.indexOf(BearerSchemeName)
      if (indexOfBearer != 0 || token.length == BearerSchemeName.length)
        ZIO.succeed(false)
      else
        f(token.substring(BearerSchemeName.length + 1))
    }

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app.
   */
  def customAuth[R, I](headerCodec: HeaderCodec[I])(
    verify: I => Boolean,
  ): Middleware[R, I, Unit] =
    customAuthZIO(headerCodec)(header => ZIO.succeed(verify(header)))

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app using an effectful verification
   * function.
   */
  def customAuthZIO[R, I](
    headerCodec: HeaderCodec[I],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  )(verify: I => ZIO[R, Nothing, Boolean])(implicit trace: Trace): Middleware[R, I, Unit] =
    MiddlewareSpec.customAuth(headerCodec).implementIncomingControl { in =>
      verify(in).map {
        case true  => Middleware.Control.Continue(())
        case false => Middleware.Control.Abort((), _.copy(status = responseStatus, headers = responseHeaders))
      }
    }

  /**
   * Creates a middleware for Cross-Origin Resource Sharing (CORS).
   *
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  def cors(config: CorsConfig = CorsConfig()) = {
    def allowCORS(origin: Origin, method: Method): Boolean =
      (config.anyOrigin, config.anyMethod, Origin.fromOrigin(origin), method) match {
        case (true, true, _, _)           => true
        case (true, false, _, acrm)       => config.allowedMethods.exists(_.contains(acrm))
        case (false, true, origin, _)     => config.allowedOrigins(origin)
        case (false, false, origin, acrm) =>
          config.allowedMethods.exists(_.contains(acrm)) && config.allowedOrigins(origin)
      }

    def corsHeaders(origin: Origin, isPreflight: Boolean): Headers = {
      def buildHeaders(headerName: String, values: Option[Set[String]]): Headers =
        values match {
          case Some(headerValues) =>
            Headers(headerValues.toList.map(value => Header(headerName, value)))
          case None               => Headers.empty
        }

      Headers.ifThenElse(isPreflight)(
        onTrue = buildHeaders(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(), config.allowedHeaders),
        onFalse = buildHeaders(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), config.exposedHeaders),
      ) ++
        Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), Origin.fromOrigin(origin)) ++
        buildHeaders(
          HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
          config.allowedMethods.map(_.map(_.toJava.name())),
        ) ++
        Headers.when(config.allowCredentials) {
          Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, config.allowCredentials.toString)
        }
    }

    MiddlewareSpec.cors.implement {
      case (Method.OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, Method.fromString(acrm)) =>
        ZIO
          .succeed(
            Middleware.Control.Abort(
              (),
              _.copy(status = Status.NoContent, headers = corsHeaders(origin, isPreflight = true)),
            ),
          )

      case (method, Some(origin), _) if allowCORS(origin, method) =>
        ZIO
          .succeed(
            Middleware.Control
              .Abort((), _.copy(headers = corsHeaders(origin, isPreflight = false))),
          )

      case _ => ZIO.succeed(Middleware.Control.Continue(()))
    } { case (_, _) =>
      ZIO.unit
    }
  }

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
  )(implicit trace: Trace): api.Middleware[R, Unit, Cookie[Response]] = {
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
  def csrfValidate(tokenName: String = "x-csrf-token"): Middleware[Any, CsrfValidate, Unit] =
    MiddlewareSpec
      .csrfValidate(tokenName)
      .implement {
        case state @ CsrfValidate(Some(cookieValue), Some(tokenValue)) if cookieValue.content == tokenValue =>
          ZIO.succeedNow(Control.Continue(state))

        case state =>
          ZIO.succeedNow(Control.Abort(state, _ => Response.status(Status.Forbidden)))
      }((_, _) => ZIO.unit)

  def fromFunction[A, B](spec: MiddlewareSpec[A, B])(
    f: A => B,
  ): Middleware[Any, A, B] =
    intercept(spec)((a: A) => Control.Continue(a))((a, _) => f(a))

  def fromFunctionZIO[R, A, B](spec: MiddlewareSpec[A, B])(
    f: A => ZIO[R, Nothing, B],
  ): Middleware[R, A, B] =
    interceptZIO(spec)((a: A) => ZIO.succeedNow(Control.Continue(a)))((a, _) => f(a))

  val none: Middleware[Any, Unit, Unit] =
    fromFunction(MiddlewareSpec.none)(_ => ())

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
