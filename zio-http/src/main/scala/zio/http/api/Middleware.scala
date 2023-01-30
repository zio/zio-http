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
//  * intercepting relevant components of the incoming request, and patching 
//  * relevant components of the outgoing response.
//  */
// sealed trait Middleware[Id, -R, State] { self =>
//   type I 
//   type E  
//   type O 

//   /**
//    * Processes an incoming request, whose relevant parts are encoded into `I`,
//    * the middleware input, and then returns an effect that will produce both
//    * middleware-specific state (which will be passed to the outgoing handlerr),
//    * together with a decision about whether to continue or abort the handling of
//    * the request.
//    */
//   def incoming(in: I): ZIO[R, E, State]

//   /**
//    * Processes an outgoing response together with the middleware state (produced
//    * by the incoming handler), returning an effect that will produce `O`, which
//    * will in turn be used to patch the response.
//    */
//   def outgoing(state: State, response: Response): ZIO[R, E, O]

//   /**
//    * Applies the middleware to an `HttpApp`, returning a new `HttpApp` with the
//    * middleware fully installed.
//    */
//   def apply[R1 <: R, E2](httpApp: HttpApp[R1, E2]): HttpApp[R1, E2] =
//     Http.fromOptionFunction[Request] { request =>
//       for {
//         in       <- spec.input.decodeRequest(request).orDie
//         control  <- incoming(in).either
//         response <- control match {
//           case Right(state) =>
//             for {
//               response1 <- httpApp(request)
//               mo        <- outgoing(state, response1).either
//               response2 = mo match {
//                 case Right(out) => response1.patch(spec.output.encodeResponsePatch(out))
//                 case Left(err)  => spec.error.encodeResponse(err)
//               }
//             } yield response2
//           case Left(err)    =>
//             ZIO.succeedNow(spec.error.encodeResponse(err))
//         }
//       } yield response
//     }

//   def ++[Id2, R1 <: R, State2](that: Middleware[Id2, R1, State2])(implicit
//     inCombiner: Combiner[I, that.I],
//     errAlternator: Alternator[E, that.E],
//     outCombiner: Combiner[O, that.O],
//     stateCombiner: Combiner[State, State2],
//   ): Middleware[Id with Id2, R1, stateCombiner.Out] =
//     Middleware.Concat[Id, Id2, R1, I, that.I, inCombiner.Out, E, that.E, errAlternator.Out, O, that.O, outCombiner.Out, State, State2, stateCombiner.Out](
//       self,
//       that,
//       inCombiner,
//       errAlternator,
//       outCombiner,
//       stateCombiner,
//     )

//   def spec: MiddlewareSpec[I, E, O]
// }

// object Middleware {
//   def intercept[S, R, I, E, O](spec: MiddlewareSpec[I, E, O])(incoming: I => Either[E, S])(
//     outgoing: (S, Response) => O,
//   ): Middleware[spec.type, R, S] =
//     interceptZIO(spec)(i => ZIO.fromEither(incoming(i)))((s, r) => ZIO.succeedNow(outgoing(s, r)))

//   def interceptZIO[S]: Interceptor1[S] = new Interceptor1[S]

//   /**
//    * Sets cookie in response headers
//    */
//   def addCookie(cookie: Cookie[Response]): Middleware[MiddlewareSpec.addCookie.type, Any, Unit] =
//     fromFunction(MiddlewareSpec.addCookie)(_ => cookie)

//   def addCookieZIO[R](cookie: ZIO[R, Nothing, Cookie[Response]]): Middleware[MiddlewareSpec.addCookie.type, R, Unit] =
//     fromFunctionZIO(MiddlewareSpec.addCookie)(_ => cookie)

//   def withAccept(value: CharSequence): Middleware[Any, Accept] =
//     fromFunction(MiddlewareSpec.withAccept)(_ => Accept.toAccept(value.toString))

//   def withAcceptEncoding(value: CharSequence): Middleware[Any, Unit] =
//     fromFunction(MiddlewareSpec.withAcceptEncoding)(_ => AcceptEncoding.toAcceptEncoding(value.toString))

//   def withAcceptLanguage(value: CharSequence): Middleware[Any, Unit, Unused, AcceptLanguage] =
//     fromFunction(MiddlewareSpec.withAcceptLanguage)(_ => AcceptLanguage.toAcceptLanguage(value.toString))

//   def withAcceptPatch(value: CharSequence): Middleware[MiddlewareSpec.withAcceptPatch.type, Any, Unit] =
//     fromFunction(MiddlewareSpec.withAcceptPatch)(_ => AcceptPatch.toAcceptPatch(value.toString))

//   def withAcceptRanges(value: CharSequence): Middleware[MiddlewareSpec.withAcceptRanges.type, Unit] =
//     fromFunction(MiddlewareSpec.withAcceptRanges)(_ => AcceptRanges.to(value.toString))

//   /**
//    * Creates a middleware for basic authentication
//    */
//   final def basicAuth(f: Auth.Credentials => Boolean)(implicit
//     trace: Trace,
//   ): Middleware[Any, Authorization, Unit, Unit] =
//     basicAuthZIO(credentials => ZIO.succeed(f(credentials)))

//   /**
//    * Creates a middleware for basic authentication that checks if the
//    * credentials are same as the ones given
//    */
//   final def basicAuth(u: String, p: String)(implicit trace: Trace): Middleware[Any, Authorization, Unit, Unit] =
//     basicAuth { credentials => (credentials.uname == u) && (credentials.upassword == p) }

//   /**
//    * Creates a middleware for basic authentication using an effectful
//    * verification function
//    */
//   def basicAuthZIO[R](f: Auth.Credentials => ZIO[R, Nothing, Boolean])(implicit
//     trace: Trace,
//   ): Middleware[R, Authorization, Unit, Unit] =
//     customAuthZIO(
//       HeaderCodec.authorization,
//       HttpCodec.empty,
//       HeaderCodec.wwwAuthenticate.unit(WWWAuthenticate.Basic(BasicSchemeName)) ++ StatusCodec.Unauthorized,
//     ) {
//       case Authorization.AuthorizationValue(Basic(username, password)) =>
//         f(Credentials(username, password)).flatMap {
//           case true  => ZIO.unit
//           case false => ZIO.fail(())
//         }
//       case _                                                           => ZIO.fail(())
//     }

//   /**
//    * Creates a middleware for bearer authentication that checks the token using
//    * the given function
//    *
//    * @param f
//    *   : function that validates the token string inside the Bearer Header
//    */
//   final def bearerAuth(f: String => Boolean)(implicit trace: Trace): Middleware[Any, Authorization, Unit, Unit] =
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
//   )(implicit trace: Trace): Middleware[R, Authorization, Unit, Unit] =
//     customAuthZIO(
//       HeaderCodec.authorization,
//       HttpCodec.Empty,
//       HeaderCodec.wwwAuthenticate.unit(WWWAuthenticate.Bearer(BearerSchemeName)) ++
//         StatusCodec.Unauthorized,
//     ) {
//       case Authorization.AuthorizationValue(Bearer(token)) =>
//         f(token).flatMap {
//           case true  => ZIO.unit
//           case false => ZIO.fail(())
//         }
//       case _                                               => ZIO.unit
//     }

//   /**
//    * Creates an authentication middleware that only allows authenticated
//    * requests to be passed on to the app.
//    */
//   def customAuth[I, E, O](
//     inputHeader: HeaderCodec[I],
//     outputHeader: HeaderCodec[O],
//     errorResponse: HttpCodec[CodecType.ResponseType, E],
//   )(
//     verify: I => Either[E, O],
//   ): Middleware[Any, I, E, O] =
//     customAuthZIO(inputHeader, outputHeader, errorResponse)(input => ZIO.fromEither(verify(input)))

//   /**
//    * Creates an authentication middleware that only allows authenticated
//    * requests to be passed on to the app using an effectful verification
//    * function.
//    */
//   def customAuthZIO[R, I, O, E](
//     inputHeader: HeaderCodec[I],
//     outputHeader: HeaderCodec[O],
//     errorResponse: HttpCodec[CodecType.ResponseType, E],
//   )(verify: I => ZIO[R, E, O])(implicit trace: Trace): Middleware[R, I, E, O] =
//     MiddlewareSpec.customAuth(inputHeader, outputHeader, errorResponse).implementIncoming(verify)

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

//     def preflight(origin: Origin) =
//       (
//         AccessControlAllowHeaders.AccessControlAllowHeadersValue(
//           Chunk.fromIterable(config.allowedHeaders.toList.flatten),
//         ),
//         AccessControlAllowOrigin.ValidAccessControlAllowOrigin(Origin.fromOrigin(origin)),
//         AccessControlAllowMethods.AllowMethods(Chunk.fromIterable(config.allowedMethods.toList.flatten)),
//         if (config.allowCredentials) Some(AccessControlAllowCredentials.AllowCredentials) else None,
//       )

//     def nonPreflight(origin: Origin) =
//       (
//         AccessControlExposeHeaders.AccessControlExposeHeadersValue(
//           Chunk.fromIterable(config.exposedHeaders.toList.flatten),
//         ),
//         AccessControlAllowOrigin.ValidAccessControlAllowOrigin(Origin.fromOrigin(origin)),
//         AccessControlAllowMethods.AllowMethods(Chunk.fromIterable(config.allowedMethods.toList.flatten)),
//         if (config.allowCredentials) Some(AccessControlAllowCredentials.AllowCredentials) else None,
//       )

//     MiddlewareSpec.cors.implement {
//       case Left((origin, AccessControlRequestMethod.RequestMethod(acrm))) if allowCORS(origin, acrm) =>
//         ZIO.fail(preflight(origin))

//       case Right((method, origin)) if allowCORS(origin, method) =>
//         ZIO
//           .succeed(nonPreflight(origin))

//       case _ => ??? // ZIO.unit
//     } { case (s, _) => ZIO.succeed(s) }
//   }

//   def addHeader(header: Header): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.addHeader(header))(_ => ())

//   def addHeaders(headers: Headers): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.addHeaders(headers))(_ => ())

//   /**
//    * Adds the content base header to the response with the given value.
//    */
//   def withContentBase(value: ContentBase): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentBase.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content base header to the response with the given value, if it is
//    * valid. Else, it add an empty value.
//    */
//   def withContentBase(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentBase.mapOut(_.unit(ContentBase.toContentBase(value))))(identity)

//   /**
//    * Adds the content base header to the response with the value computed by the
//    * given effect.
//    */
//   def withContentBaseZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentBase,
//   ): Middleware[R, Unit, Unused, ContentBase] =
//     fromFunctionZIO(MiddlewareSpec.withContentBase)(_ => value.map(ev))

//   /**
//    * Adds the content base header to the response with the value computed by the
//    * given effect, if it is valid. Else, it add an empty value.
//    */
//   def withContentBaseZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, Unused, ContentBase] =
//     fromFunctionZIO(MiddlewareSpec.withContentBase)(_ => value.map(ContentBase.toContentBase))

//   /**
//    * Adds the content disposition header to the response with the given value.
//    */
//   def withContentDisposition(value: ContentDisposition): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentDisposition.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content disposition header to the response with the given value,
//    * if it is valid. Else, it adds an empty value.
//    */
//   def withContentDisposition(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(
//       MiddlewareSpec.withContentDisposition.mapOut(_.unit(ContentDisposition.toContentDisposition(value))),
//     )(identity)

//   /**
//    * Adds the content disposition header to the response with the value computed
//    * by the given effect.
//    */
//   def withContentDispositionZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentDisposition,
//   ): Middleware[R, Unit, Unused, ContentDisposition] =
//     fromFunctionZIO(MiddlewareSpec.withContentDisposition)(_ => value.map(ev))

//   /**
//    * Adds the content disposition header to the response with the value computed
//    * by the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentDispositionZIO[R](
//     value: ZIO[R, Nothing, CharSequence],
//   ): Middleware[R, Unit, Unused, ContentDisposition] =
//     fromFunctionZIO(MiddlewareSpec.withContentDisposition)(_ => value.map(ContentDisposition.toContentDisposition))

//   /**
//    * Adds the content encoding header to the response with the given value.
//    */
//   def withContentEncoding(value: ContentEncoding): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentEncoding.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content encoding header to the response with the given value, if
//    * it is valid. Else, it adds an empty value.
//    */
//   def withContentEncoding(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentEncoding.mapOut(_.unit(ContentEncoding.toContentEncoding(value))))(identity)

//   /**
//    * Adds the content encoding header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentEncodingZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentEncoding,
//   ): Middleware[R, Unit, Unused, ContentEncoding] =
//     fromFunctionZIO(MiddlewareSpec.withContentEncoding)(_ => value.map(ev))

//   /**
//    * Adds the content encoding header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentEncodingZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, Unused, ContentEncoding] =
//     fromFunctionZIO(MiddlewareSpec.withContentEncoding)(_ => value.map(ContentEncoding.toContentEncoding))

//   /**
//    * Adds the content language header to the response with the given value.
//    */
//   def withContentLanguage(value: ContentLanguage): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentLanguage.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content language header to the response with the given value, if
//    * it is valid. Else, it adds an empty value.
//    */
//   def withContentLanguage(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentLanguage.mapOut(_.unit(ContentLanguage.toContentLanguage(value))))(identity)

//   /**
//    * Adds the content language header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentLanguageZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentLanguage,
//   ): Middleware[R, Unit, Unused, ContentLanguage] =
//     fromFunctionZIO(MiddlewareSpec.withContentLanguage)(_ => value.map(ev))

//   /**
//    * Adds the content language header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentLanguageZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, Unused, ContentLanguage] =
//     fromFunctionZIO(MiddlewareSpec.withContentLanguage)(_ => value.map(ContentLanguage.toContentLanguage))

//   /**
//    * Adds the content length header to the response with the given value.
//    */
//   def withContentLength(value: ContentLength): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentLength.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content length header to the response with the given value, if it
//    * is valid. Else, it adds an empty value.
//    */
//   def withContentLength(value: Long): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentLength.mapOut(_.unit(ContentLength.toContentLength(value))))(identity)

//   /**
//    * Adds the content length header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentLengthZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentLength,
//   ): Middleware[R, Unit, Unused, ContentLength] =
//     fromFunctionZIO(MiddlewareSpec.withContentLength)(_ => value.map(ev))

//   /**
//    * Adds the content length header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentLengthZIO[R](value: ZIO[R, Nothing, Long]): Middleware[R, Unit, Unused, ContentLength] =
//     fromFunctionZIO(MiddlewareSpec.withContentLength)(_ => value.map(ContentLength.toContentLength))

//   /**
//    * Adds the content location header to the response with the given value.
//    */
//   def withContentLocation(value: ContentLocation): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentLocation.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content location header to the response with the given value, if
//    * it is valid. Else, it adds an empty value.
//    */
//   def withContentLocation(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentLocation.mapOut(_.unit(ContentLocation.toContentLocation(value))))(identity)

//   /**
//    * Adds the content location header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentLocationZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentLocation,
//   ): Middleware[R, Unit, Unused, ContentLocation] =
//     fromFunctionZIO(MiddlewareSpec.withContentLocation)(_ => value.map(ev))

//   /**
//    * Adds the content location header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentLocationZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, Unused, ContentLocation] =
//     fromFunctionZIO(MiddlewareSpec.withContentLocation)(_ => value.map(ContentLocation.toContentLocation))

//   /**
//    * Adds the content md5 header to the response with the given value.
//    */
//   def withContentMd5(value: ContentMd5): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentMd5.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content md5 header to the response with the given value, if it is
//    * valid. Else, it adds an empty value.
//    */
//   def withContentMd5(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentMd5.mapOut(_.unit(ContentMd5.toContentMd5(value))))(identity)

//   /**
//    * Adds the content md5 header to the response with the value computed by the
//    * given effect.
//    */
//   def withContentMd5ZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentMd5,
//   ): Middleware[R, Unit, Unused, ContentMd5] =
//     fromFunctionZIO(MiddlewareSpec.withContentMd5)(_ => value.map(ev))

//   /**
//    * Adds the content md5 header to the response with the value computed by the
//    * given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentMd5ZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, Unused, ContentMd5] =
//     fromFunctionZIO(MiddlewareSpec.withContentMd5)(_ => value.map(ContentMd5.toContentMd5))

//   /**
//    * Adds the content range header to the response with the given value.
//    */
//   def withContentRange(value: ContentRange): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentRange.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content range header to the response with the given value, if it
//    * is valid. Else, it adds an empty value.
//    */
//   def withContentRange(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentRange.mapOut(_.unit(ContentRange.toContentRange(value))))(identity)

//   /**
//    * Adds the content range header to the response with the value computed by
//    * the given effect.
//    */
//   def withContentRangeZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentRange,
//   ): Middleware[R, Unit, Unused, ContentRange] =
//     fromFunctionZIO(MiddlewareSpec.withContentRange)(_ => value.map(ev))

//   /**
//    * Adds the content range header to the response with the value computed by
//    * the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentRangeZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, Unused, ContentRange] =
//     fromFunctionZIO(MiddlewareSpec.withContentRange)(_ => value.map(ContentRange.toContentRange))

//   /**
//    * Adds the content security policy header to the response with the given
//    * value.
//    */

//   def withContentSecurityPolicy(value: ContentSecurityPolicy): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentSecurityPolicy.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content security policy header to the response with the given
//    * value, if it is valid. Else, it adds an empty value.
//    */
//   def withContentSecurityPolicy(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(
//       MiddlewareSpec.withContentSecurityPolicy.mapOut(_.unit(ContentSecurityPolicy.toContentSecurityPolicy(value))),
//     )(identity)

//   /**
//    * Adds the content security policy header to the response with the value
//    * computed by the given effect.
//    */
//   def withContentSecurityPolicyZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentSecurityPolicy,
//   ): Middleware[R, Unit, Unused, ContentSecurityPolicy] =
//     fromFunctionZIO(MiddlewareSpec.withContentSecurityPolicy)(_ => value.map(ev))

//   /**
//    * Adds the content security policy header to the response with the value
//    * computed by the given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentSecurityPolicyZIO[R](
//     value: ZIO[R, Nothing, CharSequence],
//   ): Middleware[R, Unit, Unused, ContentSecurityPolicy] =
//     fromFunctionZIO(MiddlewareSpec.withContentSecurityPolicy)(_ =>
//       value.map(ContentSecurityPolicy.toContentSecurityPolicy),
//     )

//   /**
//    * Adds the content transfer encoding header to the response with the given
//    * value.
//    */
//   def withContentTransferEncoding(value: ContentTransferEncoding): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentTransferEncoding.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content transfer encoding header to the response with the given
//    * value, if it is valid. Else, it adds an empty value.
//    */
//   def withContentTransferEncoding(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
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
//   )(implicit ev: A <:< ContentTransferEncoding): Middleware[R, Unit, Unused, ContentTransferEncoding] =
//     fromFunctionZIO(MiddlewareSpec.withContentTransferEncoding)(_ => value.map(ev))

//   /**
//    * Adds the content transfer encoding header to the response with the value
//    */
//   def withContentTransferEncodingZIO[R](
//     value: ZIO[R, Nothing, CharSequence],
//   ): Middleware[R, Unit, Unused, ContentTransferEncoding] =
//     fromFunctionZIO(MiddlewareSpec.withContentTransferEncoding)(_ =>
//       value.map(ContentTransferEncoding.toContentTransferEncoding),
//     )

//   /**
//    * Adds the content type header to the response with the given value.
//    */
//   def withContentType(value: ContentType): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentType.mapOut(_.unit(value)))(identity)

//   /**
//    * Adds the content type header to the response with the given value, if it is
//    * valid. Else, it adds an empty value.
//    */
//   def withContentType(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(MiddlewareSpec.withContentType.mapOut(_.unit(ContentType.toContentType(value))))(identity)

//   /**
//    * Adds the content type header to the response with the value computed by the
//    * given effect.
//    */
//   def withContentTypeZIO[R, A](value: ZIO[R, Nothing, A])(implicit
//     ev: A <:< ContentType,
//   ): Middleware[R, Unit, Unused, ContentType] =
//     fromFunctionZIO(MiddlewareSpec.withContentType)(_ => value.map(ev))

//   /**
//    * Adds the content type header to the response with the value computed by the
//    * given effect, if it is valid. Else, it adds an empty value.
//    */
//   def withContentTypeZIO[R](value: ZIO[R, Nothing, CharSequence]): Middleware[R, Unit, Unused, ContentType] =
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
//   )(implicit trace: Trace): api.Middleware[R, Unit, Unused, Cookie[Response]] = {
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
//   def csrfValidate(tokenName: String = "x-csrf-token"): Middleware[Any, CsrfValidate, Unit, Unit] =
//     MiddlewareSpec
//       .csrfValidate(tokenName)
//       .implement {
//         case CsrfValidate(Some(cookieValue), Some(tokenValue)) if cookieValue.content == tokenValue =>
//           ZIO.unit

//         case _ => ZIO.fail(())
//       }((_, _) => ZIO.unit)

//   def fromFunction[A, E, B](spec: MiddlewareSpec[A, E, B])(
//     f: A => B,
//   ): Middleware[spec.type, Any, A] =
//     intercept(spec)((a: A) => Right(a))((a, _) => f(a))

//   def fromFunctionZIO[R, A, E, B](spec: MiddlewareSpec[A, E, B])(
//     f: A => ZIO[R, Nothing, B],
//   ): Middleware[spec.type, R, A] =
//     interceptZIO(spec)((a: A) => ZIO.succeedNow(a))((a, _) => f(a))

//   def withAccessControlAllowOrigin(value: CharSequence): Middleware[Any, Unit, Unused, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowOrigin.mapOut(
//         _.unit(AccessControlAllowOrigin.toAccessControlAllowOrigin(value.toString)),
//       ),
//     )(identity)
//   }

//   def withAccessControlAllowMaxAge(value: CharSequence): Middleware[Any, Unit, Unused, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowMaxAge.mapOut(
//         _.unit(AccessControlMaxAge.toAccessControlMaxAge(value.toString)),
//       ),
//     )(identity)
//   }

//   def withProxyAuthenticate(value: CharSequence): Middleware[Any, Unit, Unused, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withProxyAuthenticate.mapOut(
//         _.unit(ProxyAuthenticate.toProxyAuthenticate(value.toString)),
//       ),
//     )(identity)
//   }

//   def withProxyAuthorization(value: CharSequence): Middleware[Any, Unit, Unused, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withProxyAuthorization.mapOut(
//         _.unit(ProxyAuthorization.toProxyAuthorization(value.toString)),
//       ),
//     )(identity)
//   }

//   def withReferer(value: CharSequence): Middleware[Any, Unit, Unused, Unit]    = {
//     fromFunction(
//       MiddlewareSpec.withReferer.mapOut(
//         _.unit(Referer.toReferer(value.toString)),
//       ),
//     )(identity)
//   }
//   def withRetryAfter(value: CharSequence): Middleware[Any, Unit, Unused, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withRetryAfter.mapOut(
//         _.unit(RetryAfter.toRetryAfter(value.toString)),
//       ),
//     )(identity)
//   }

//   def withAccessControlAllowCredentials(value: Boolean): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowCredentials.mapOut(
//         _.unit(AccessControlAllowCredentials.toAccessControlAllowCredentials(value)),
//       ),
//     )(identity)

//   def withAccessControlAllowMethods(value: Method*): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowMethods.mapOut(
//         _.unit(AccessControlAllowMethods.AllowMethods(Chunk.fromIterable(value))),
//       ),
//     )(identity)

//   def withAccessControlAllowMethods(value: CharSequence): Middleware[Any, Unit, Unused, Unit] =
//     fromFunction(
//       MiddlewareSpec.withAccessControlAllowMethods.mapOut(
//         _.unit(AccessControlAllowMethods.toAccessControlAllowMethods(value.toString)),
//       ),
//     )(identity)

//   def withTransferEncoding(value: CharSequence): Middleware[Any, Unit, Unused, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withTransferEncoding.mapOut(
//         _.unit(TransferEncoding.toTransferEncoding(value.toString)),
//       ),
//     )(identity)
//   }

//   def withConnection(value: CharSequence): Middleware[Any, Unit, Unused, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withConnection.mapOut(
//         _.unit(Connection.toConnection(value.toString)),
//       ),
//     )(identity)
//   }

//   def withExpires(value: CharSequence): Middleware[Any, Unit, Unused, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withExpires.mapOut(
//         _.unit(Expires.toExpires(value.toString)),
//       ),
//     )(identity)
//   }

//   def withIfRange(value: CharSequence): Middleware[Any, Unit, Unused, Unit] = {
//     fromFunction(
//       MiddlewareSpec.withIfRange.mapOut(
//         _.unit(IfRange.toIfRange(value.toString)),
//       ),
//     )(identity)
//   }

//   val none: Middleware[MiddlewareSpec.none.type, Any, Unit] =
//     fromFunction(MiddlewareSpec.none)(_ => ())

//   class Interceptor1[S](val dummy: Boolean = true) extends AnyVal {
//     def apply[R, I, E, O](spec: MiddlewareSpec[I, E, O])(
//       incoming: I => ZIO[R, E, S],
//     ): Interceptor2[S, R, I, E, O] =
//       new Interceptor2[S, R, I, E, O](spec, incoming)
//   }

//   class Interceptor2[S, R, I, E, O](spec: MiddlewareSpec[I, E, O], incoming: I => ZIO[R, E, S]) {
//     def apply[R1 <: R](outgoing: (S, Response) => ZIO[R1, E, O]): Middleware[R1, I, E, O] =
//       InterceptZIO(spec, incoming, outgoing)
//   }

//   private[api] final case class InterceptZIO[Id, State, R, I0, E0, O0](
//     spec: MiddlewareSpec[I0, E0, O0],
//     incoming0: I0 => ZIO[R, E0, State],
//     outgoing0: (State, Response) => ZIO[R, E0, O0],
//   ) extends Middleware[Id, R, State] {
//     type I = I0 
//     type E = E0 
//     type O = O0 

//     def incoming(in: I0): ZIO[R, E0, State] = incoming0(in)

//     def outgoing(state: State, response: Response): ZIO[R, E, O] = outgoing0(state, response)
//   }
//   private[api] final case class Concat[Id1, Id2, -R, I1, I2, I3, E1, E2, E3, O1, O2, O3, State1, State2, State3](
//     left: Middleware[Id1, R, State1],
//     right: Middleware[Id2, R, State2],
//     inCombiner: Combiner.WithOut[I1, I2, I3],
//     errAlternator: Alternator.WithOut[E1, E2, E3],
//     outCombiner: Combiner.WithOut[O1, O2, O3],
//     stateCombiner: Combiner.WithOut[State1, State2, State3],
//   ) extends Middleware[Id1 with Id2, R, State3] {
//     def incoming(in: I3): ZIO[R, E3, State3] = {
//       val (l, r) = inCombiner.separate(in)

//       val leftIn  = left.incoming(l).mapError(errAlternator.left(_))
//       val rightIn = right.incoming(r).mapError(errAlternator.right(_))

//       leftIn.zip(rightIn)
//     }

//     def outgoing(state: State3, response: Response): ZIO[R, E3, O3] =
//       for {
//         l <- left.outgoing(state._1, response).mapError(errAlternator.left(_))
//         r <- right.outgoing(state._2, response).mapError(errAlternator.right(_))
//       } yield outCombiner.combine(l, r)

//     def spec: MiddlewareSpec[I3, E3, O3] =
//       left.spec.++(right.spec)(inCombiner, outCombiner, errAlternator)
//   }
// }
