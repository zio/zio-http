package zio.http.middleware

import io.netty.handler.codec.http.HttpHeaderValues
import zio._
import zio.http.URL.encode
import zio.http._
import zio.http.html._
import zio.http.middleware.{IT, ITIfThenElse, ITOrElse}
import zio.http.middleware.Web.{PartialInterceptPatch, PartialInterceptZIOPatch}
import zio.http.model._
import zio.http.model.headers._

import java.io.{IOException, PrintWriter, StringWriter}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Middlewares on an HttpApp
 */
private[zio] trait Web
    extends Cors
    with Auth
    with RequestLogging
    with Metrics
    with HeaderModifier[HttpMiddleware[Any, Nothing, IT.Id[Request]]] {
  self =>

  /**
   * Sets cookie in response headers
   */
  final def addCookie(cookie: Cookie[Response]): HttpMiddleware[Any, Nothing, IT.Id[Request]] =
    self.withSetCookie(cookie)

  final def addCookieZIO[R, E](cookie: ZIO[R, E, Cookie[Response]]): HttpMiddleware[R, E, IT.Id[Request]] =
    new HttpMiddleware[R, E, IT.Id[Request]] {

      override def inputTransformation: IT.Id[Request] = IT.Id()

      override def apply[R1 <: R, E1 >: E](
        app: Http.Total[R1, E1, Request, Response],
      )(implicit trace: Trace): Http.Total[R1, E1, Request, Response] =
        Http.fromFunctionZIO { request =>
          for {
            response <- app.toZIO(request)
            patch    <- cookie.map(c => Patch.addHeader(Headers.setCookie(c)))
          } yield patch(response)
        }
    }

  /**
   * Beautify the error response.
   */
  final def beautifyErrors: HttpMiddleware[Any, Nothing, IT.Id[Request]] =
    Middleware.intercept[Request, Response](identity)((res, req) => Web.updateErrorResponse(res, req))

  /**
   * Add log status, method, url and time taken from req to res
   */
  final def debug: HttpMiddleware[Any, IOException, IT.Id[Request]] =
    new HttpMiddleware[Any, IOException, IT.Id[Request]] {

      override def inputTransformation: IT.Id[Request] = IT.Id()

      override def apply[R, E >: IOException](app: Http.Total[R, E, Request, Response])(implicit
        trace: Trace,
      ): Http.Total[R, E, Request, Response] =
        Http.fromFunctionZIO { request =>
          for {
            start    <- Clock.nanoTime
            response <- app.toZIO(request)
            end      <- Clock.nanoTime
            _        <- Console
              .printLine(
                s"${response.status.asJava.code()} ${request.method} ${request.url.encode} ${(end - start) / 1000000}ms",
              )
          } yield response
        }
    }

  /**
   * Removes the trailing slash from the path.
   */
  final def dropTrailingSlash: HttpMiddleware[Any, Nothing, IT.Contramap[Request, Request]] =
    Middleware.identity[Request, Response].contramap[Request](_.dropTrailingSlash).when(_.url.queryParams.isEmpty)

  /**
   * Logical operator to decide which middleware to select based on the header
   */
  final def ifHeaderThenElse[R, E, ReqT1 <: IT[Request], ReqT2 <: IT[Request], ReqT <: IT[Request]](
    cond: Headers => Boolean,
  )(left: HttpMiddleware[R, E, ReqT1], right: HttpMiddleware[R, E, ReqT2])(implicit
    ev: ITIfThenElse.Aux[Request, Request, Request, Request, ReqT1, ReqT2, ReqT],
  ): HttpMiddleware[R, E, ReqT] =
    Middleware.ifThenElse[Request](req => cond(req.headers))(_ => left, _ => right)

  /**
   * Logical operator to decide which middleware to select based on the method.
   */
  final def ifMethodThenElse[R, E, ReqT1 <: IT[Request], ReqT2 <: IT[Request], ReqT <: IT[Request]](
    cond: Method => Boolean,
  )(left: HttpMiddleware[R, E, ReqT1], right: HttpMiddleware[R, E, ReqT2])(implicit
    ev: ITIfThenElse.Aux[Request, Request, Request, Request, ReqT1, ReqT2, ReqT],
  ): HttpMiddleware[R, E, ReqT] =
    Middleware.ifThenElse[Request](req => cond(req.method))(_ => left, _ => right)

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate.
   */
  final def ifRequestThenElse[R, E, ReqT1 <: IT[Request], ReqT2 <: IT[Request], ReqT <: IT[Request]](
    cond: Request => Boolean,
  )(left: HttpMiddleware[R, E, ReqT1], right: HttpMiddleware[R, E, ReqT2])(implicit
    ev: ITIfThenElse.Aux[Request, Request, Request, Request, ReqT1, ReqT2, ReqT],
  ): HttpMiddleware[R, E, ReqT] =
    Middleware.ifThenElse[Request](cond)(_ => left, _ => right)

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate.
   */
  final def ifRequestThenElseZIO[R, E, ReqT1 <: IT[Request], ReqT2 <: IT[Request], ReqT <: IT[Request]](
    cond: Request => ZIO[R, E, Boolean],
  )(left: HttpMiddleware[R, E, ReqT1], right: HttpMiddleware[R, E, ReqT2])(implicit
    ev: ITOrElse.Aux[ReqT1, ReqT2, ReqT],
  ): HttpMiddleware[R, E, ReqT] =
    Middleware.ifThenElseZIOStatic[Request](cond)(left, right)

  /**
   * Creates a new middleware using transformation functions
   */
  final def interceptPatch[S](req: Request => S): PartialInterceptPatch[S] = PartialInterceptPatch(req)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  final def interceptZIOPatch[R, E, S](req: Request => ZIO[R, E, S]): PartialInterceptZIOPatch[R, E, S] =
    PartialInterceptZIOPatch(req)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  final def patch[R, E](f: Response => Patch): HttpMiddleware[R, E, IT.Id[Request]] =
    Middleware.interceptPatch(_ => ())((res, _) => f(res))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  final def patchZIO[R, E](f: Response => ZIO[R, E, Patch]): HttpMiddleware[R, E, IT.Id[Request]] =
    Middleware.interceptZIOPatch(_ => ZIO.unit)((res, _) => f(res))

  /**
   * Client redirect temporary or permanent to specified url.
   */
  final def redirect(url: URL, permanent: Boolean): HttpMiddleware[Any, Nothing, IT.Id[Request]] =
    Middleware.fromHttp(
      Http.response(Response.redirect(encode(url), isPermanent = permanent)),
    )

  /**
   * Permanent redirect if the trailing slash is present in the request URL.
   */
  final def redirectTrailingSlash(permanent: Boolean): HttpMiddleware[Any, Nothing, IT.Id[Request]] =
    Middleware.ifThenElse[Request](_.url.path.trailingSlash)(
      req => redirect(req.dropTrailingSlash.url, permanent).when(_.url.queryParams.isEmpty),
      _ => Middleware.identity,
    )

  /**
   * Runs the effect after the middleware is applied
   */
  final def runAfter[R, E](effect: ZIO[R, E, Any]): HttpMiddleware[R, E, IT.Id[Request]] =
    new HttpMiddleware[R, E, IT.Id[Request]] {

      override def inputTransformation: IT.Id[Request] = IT.Id()

      override def apply[R1 <: R, E1 >: E](app: Http.Total[R1, E1, Request, Response])(implicit
        trace: Trace,
      ): Http.Total[R1, E1, Request, Response] =
        Http.fromFunctionZIO { request =>
          for {
            response <- app.toZIO(request)
            _        <- effect
          } yield response
        }
    }

  /**
   * Runs the effect before the request is passed on to the HttpApp on which the
   * middleware is applied.
   */
  final def runBefore[R, E](effect: ZIO[R, E, Any]): HttpMiddleware[R, E, IT.Id[Request]] =
    new HttpMiddleware[R, E, IT.Id[Request]] {
      override def inputTransformation: IT.Id[Request] = IT.Id()

      override def apply[R1 <: R, E1 >: E](app: Http.Total[R1, E1, Request, Response])(implicit
        trace: Trace,
      ): Http.Total[R1, E1, Request, Response] =
        Http.fromFunctionZIO { request =>
          for {
            _        <- effect
            response <- app.toZIO(request)
          } yield response
        }
    }

  /**
   * Creates a new middleware that always sets the response status to the
   * provided value
   */
  final def setStatus(status: Status): HttpMiddleware[Any, Nothing, IT.Id[Request]] =
    patch(_ => Patch.setStatus(status))

  /**
   * Creates a middleware for signing cookies
   */
  final def signCookies(secret: String): HttpMiddleware[Any, Nothing, IT.Id[Request]] =
    updateHeaders {
      case h if h.header(HeaderNames.setCookie).isDefined =>
        Cookie
          .decode[Response](h.header(HeaderNames.setCookie).get._2.toString)
          .map(_.sign(secret))
          .map { cookie => Headers.setCookie(cookie) }
          .getOrElse(h)

      case h => h
    }

  /**
   * Times out the application with a 408 status code.
   */
  final def timeout(duration: Duration): HttpMiddleware[Any, Nothing, IT.Id[Request]] =
    new HttpMiddleware[Any, Nothing, IT.Id[Request]] {

      override def inputTransformation: IT.Id[Request] = IT.Id()

      def apply[R, E](
        app: Http.Total[R, E, Request, Response],
      )(implicit trace: Trace): Http.Total[R, E, Request, Response] =
        Http.fromFunctionZIO { request =>
          app.toZIO(request).timeoutTo(Response.status(Status.RequestTimeout))(identity)(duration)
        }
    }

  /**
   * Updates the provided list of headers to the response
   */
  final def updateHeaders(update: Headers => Headers): HttpMiddleware[Any, Nothing, IT.Id[Request]] =
    Middleware.updateResponse(_.updateHeaders(update))

  /**
   * Creates a middleware that updates the response produced
   */
  final def updateResponse[R, E](f: Response => Response): HttpMiddleware[R, E, IT.Id[Request]] =
    Middleware.intercept[Request, Response](_ => ())((res, _) => f(res))

  /**
   * Applies the middleware only when the condition for the headers are true
   */
  final def whenHeader[R, E, ReqT <: IT[Request], ReqTR <: IT[Request]](
    cond: Headers => Boolean,
    middleware: HttpMiddleware[R, E, ReqT],
  )(implicit
    ev: ITIfThenElse.Aux[Request, Request, Request, Request, ReqT, IT.Id[Request], ReqTR],
  ): HttpMiddleware[R, E, ReqTR] =
    middleware.when(req => cond(req.headers))

  /**
   * Applies the middleware only if status matches the condition
   */
  final def whenStatus[R, E, ReqT <: IT[Request], ReqT2 <: IT[Request]](cond: Status => Boolean)(
    middleware: HttpMiddleware[R, E, ReqT],
  )(implicit
    ev: ITIfThenElse.Aux[Request, Request, Request, Request, ReqT, IT.Id[Request], ReqT2],
  ): HttpMiddleware[R, E, IT.Impossible[Request]] =
    whenResponse(response => cond(response.status))(middleware)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  final def whenRequest[R, E, ReqT <: IT[Request], ReqTR <: IT[Request]](
    cond: Request => Boolean,
  )(middleware: HttpMiddleware[R, E, ReqT])(implicit
    ev: ITIfThenElse.Aux[Request, Request, Request, Request, ReqT, IT.Id[Request], ReqTR],
  ): HttpMiddleware[R, E, ReqTR] =
    middleware.when(cond)

  /**
   * Applies the middleware only if the condition function effectfully evaluates
   * to true
   */
  final def whenRequestZIO[R, E, ReqT <: IT[Request], ReqTR <: IT[Request]](
    cond: Request => ZIO[R, E, Boolean],
  )(middleware: HttpMiddleware[R, E, ReqT])(implicit
    ev: ITOrElse.Aux[ReqT, IT.Id[Request], ReqTR],
  ): HttpMiddleware[R, E, ReqTR] =
    middleware.whenZIO(cond)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenResponse[R, E, ReqT <: IT[Request], ReqT2 <: IT[Request]](
    cond: Response => Boolean,
  )(middleware: HttpMiddleware[R, E, ReqT])(implicit
    ev: ITIfThenElse.Aux[Request, Request, Request, Request, ReqT, IT.Id[Request], ReqT2],
  ): HttpMiddleware[R, E, IT.Impossible[Request]] =
    Middleware.identity[Request, Response].flatMap(response => middleware.when((_: Request) => cond(response)))

  /**
   * Applies the middleware only if the condition function effectfully evaluates
   * to true
   */
  def whenResponseZIO[R, E, ReqT <: IT[Request], ReqT2 <: IT[Request]](
    cond: Response => ZIO[R, E, Boolean],
  )(middleware: HttpMiddleware[R, E, ReqT])(implicit
    ev: ITOrElse.Aux[ReqT, IT.Id[Request], ReqT2],
  ): HttpMiddleware[R, E, IT.Impossible[Request]] =
    Middleware.identity[Request, Response].flatMap(response => middleware.whenZIO((_: Request) => cond(response)))
}

object Web {

  final case class PartialInterceptPatch[S](req: Request => S) extends AnyVal {
    def apply(res: (Response, S) => Patch): HttpMiddleware[Any, Nothing, IT.Id[Request]] = {
      Middleware.intercept[Request, Response](req(_))((response, state) => res(response, state)(response))
    }
  }

  final case class PartialInterceptZIOPatch[R, E, S](req: Request => ZIO[R, E, S]) extends AnyVal {
    def apply[R1 <: R, E1 >: E](
      res: (Response, S) => ZIO[R1, E1, Patch],
    ): HttpMiddleware[R1, E1, IT.Id[Request]] =
      new HttpMiddleware[R1, E1, IT.Id[Request]] {

        override def inputTransformation: IT.Id[Request] = IT.Id()

        def apply[R2 <: R1, E2 >: E1](
          app: Http.Total[R2, E2, Request, Response],
        )(implicit trace: Trace): Http.Total[R2, E2, Request, Response] =
          Http.fromFunctionZIO { request =>
            for {
              s        <- req(request)
              response <- app.toZIO(request)
              c        <- res(response, s)
            } yield c(response)
          }
      }
  }

  private[middleware] def updateErrorResponse(response: Response, request: Request): Response = {
    def htmlResponse: Body = {
      val message: String = response.httpError.map(_.message).getOrElse("")
      val data            = Template.container(s"${response.status}") {
        div(
          div(
            styles := Seq("text-align" -> "center"),
            div(s"${response.status.code}", styles := Seq("font-size" -> "20em")),
            div(message),
          ),
          div(
            response.httpError.get.foldCause(div()) { throwable =>
              div(h3("Cause:"), pre(prettify(throwable)))
            },
          ),
        )
      }
      Body.fromString("<!DOCTYPE html>" + data.encode)
    }

    def textResponse: Body = {
      Body.fromString(formatErrorMessage(response))
    }

    if (response.status.isError) {
      request.accept match {
        case Some(value) if value.toString.contains(HttpHeaderValues.TEXT_HTML) =>
          response.copy(
            body = htmlResponse,
            headers = Headers(HeaderNames.contentType, model.HeaderValues.textHtml),
          )
        case Some(value) if value.toString.equals("*/*")                        =>
          response.copy(
            body = textResponse,
            headers = Headers(HeaderNames.contentType, model.HeaderValues.textPlain),
          )
        case _                                                                  => response
      }

    } else
      response
  }

  private def prettify(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    s"${sw.toString}"
  }

  private def formatCause(response: Response): String =
    response.httpError.get.foldCause("") { throwable =>
      s"${scala.Console.BOLD}Cause: ${scala.Console.RESET}\n ${prettify(throwable)}"
    }

  private def formatErrorMessage(response: Response) = {
    val errorMessage: String = response.httpError.map(_.message).getOrElse("")
    val status               = response.status.code
    s"${scala.Console.BOLD}${scala.Console.RED}${response.status} ${scala.Console.RESET} - " +
      s"${scala.Console.BOLD}${scala.Console.CYAN}$status ${scala.Console.RESET} - " +
      s"${errorMessage}\n${formatCause(response)}"
  }
}
