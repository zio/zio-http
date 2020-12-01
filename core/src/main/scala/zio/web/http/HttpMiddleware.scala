package zio.web.http

import zio._
import java.nio.charset.StandardCharsets

/**
 * An `HttpMiddleware[R, E]` value defines HTTP middleware that requires an
 * environment `R` and may fail with error type `E`.
 *
 * Middleware can be used to add request/response logging, metrics, monitoring,
 * authentication, authorization, and other features.
 */
final case class HttpMiddleware[-R, +E](make: ZIO[R, Nothing, HttpMiddleware.Middleware[R, E]]) { self =>
  import HttpMiddleware.Middleware

  /**
   * Composes this middleware with the specified middleware.
   */
  def <>[R1 <: R, E1 >: E](that: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware(
      self.make
        .zipWith(that.make)((self, that) => Middleware(self.request <> that.request, self.response <> that.response))
    )

  def mapError[E2](f: E => E2): HttpMiddleware[R, E2] =
    HttpMiddleware(self.make.map(middleware => middleware.mapError(f)))
}

object HttpMiddleware {

  trait Middleware[-R, +E] {
    type State

    val request: Request[R, E, State]
    val response: Response[R, E, State]

    def mapError[E2](f: E => E2): Middleware.Aux[R, E2, State] =
      Middleware(request.mapError(f), response.mapError(f))

    def runRequest(method: String, uri: java.net.URI, headers: HttpHeaders): ZIO[R, Option[(State, E)], State] =
      request.run(method, uri, headers)

    def runResponse[S2 <: State](s: S2, statusCode: Int, headers: HttpHeaders): ZIO[R, Option[E], Patch] =
      response.run(s, statusCode, headers)
  }

  object Middleware {
    type Aux[-R, +E, S] = Middleware[R, E] { type State = S }

    def apply[R, E, S](request0: Request[R, E, S], response0: Response[R, E, S]): Middleware.Aux[R, E, S] =
      new Middleware[R, E] {
        type State = S

        val request  = request0
        val response = response0
      }
  }

  def basicAuth[R, E](authenticate: Option[(String, String)] => ZIO[R, E, Unit]): HttpMiddleware[R, E] =
    HttpMiddleware(
      ZIO.succeed(
        Middleware(
          request(HttpRequest.Header("Authorization")).stateless { header =>
            if (header.startsWith("Basic")) {
              val data = header.drop("Basic ".length).trim

              // TODO: base64 decode data
              val decoded = new String(java.util.Base64.getDecoder().decode(data), StandardCharsets.UTF_8)

              val split = decoded.split(":")

              if (split.length == 2) {
                val username = split(0)
                val password = split(1)

                authenticate(Some(username -> password))
              } else authenticate(None)
            } else authenticate(None)
          },
          Response.none
        )
      )
    )

  val logging: HttpMiddleware[zio.console.Console, Nothing] =
    HttpMiddleware(
      ZIO.succeed(
        Middleware(
          request(HttpRequest.Method.zip(HttpRequest.URI))(tuple => zio.console.putStrLn(tuple.toString)),
          Response.none
        )
      )
    )

  def rateLimiter(n: Int): HttpMiddleware[Any, None.type] =
    HttpMiddleware(
      Ref
        .make[Int](0)
        .flatMap(
          ref =>
            ZIO.succeed {
              Middleware(
                Request(
                  HttpRequest.Succeed,
                  (_: Unit) =>
                    ref.modify { old =>
                      if (old < n) (ZIO.succeed(true), n + 1) else (ZIO.fail(false -> None), n)
                    }.flatten
                ),
                Response(
                  HttpResponse.Succeed,
                  (flag: Boolean, _: Unit) => (if (flag) ref.update(_ - 1) else ZIO.unit).as(Patch.empty)
                )
              )
            }
        )
    )

  /**
   * HTTP middleware that does nothing.
   */
  val none: HttpMiddleware[Any, Nothing] = HttpMiddleware(ZIO.succeed(Middleware(Request.none, Response.none)))

  trait Request[-R, +E, +S] { self =>
    type Metadata

    val pattern: HttpRequest[Metadata]

    val processor: Metadata => ZIO[R, (S, E), S]

    def <>[R1 <: R, E1 >: E, S2](that: Request[R1, E1, S2]): Request[R1, E1, (S, S2)] =
      new Request[R1, E1, (S, S2)] {
        type Metadata = (self.Metadata, that.Metadata)

        val pattern = self.pattern.zip(that.pattern)

        val processor = (metadata: Metadata) =>
          self.processor(metadata._1).either.zip(that.processor(metadata._2).either).flatMap {
            case (Left((ls, le)), Right(rs))     => ZIO.fail((ls -> rs, le))
            case (Right(ls), Left((rs, re)))     => ZIO.fail((ls -> rs, re))
            case (Left((ls, le)), Left((rs, _))) => ZIO.fail((ls -> rs, le))
            case (Right(ls), Right(rs))          => ZIO.succeed(ls -> rs)
          }
      }

    def mapError[E2](f: E => E2): Request[R, E2, S] =
      Request(pattern, (m: Metadata) => self.processor(m).mapError { case (s, e) => (s, f(e)) })

    def run(method: String, uri: java.net.URI, headers: HttpHeaders): ZIO[R, Option[(S, E)], S] =
      pattern.run(method, uri, headers) match {
        case Some(metadata) => processor(metadata).mapError(Some(_))
        case None           => ZIO.fail(None)
      }
  }

  object Request {

    def apply[R, E, S, M](p: HttpRequest[M], f: M => ZIO[R, (S, E), S]): Request[R, E, S] =
      new Request[R, E, S] {
        type Metadata = M
        val pattern   = p
        val processor = f
      }

    val none: Request[Any, Nothing, Unit] = apply(HttpRequest.Succeed, (_: Unit) => ZIO.unit)

    def stateless[R, E, M](p: HttpRequest[M], f: M => ZIO[R, E, Any]): Request[R, E, Unit] =
      Request[R, E, Unit, M](p, m => f(m).unit.bimap(e => ((), e), _ => ()))
  }

  def request[M](request: HttpRequest[M]): RequestBuilder[M] = new RequestBuilder[M](request)

  class RequestBuilder[M](request: HttpRequest[M]) {
    def apply[R, E, S](f: M => ZIO[R, (S, E), S]): Request[R, E, S] = Request(request, f)

    def stateless[R, E, S](f: M => ZIO[R, E, Any]): Request[R, E, Unit] = Request.stateless(request, f)
  }

  trait Response[-R, +E, -S] { self =>
    type Metadata

    val pattern: HttpResponse[Metadata]

    val processor: (S, Metadata) => ZIO[R, E, Patch]

    def <>[R1 <: R, E1 >: E, S2](that: Response[R1, E1, S2]): Response[R1, E1, (S, S2)] =
      new Response[R1, E1, (S, S2)] {
        type Metadata = (self.Metadata, that.Metadata)

        val pattern = self.pattern.zip(that.pattern)

        val processor = (state: (S, S2), metadata: Metadata) =>
          self.processor(state._1, metadata._1).zipWith(that.processor(state._2, metadata._2))(_ + _)
      }

    def mapError[E2](f: E => E2): Response[R, E2, S] =
      Response(pattern, (s: S, m: Metadata) => processor(s, m).mapError(f))

    def run(s: S, statusCode: Int, headers: HttpHeaders): ZIO[R, Option[E], Patch] =
      pattern.run(statusCode, headers) match {
        case Some(metadata) => processor(s, metadata).mapError(Some(_))
        case None           => ZIO.fail(None)
      }
  }

  object Response {

    def apply[R, E, S, M](p: HttpResponse[M], f: (S, M) => ZIO[R, E, Patch]): Response[R, E, S] =
      new Response[R, E, S] {
        type Metadata = M
        val pattern   = p
        val processor = f
      }

    val none: Response[Any, Nothing, Any] =
      apply[Any, Nothing, Any, Unit](HttpResponse.Succeed, (_, _) => ZIO.succeed(Patch.empty))
  }
}
