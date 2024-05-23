package zio.http.internal

import scala.collection.compat.immutable.ArraySeq
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

import zio._

import zio.http._

import org.scalajs.dom
import org.scalajs.dom.BodyInit

final case class FetchDriver() extends ZClient.Driver[Any, Throwable] {
  override def request(
    version: Version,
    requestMethod: Method,
    url: URL,
    requestHeaders: Headers,
    requestBody: Body,
    sslConfig: Option[ClientSSLConfig],
    proxy: Option[Proxy],
  )(implicit trace: Trace): ZIO[Any & Scope, Throwable, Response] = {
    for {
      jsBody   <- fromZBody(requestBody)
      response <-
        ZIO.fromFuture { implicit ec =>
          val jsMethod  = fromZMethod(requestMethod)
          val jsHeaders = js.Dictionary(requestHeaders.map(h => h.headerName -> h.renderedValue).toSeq: _*)
          for {
            response <- dom
              .fetch(
                url.encode,
                new dom.RequestInit {
                  method = jsMethod
                  headers = jsHeaders
                  body = jsBody
                },
              )
              .toFuture
          } yield Response(
            status = Status.fromInt(response.status),
            headers = Headers.fromIterable(response.headers.map(h => Header.Custom(h(0), h(1)))),
            body = FetchBody.fromResponse(response),
          )

        }
    } yield response
  }

  private def fromZMethod(method: Method): dom.HttpMethod = method match {
    case Method.GET          => dom.HttpMethod.GET
    case Method.POST         => dom.HttpMethod.POST
    case Method.PUT          => dom.HttpMethod.PUT
    case Method.PATCH        => dom.HttpMethod.PATCH
    case Method.DELETE       => dom.HttpMethod.DELETE
    case Method.HEAD         => dom.HttpMethod.HEAD
    case Method.OPTIONS      => dom.HttpMethod.OPTIONS
    case Method.ANY          => dom.HttpMethod.POST
    case Method.CUSTOM(name) => throw new IllegalArgumentException(s"Custom method $name is not supported")
    case Method.TRACE        => throw new IllegalArgumentException("TRACE is not supported")
    case Method.CONNECT      => throw new IllegalArgumentException("CONNECT is not supported")
  }

  private def fromZBody(body: Body): ZIO[Any, Throwable, js.UndefOr[BodyInit]] =
    if (body.isEmpty) {
      ZIO.succeed(js.undefined)
    } else {
      body.asArray.map { ar => Uint8Array.of(ArraySeq.unsafeWrapArray(ar.map(_.toShort)): _*) }
    }

  override def socket[Env1 <: Any](version: Version, url: URL, headers: Headers, app: WebSocketApp[Env1])(implicit
    trace: Trace,
  ): ZIO[Env1 & Scope, Throwable, Response] =
    throw new UnsupportedOperationException("WebSockets are not supported in the js client yet.")

}

object FetchDriver {

  val live: ZLayer[Any, Nothing, FetchDriver] =
    ZLayer.succeed(FetchDriver())

}
