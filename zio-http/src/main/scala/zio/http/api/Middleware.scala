// package zio.http.api

// import io.netty.handler.codec.http.HttpHeaderNames
// import zio._
// import zio.http._
// import zio.http.api.MiddlewareSpec.CsrfValidate
// import zio.http.middleware.Auth
// import zio.http.middleware.Auth.Credentials
// import zio.http.middleware.Cors.CorsConfig
// import zio.http.model.Headers.{BasicSchemeName, BearerSchemeName, Header}
// import zio.http.model.headers.values.AccessControlRequestMethod.RequestMethod
// import zio.http.model.headers.values.Authorization.AuthScheme.{Basic, Bearer}
// import zio.http.model.headers.values._
// import zio.http.model.{Cookie, Headers, Method, Status}

// import java.util.UUID

// /**
//  * A `Middleware` represents the implementation of a `MiddlewareSpec`,
//  * intercepting parts of the request, and appending to the response.
//  */
// sealed trait Middleware[-R, I, O] { self =>
//   type State

//   /**
//    * Processes an incoming request, whose relevant parts are encoded into `I`,
//    * the middleware input, and then returns an effect that will produce both
//    * middleware-specific state (which will be passed to the outgoing handler),
//    * together with a decision about whether to continue or abort the handling of
//    * the request.
//    */
//   def incoming(in: I): ZIO[R, Nothing, Middleware.Control[State]]

//   /**
//    * Processes an outgoing response together with the middleware state (produced
//    * by the incoming handler), returning an effect that will produce `O`, which
//    * will in turn be used to patch the response.
//    */
//   def outgoing(state: State, response: Response): ZIO[R, Nothing, O]

//   /**
//    * Applies the middleware to an `HttpApp`, returning a new `HttpApp` with the
//    * middleware fully installed.
//    */
//   def apply[R1 <: R, E](httpRoute: HttpApp[R1, E]): HttpApp[R1, E] =
//     Http.fromOptionalHandlerZIO { request =>
//       for {
//         in       <- spec.middlewareIn.decodeRequest(request).orDie
//         control  <- incoming(in)
//         response <- control match {
//           case Middleware.Control.Continue(state)     =>
//             for {
//               response1 <- httpRoute.runZIO(request)
//               mo        <- outgoing(state, response1)
//               patch = spec.middlewareOut.encodeResponsePatch(mo)
//             } yield response1.patch(patch)
//           case Middleware.Control.Abort(state, patch) =>
//             val response = patch(Response.ok)

//             outgoing(state, response)
//               .map(out => response.patch(spec.middlewareOut.encodeResponsePatch(out)))

//         }
//       } yield Handler.response(response)
//     }

//   def ++[R1 <: R, I2, O2](that: Middleware[R1, I2, O2])(implicit
//     inCombiner: Combiner[I, I2],
//     outCombiner: Combiner[O, O2],
//   ): Middleware[R1, inCombiner.Out, outCombiner.Out] =
//     Middleware.Concat[R1, I, O, I2, O2, inCombiner.Out, outCombiner.Out](self, that, inCombiner, outCombiner)

//   def spec: MiddlewareSpec[I, O]
// }

// object Middleware {
//   sealed trait Control[+State] { self =>
//     def ++[State2](that: Control[State2])(implicit zippable: Zippable[State, State2]): Control[zippable.Out] =
//       (self, that) match {
//         case (Control.Continue(l), Control.Continue(r))           => Control.Continue(zippable.zip(l, r))
//         case (Control.Continue(l), Control.Abort(r, rpatch))      => Control.Abort(zippable.zip(l, r), rpatch)
//         case (Control.Abort(l, lpatch), Control.Continue(r))      => Control.Abort(zippable.zip(l, r), lpatch)
//         case (Control.Abort(l, lpatch), Control.Abort(r, rpatch)) =>
//           Control.Abort(zippable.zip(l, r), lpatch.andThen(rpatch))
//       }

//     def map[State2](f: State => State2): Control[State2] =
//       self match {
//         case Control.Continue(state)     => Control.Continue(f(state))
//         case Control.Abort(state, patch) => Control.Abort(f(state), patch)
//       }
//   }
//   object Control               {
//     final case class Continue[State](state: State)                           extends Control[State]
//     final case class Abort[State](state: State, patch: Response => Response) extends Control[State]
//   }

//   def intercept[S, R, I, O](spec: MiddlewareSpec[I, O])(incoming: I => Control[S])(
//     outgoing: (S, Response) => O,
//   ): Middleware[R, I, O] =
//     interceptZIO(spec)(i => ZIO.succeedNow(incoming(i)))((s, r) => ZIO.succeedNow(outgoing(s, r)))

//   def interceptZIO[S]: Interceptor1[S] = new Interceptor1[S]

//   /**
//    * Sets cookie in response headers
//    */
//   def addCookie(cookie: Cookie[Response]): Middleware[Any, Unit, Cookie[Response]] =
//     fromFunction(MiddlewareSpec.addCookie)(_ => cookie)

//   def addCookieZIO[R](cookie: ZIO[R, Nothing, Cookie[Response]]): Middleware[R, Unit, Cookie[Response]] =
//     fromFunctionZIO(MiddlewareSpec.addCookie)(_ => cookie)

//   def withAccept(value: CharSequence): Middleware[Any, Unit, Accept] =
//     fromFunction(MiddlewareSpec.withAccept)(_ => Accept.toAccept(value.toString))

//   def withAcceptEncoding(value: CharSequence): Middleware[Any, Unit, AcceptEncoding] =
//     fromFunction(MiddlewareSpec.withAcceptEncoding)(_ => AcceptEncoding.toAcceptEncoding(value.toString))

//   def withAcceptLanguage(value: CharSequence): Middleware[Any, Unit, AcceptLanguage] =
//     fromFunction(MiddlewareSpec.withAcceptLanguage)(_ => AcceptLanguage.toAcceptLanguage(value.toString))

//   def withAcceptPatch(value: CharSequence): Middleware[Any, Unit, AcceptPatch] =
//     fromFunction(MiddlewareSpec.withAcceptPatch)(_ => AcceptPatch.toAcceptPatch(value.toString))

//   def withAcceptRanges(value: CharSequence): Middleware[Any, Unit, AcceptRanges] =
//     fromFunction(MiddlewareSpec.withAcceptRanges)(_ => AcceptRanges.to(value.toString))

//   /**
//    * Creates a middleware for basic authentication
//    */
//   final def basicAuth(f: Auth.Credentials => Boolean)(implicit trace: Trace): Middleware[Any, Authorization, Unit] =
//     basicAuthZIO(credentials => ZIO.succeed(f(credentials)))

//   /**
//    * Creates a middleware for basic authentication that checks if the
//    * credentials are same as the ones given
//    */
//   final def basicAuth(u: String, p: String)(implicit trace: Trace): Middleware[Any, Authorization, Unit] =
//     basicAuth { credentials => (credentials.uname == u) && (credentials.upassword == p) }

//   /**
//    * Creates a middleware for basic authentication using an effectful
//    * verification function
//    */
//   def basicAuthZIO[R](f: Auth.Credentials => ZIO[R, Nothing, Boolean])(implicit
//     trace: Trace,
//   ): Middleware[R, Authorization, Unit] =
//     customAuthZIO(HeaderCodec.authorization, Headers(HttpHeaderNames.WWW_AUTHENTICATE, BasicSchemeName)) {
//       case Authorization.AuthorizationValue(Basic(username, password)) => f(Credentials(username, password))
//       case _                                                           => ZIO.succeed(false)
//     }

//   /**
//    * Creates a middleware for bearer authentication that checks the token using
//    * the given function
//    *
//    * @param f
//    *   : function that validates the token string inside the Bearer Header
//    */
//   final def bearerAuth(f: String => Boolean)(implicit trace: Trace): Middleware[Any, Authorization, Unit] =
//     bearerAuthZIO(token => ZIO.succeed(f(token)))

//   /**
//    * Creates a middleware for bearer authentication that checks the token using
//    * the given effectful function
//    *
//    * @param f
//    *   : function that effectfully validates the token string inside the Bearer
//    *   Header
//    */
//   final def bearerAuthZIO[R](
//     f: String => ZIO[R, Nothing, Boolean],
//   )(implicit trace: Trace): Middleware[R, Authorization, Unit] =
//     customAuthZIO(
//       HeaderCodec.authorization,
//       responseHeaders = Headers(HttpHeaderNames.WWW_AUTHENTICATE, BearerSchemeName),
//     ) {
//       case Authorization.AuthorizationValue(Bearer(token)) => f(token)
//       case _                                               => ZIO.succeed(false)
//     }

//   /**
//    * Creates an authentication middleware that only allows authenticated
//    * requests to be passed on to the app.
//    */
//   def customAuth[R, I](headerCodec: HeaderCodec[I])(
//     verify: I => Boolean,
//   ): Middleware[R, I, Unit] =
//     customAuthZIO(headerCodec)(header => ZIO.succeed(verify(header)))

//   /**
//    * Creates an authentication middleware that only allows authenticated
//    * requests to be passed on to the app using an effectful verification
//    * function.
//    */
//   def customAuthZIO[R, I](
//     headerCodec: HeaderCodec[I],
//     responseHeaders: Headers = Headers.empty,
//     responseStatus: Status = Status.Unauthorized,
//   )(verify: I => ZIO[R, Nothing, Boolean])(implicit trace: Trace): Middleware[R, I, Unit] =
//     MiddlewareSpec.customAuth(headerCodec).implementIncomingControl { in =>
//       verify(in).map {
//         case true  => Middleware.Control.Continue(())
//         case false => Middleware.Control.Abort((), _.copy(status = responseStatus, headers = responseHeaders))
//       }
//     }

//   /**
//    * Creates a middleware for Cross-Origin Resource Sharing (CORS).
//    *
//    * @see
//    *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
//    */
//   def cors(config: CorsConfig = CorsConfig()) = {
//     def allowCORS(origin: Origin, method: Method): Boolean =
//       (config.anyOrigin, config.anyMethod, Origin.fromOrigin(origin), method) match {
//         case (true, true, _, _)           => true
//         case (true, false, _, acrm)       => config.allowedMethods.exists(_.contains(acrm))
//         case (false, true, origin, _)     => config.allowedOrigins(origin)
//         case (false, false, origin, acrm) =>
//           config.allowedMethods.exists(_.contains(acrm)) && config.allowedOrigins(origin)
//       }

//     def corsHeaders(origin: Origin, isPreflight: Boolean): Headers = {
//       def buildHeaders(headerName: String, values: Option[Set[String]]): Headers =
//         values match {
//           case Some(headerValues) =>
//             Headers(headerValues.toList.map(value => Header(headerName, value)))
//           case None               => Headers.empty
//         }

//       Headers.ifThenElse(isPreflight)(
//         onTrue = buildHeaders(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(), config.allowedHeaders),
//         onFalse = buildHeaders(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), config.exposedHeaders),
//       ) ++
//         Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), Origin.fromOrigin(origin)) ++
//         buildHeaders(
//           HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
//           config.allowedMethods.map(_.map(_.toJava.name())),
//         ) ++
//         Headers.when(config.allowCredentials) {
//           Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, config.allowCredentials.toString)
//         }
//     }

//     MiddlewareSpec.cors.implement {
//       case (Method.OPTIONS, Some(origin), Some(acrm: RequestMethod)) if allowCORS(origin, acrm.method) =>
//         ZIO
//           .succeed(
//             Middleware.Control.Abort(
//               (),
//               _.copy(status = Status.NoContent, headers = corsHeaders(origin, isPreflight = true)),
//             ),
//           )

//       case (method, Some(origin), _) if allowCORS(origin, method) =>
//         ZIO
//           .succeed(
//             Middleware.Control
//               .Abort((), _.copy(headers = corsHeaders(origin, isPreflight = false))),
//           )

//       case _ => ZIO.succeed(Middleware.Control.Continue(()))
//     } { case (_, _) =>
//       ZIO.unit
//     }
//   }

//   def addHeader(header: Header): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.addHeader(header))(_ => ())

//   def addHeaders(headers: Headers): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.addHeaders(headers))(_ => ())

//   /**
//    * Adds the content base header to the response with the given value.
//    */
//   def withContentBase(value: ContentBase): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentBase.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content base header to the response with the given value, if it is
//    * valid. Else, it add an empty value.
//    */
//   def withContentBase(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentBase.mapOut(_.unit(ContentBase.toContentBase(value))))(identity)

//   /**
//    * Adds the content base header to the response with the value computed by the
//    * given effect.
//    */
//   def withContentBaseZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentBase,
//   ): Middleware[R, Unit, ContentBase] =
//     fromFunctionZIO(MiddlewareSpec.withContentBase)(_ => value.map(ev))

//   /**
//    * Adds the content base header to the response with the value computed by the
//    * given effect, if it is valid. Else, it add an empty value.
//    */
//   def withContentBaseZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, ContentBase] =
//     fromFunctionZIO(MiddlewareSpec.withContentBase)(_ => value.map(ContentBase.toContentBase))

//   /**
//    * Adds the content disposition header to the response with the given value.
//    */
//   def withContentDisposition(value: ContentDisposition): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentDisposition.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content disposition header to the response with the given value,
//    * if it is valid. Else, it adds an empty value.
//    */
//   def withContentDisposition(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(
//       MiddlewareSpec.withContentDisposition.mapOut(_.unit(ContentDisposition.toContentDisposition(value))),
//     )(identity)

//   /**
//    * Adds the content disposition header to the response with the value computed
//    * by the given effect.
//    */
//   def withContentDispositionZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentDisposition,
//   ): Middleware[R, Unit, ContentDisposition] =
//     fromFunctionZIO(MiddlewareSpec.withContentDisposition)(_ => value.map(ev))

//   /**
//    * Adds the content disposition header to the response with the value computed
//    * by the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentDispositionZIO[R](
//     value: ZIO[R, Nothing, CharSequence],
//   ): Middleware[R, Unit, ContentDisposition] =
//     fromFunctionZIO(MiddlewareSpec.withContentDisposition)(_ => value.map(ContentDisposition.toContentDisposition))

//   /**
//    * Adds the content encoding header to the response with the given value.
//    */
//   def withContentEncoding(value: ContentEncoding): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentEncoding.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content encoding header to the response with the given value, if
//    * it is valid. Else, it adds an empty value.
//    */
//   def withContentEncoding(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentEncoding.mapOut(_.unit(ContentEncoding.toContentEncoding(value))))(identity)

//   /**
//    * Adds the content encoding header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentEncodingZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentEncoding,
//   ): Middleware[R, Unit, ContentEncoding] =
//     fromFunctionZIO(MiddlewareSpec.withContentEncoding)(_ => value.map(ev))

//   /**
//    * Adds the content encoding header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentEncodingZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, ContentEncoding] =
//     fromFunctionZIO(MiddlewareSpec.withContentEncoding)(_ => value.map(ContentEncoding.toContentEncoding))

//   /**
//    * Adds the content language header to the response with the given value.
//    */
//   def withContentLanguage(value: ContentLanguage): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentLanguage.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content language header to the response with the given value, if
//    * it is valid. Else, it adds an empty value.
//    */
//   def withContentLanguage(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentLanguage.mapOut(_.unit(ContentLanguage.toContentLanguage(value))))(identity)

//   /**
//    * Adds the content language header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentLanguageZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentLanguage,
//   ): Middleware[R, Unit, ContentLanguage] =
//     fromFunctionZIO(MiddlewareSpec.withContentLanguage)(_ => value.map(ev))

//   /**
//    * Adds the content language header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentLanguageZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, ContentLanguage] =
//     fromFunctionZIO(MiddlewareSpec.withContentLanguage)(_ => value.map(ContentLanguage.toContentLanguage))

//   /**
//    * Adds the content length header to the response with the given value.
//    */
//   def withContentLength(value: ContentLength): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentLength.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content length header to the response with the given value, if it
//    * is valid. Else, it adds an empty value.
//    */
//   def withContentLength(value: Long): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentLength.mapOut(_.unit(ContentLength.toContentLength(value))))(identity)

//   /**
//    * Adds the content length header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentLengthZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentLength,
//   ): Middleware[R, Unit, ContentLength] =
//     fromFunctionZIO(MiddlewareSpec.withContentLength)(_ => value.map(ev))

//   /**
//    * Adds the content length header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentLengthZIO[R](value: ZIO[R, Nothing, Long]): Middleware[R, Unit, ContentLength] =
//     fromFunctionZIO(MiddlewareSpec.withContentLength)(_ => value.map(ContentLength.toContentLength))

//   /**
//    * Adds the content location header to the response with the given value.
//    */
//   def withContentLocation(value: ContentLocation): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentLocation.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content location header to the response with the given value, if
//    * it is valid. Else, it adds an empty value.
//    */
//   def withContentLocation(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentLocation.mapOut(_.unit(ContentLocation.toContentLocation(value))))(identity)

//   /**
//    * Adds the content location header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentLocationZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentLocation,
//   ): Middleware[R, Unit, ContentLocation] =
//     fromFunctionZIO(MiddlewareSpec.withContentLocation)(_ => value.map(ev))

//   /**
//    * Adds the content location header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentLocationZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, ContentLocation] =
//     fromFunctionZIO(MiddlewareSpec.withContentLocation)(_ => value.map(ContentLocation.toContentLocation))

//   /**
//    * Adds the content md5 header to the response with the given value.
//    */
//   def withContentMd5(value: ContentMd5): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentMd5.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content md5 header to the response with the given value, if it is
//    * valid. Else, it adds an empty value.
//    */
//   def withContentMd5(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentMd5.mapOut(_.unit(ContentMd5.toContentMd5(value))))(identity)

//   /**
//    * Adds the content md5 header to the response with the value computed by the
//    * given effect.
//    */
//   def withContentMd5ZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentMd5,
//   ): Middleware[R, Unit, ContentMd5] =
//     fromFunctionZIO(MiddlewareSpec.withContentMd5)(_ => value.map(ev))

//   /**
//    * Adds the content md5 header to the response with the value computed by the
//    * given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentMd5ZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, ContentMd5] =
//     fromFunctionZIO(MiddlewareSpec.withContentMd5)(_ => value.map(ContentMd5.toContentMd5))

//   /**
//    * Adds the content range header to the response with the given value.
//    */
//   def withContentRange(value: ContentRange): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentRange.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content range header to the response with the given value, if it
//    * is valid. Else, it adds an empty value.
//    */
//   def withContentRange(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentRange.mapOut(_.unit(ContentRange.toContentRange(value))))(identity)

//   /**
//    * Adds the content range header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentRangeZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentRange,
//   ): Middleware[R, Unit, ContentRange] =
//     fromFunctionZIO(MiddlewareSpec.withContentRange)(_ => value.map(ev))

//   /**
//    * Adds the content range header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentRangeZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, ContentRange] =
//     fromFunctionZIO(MiddlewareSpec.withContentRange)(_ => value.map(ContentRange.toContentRange))

//   /**
//    * Adds the content security policy header to the response with the given
//    * value.
//    */

//   def withContentSecurityPolicy(value: ContentSecurityPolicy): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentSecurityPolicy.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content security policy header to the response with the given
//    * value, if it is valid. Else, it adds an empty value.
//    */
//   def withContentSecurityPolicy(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(
//       MiddlewareSpec.withContentSecurityPolicy.mapOut(_.unit(ContentSecurityPolicy.toContentSecurityPolicy(value))),
//     )(identity)

//   /**
//    * Adds the content security policy header to the response with the value
//    * computed by the given effect.
//    */
//   def withContentSecurityPolicyZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentSecurityPolicy,
//   ): Middleware[R, Unit, ContentSecurityPolicy] =
//     fromFunctionZIO(MiddlewareSpec.withContentSecurityPolicy)(_ => value.map(ev))

//   /**
//    * Adds the content security policy header to the response with the value
//    * computed by the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentSecurityPolicyZIO[R](
//     value: ZIO[R, Nothing, CharSequence],
//   ): Middleware[R, Unit, ContentSecurityPolicy] =
//     fromFunctionZIO(MiddlewareSpec.withContentSecurityPolicy)(_ =>
//       value.map(ContentSecurityPolicy.toContentSecurityPolicy),
//     )

//   /**
//    * Adds the content transfer encoding header to the response with the given
//    * value.
//    */
//   def withContentTransferEncoding(value: ContentTransferEncoding): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentTransferEncoding.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content transfer encoding header to the response with the given
//    * value, if it is valid. Else, it adds an empty value.
//    */
//   def withContentTransferEncoding(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(
//       MiddlewareSpec.withContentTransferEncoding.mapOut(
//         _.unit(ContentTransferEncoding.toContentTransferEncoding(value)),
//       ),
//     )(identity)

//   /**
//    * Adds the content transfer encoding header to the response with the value
//    */
//   def withContentTransferEncodingZIO[R, A](
//     value: ZIO[R, Nothing, A],
//   )(implicit ev: A <:< ContentTransferEncoding): Middleware[R, Unit, ContentTransferEncoding] =
//     fromFunctionZIO(MiddlewareSpec.withContentTransferEncoding)(_ => value.map(ev))

//   /**
//    * Adds the content transfer encoding header to the response with the value
//    */
//   def withContentTransferEncodingZIO[R](
//     value: ZIO[R, Nothing, CharSequence],
//   ): Middleware[R, Unit, ContentTransferEncoding] =
//     fromFunctionZIO(MiddlewareSpec.withContentTransferEncoding)(_ =>
//       value.map(ContentTransferEncoding.toContentTransferEncoding),
//     )

//   /**
//    * Adds the content type header to the response with the given value.
//    */
//   def withContentType(value: ContentType): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentType.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content type header to the response with the given value, if it is
//    * valid. Else, it adds an empty value.
//    */
//   def withContentType(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.withContentType.mapOut(_.unit(ContentType.toContentType(value))))(identity)

//   /**
//    * Adds the content type header to the response with the value computed by the
//    * given effect.
//    */
//   def withContentTypeZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentType,
//   ): Middleware[R, Unit, ContentType] =
//     fromFunctionZIO(MiddlewareSpec.withContentType)(_ => value.map(ev))

//   /**
//    * Adds the content type header to the response with the value computed by the
//    * given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentTypeZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, ContentType] =
//     fromFunctionZIO(MiddlewareSpec.withContentType)(_ => value.map(ContentType.toContentType))

//   /**
//    * Generates a new CSRF token that can be validated using the csrfValidate
//    * middleware.
//    *
//    * CSRF middlewares: To prevent Cross-site request forgery attacks. This
//    * middleware is modeled after the double submit cookie pattern. Used in
//    * conjunction with [[#csrfValidate]] middleware.
//    *
//    * https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
//    */
//   final def csrfGenerate[R, E](
//     tokenName: String = "x-csrf-token",
//     tokenGen: ZIO[R, Nothing, String] = ZIO.succeed(UUID.randomUUID.toString)(Trace.empty),
//   )(implicit trace: Trace): api.Middleware[R, Unit, Cookie[Response]] = {
//     api.Middleware.addCookieZIO(tokenGen.map(Cookie(tokenName, _)))
//   }

//   /**
//    * Validates the CSRF token appearing in the request headers. Typically the
//    * token should be set using the `csrfGenerate` middleware.
//    *
//    * CSRF middlewares : To prevent Cross-site request forgery attacks. This
//    * middleware is modeled after the double submit cookie pattern. Used in
//    * conjunction with [[#csrfGenerate]] middleware
//    *
//    * https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
//    */
//   def csrfValidate(tokenName: String = "x-csrf-token"): Middleware[Any, CsrfValidate, Unit] =
//     MiddlewareSpec
//       .csrfValidate(tokenName)
//       .implement {
//         case state @ CsrfValidate(Some(cookieValue), Some(tokenValue)) if cookieValue.content == tokenValue =>
//           ZIO.succeedNow(Control.Continue(state))

//         case state =>
//           ZIO.succeedNow(Control.Abort(state, _ => Response.status(Status.Forbidden)))
//       }((_, _) => ZIO.unit)

//   def fromFunction[A, B](spec: MiddlewareSpec[A, B])(
//     f: A => B,
//   ): Middleware[Any, A, B] =
//     intercept(spec)((a: A) => Control.Continue(a))((a, _) => f(a))

//   def fromFunctionZIO[R, A, B](spec: MiddlewareSpec[A, B])(
//     f: A => ZIO[R, Nothing, B],
//   ): Middleware[R, A, B] =
//     interceptZIO(spec)((a: A) => ZIO.succeedNow(Control.Continue(a)))((a, _) => f(a))

//   def withAccessControlAllowOrigin(value: CharSequence): Middleware[Any, Unit, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowOrigin.mapOut(
//         _.unit(AccessControlAllowOrigin.toAccessControlAllowOrigin(value.toString)),
//       ),
//     )(identity)
//   }

//   def withAccessControlAllowMaxAge(value: CharSequence): Middleware[Any, Unit, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowMaxAge.mapOut(
//         _.unit(AccessControlMaxAge.toAccessControlMaxAge(value.toString)),
//       ),
//     )(identity)
//   }

//   def withProxyAuthenticate(value: CharSequence): Middleware[Any, Unit, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withProxyAuthenticate.mapOut(
//         _.unit(ProxyAuthenticate.toProxyAuthenticate(value.toString)),
//       ),
//     )(identity)
//   }

//   def withProxyAuthorization(value: CharSequence): Middleware[Any, Unit, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withProxyAuthorization.mapOut(
//         _.unit(ProxyAuthorization.toProxyAuthorization(value.toString)),
//       ),
//     )(identity)
//   }

//   def withReferer(value: CharSequence): Middleware[Any, Unit, Unit]    = {
//     fromFunction(
//       MiddlewareSpec.withReferer.mapOut(
//         _.unit(Referer.toReferer(value.toString)),
//       ),
//     )(identity)
//   }
//   def withRetryAfter(value: CharSequence): Middleware[Any, Unit, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withRetryAfter.mapOut(
//         _.unit(RetryAfter.toRetryAfter(value.toString)),
//       ),
//     )(identity)
//   }

//   def withAccessControlAllowCredentials(value: Boolean): Middleware[Any, Unit, Unit] =
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowCredentials.mapOut(
//         _.unit(AccessControlAllowCredentials.toAccessControlAllowCredentials(value)),
//       ),
//     )(identity)

//   def withAccessControlAllowMethods(value: Method*): Middleware[Any, Unit, Unit] =
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowMethods.mapOut(
//         _.unit(AccessControlAllowMethods.AllowMethods(Chunk.fromIterable(value))),
//       ),
//     )(identity)

//   def withAccessControlAllowMethods(value: CharSequence): Middleware[Any, Unit, Unit] =
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowMethods.mapOut(
//         _.unit(AccessControlAllowMethods.toAccessControlAllowMethods(value.toString)),
//       ),
//     )(identity)

//   def withTransferEncoding(value: CharSequence): Middleware[Any, Unit, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withTransferEncoding.mapOut(
//         _.unit(TransferEncoding.toTransferEncoding(value.toString)),
//       ),
//     )(identity)
//   }

//   def withConnection(value: CharSequence): Middleware[Any, Unit, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withConnection.mapOut(
//         _.unit(Connection.toConnection(value.toString)),
//       ),
//     )(identity)
//   }

//   def withExpires(value: CharSequence): Middleware[Any, Unit, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withExpires.mapOut(
//         _.unit(Expires.toExpires(value.toString)),
//       ),
//     )(identity)
//   }

//   def withIfRange(value: CharSequence): Middleware[Any, Unit, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withIfRange.mapOut(
//         _.unit(IfRange.toIfRange(value.toString)),
//       ),
//     )(identity)
//   }

//   val none: Middleware[Any, Unit, Unit] =
//     fromFunction(MiddlewareSpec.none)(_ => ())

//   class Interceptor1[S](val dummy: Boolean = true) extends AnyVal {
//     def apply[R, I, O](spec: MiddlewareSpec[I, O])(
//       incoming: I => ZIO[R, Nothing, Control[S]],
//     ): Interceptor2[S, R, I, O] =
//       new Interceptor2[S, R, I, O](spec, incoming)
//   }

//   class Interceptor2[S, R, I, O](spec: MiddlewareSpec[I, O], incoming: I => ZIO[R, Nothing, Control[S]]) {
//     def apply[R1 <: R, E](outgoing: (S, Response) => ZIO[R1, Nothing, O]): Middleware[R1, I, O] =
//       InterceptZIO(spec, incoming, outgoing)
//   }

//   private[api] final case class InterceptZIO[S, R, I, O](
//     spec: MiddlewareSpec[I, O],
//     incoming0: I => ZIO[R, Nothing, Control[S]],
//     outgoing0: (S, Response) => ZIO[R, Nothing, O],
//   ) extends Middleware[R, I, O] {
//     type State = S

//     def incoming(in: I): ZIO[R, Nothing, Middleware.Control[State]] = incoming0(in)

//     def outgoing(state: State, response: Response): ZIO[R, Nothing, O] = outgoing0(state, response)
//   }
//   private[api] final case class Concat[-R, I1, O1, I2, O2, I3, O3](
//     left: Middleware[R, I1, O1],
//     right: Middleware[R, I2, O2],
//     inCombiner: Combiner.WithOut[I1, I2, I3],
//     outCombiner: Combiner.WithOut[O1, O2, O3],
//   ) extends Middleware[R, I3, O3] {
//     type State = (left.State, right.State)

//     def incoming(in: I3): ZIO[R, Nothing, Middleware.Control[State]] = {
//       val (l, r) = inCombiner.separate(in)

//       for {
//         leftControl  <- left.incoming(l)
//         rightControl <- right.incoming(r)
//       } yield leftControl ++ rightControl
//     }

//     def outgoing(state: State, response: Response): ZIO[R, Nothing, O3] =
//       for {
//         l <- left.outgoing(state._1, response)
//         r <- right.outgoing(state._2, response)
//       } yield outCombiner.combine(l, r)

//     def spec: MiddlewareSpec[I3, O3] =
//       left.spec.++(right.spec)(inCombiner, outCombiner)
//   }
// }
