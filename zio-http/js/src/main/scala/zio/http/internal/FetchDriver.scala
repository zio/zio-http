package zio.http.internal

import scala.collection.compat.immutable.ArraySeq
import scala.scalajs.js
import scala.scalajs.js.typedarray.{TypedArrayBuffer, Uint8Array}

import zio._

import zio.http._

import org.scalajs.dom
import org.scalajs.dom.BodyInit

final case class FetchDriver() extends ZClient.Driver[Any, Scope, Throwable] {
  override def request(
    version: Version,
    requestMethod: Method,
    url: URL,
    requestHeaders: Headers,
    requestBody: Body,
    sslConfig: Option[ClientSSLConfig],
    proxy: Option[Proxy],
  )(implicit trace: Trace): ZIO[Scope, Throwable, Response] =
    for {
      jsBody <- FetchDriver.fromZBody(requestBody)
      jsMethod  = FetchDriver.fromZMethod(requestMethod)
      jsHeaders = js.Dictionary(requestHeaders.map(h => h.headerName -> h.renderedValue).toSeq: _*)
      abortSignal <- FetchDriver.makeAbortSignal
      response    <-
        ZIO.fromPromiseJS {
          dom.fetch(
            url.encode,
            new dom.RequestInit {
              method = jsMethod
              headers = jsHeaders
              body = jsBody
              signal = abortSignal
            },
          )
        }
      respHeaders = Headers.fromIterable(response.headers.map(h => Header.Custom(h(0), h(1))))
      ct = respHeaders.get(Header.ContentType)
    } yield Response(
      status = Status.fromInt(response.status),
      headers = respHeaders,
      body = FetchBodyInternal.fromResponse(response, ct.map(Body.ContentType.fromHeader)),
    )

  override def disableStreaming(implicit ev1: Scope =:= Scope): ZClient.Driver[Any, Any, Throwable] =
    FetchDriverBatched()

  override def socket[Env1](version: Version, url: URL, headers: Headers, app: WebSocketApp[Env1])(implicit
    trace: Trace,
    ev: Scope =:= Scope,
  ): ZIO[Env1 & Scope, Throwable, Response] =
    ZIO.die(new UnsupportedOperationException("WebSockets are not supported in the js client yet."))

}

object FetchDriver {

  val live: ZLayer[Any, Nothing, FetchDriver] =
    ZLayer.succeed(FetchDriver())

  private[http] def fromZMethod(method: Method): dom.HttpMethod = method match {
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

  private[http] def fromZBody(body: Body): ZIO[Any, Throwable, js.UndefOr[BodyInit]] =
    if (body.isEmpty) {
      ZIO.succeed(js.undefined)
    } else {
      body.asArray.map { ar => Uint8Array.of(ArraySeq.unsafeWrapArray(ar.map(_.toShort)): _*) }
    }

  // Without this, if you have a streaming request, and disconnect from the stream, the connection will leak and stay open indefinitely.
  private[http] def makeAbortSignal: URIO[Scope, dom.AbortSignal] =
    for {
      controller <- ZIO.succeed { new dom.AbortController() }
      _          <- ZIO.addFinalizer { ZIO.succeed { controller.abort() } }
    } yield controller.signal

}

private[http] final case class FetchDriverBatched() extends ZClient.Driver[Any, Any, Throwable] {
  self =>
  override def request(
    version: Version,
    requestMethod: Method,
    url: URL,
    requestHeaders: Headers,
    requestBody: Body,
    sslConfig: Option[ClientSSLConfig],
    proxy: Option[Proxy],
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] =
    ZIO.scoped {
      for {
        jsBody <- FetchDriver.fromZBody(requestBody)
        jsMethod  = FetchDriver.fromZMethod(requestMethod)
        jsHeaders = js.Dictionary(requestHeaders.map(h => h.headerName -> h.renderedValue).toSeq: _*)
        abortSignal <- FetchDriver.makeAbortSignal
        response    <- ZIO.fromPromiseJS {
          dom.fetch(
            url.encode,
            new dom.RequestInit {
              method = jsMethod
              headers = jsHeaders
              body = jsBody
              signal = abortSignal
            },
          )
        }
        bytes       <- ZIO.fromPromiseJS { response.arrayBuffer() }.map { buf =>
          val view = new scala.scalajs.js.typedarray.Uint8Array(buf)
          val out  = new Array[Byte](view.length)
          var i    = 0
          while (i < view.length) { out(i) = view(i).toByte; i += 1 }
          out
        }.catchAllCause { _ =>
          ZIO.logDebug("Error fetching body bytes, using text fetch instead") *>
            ZIO.fromPromiseJS { response.clone().text() }.map(_.getBytes(Charsets.Http))
        }
        respHeaders = Headers.fromIterable(response.headers.map(h => Header.Custom(h(0), h(1))))
        ct       = respHeaders.get(Header.ContentType)
        clHeader = respHeaders.get(Header.ContentLength)
        cl       = clHeader.orElse(Some(Header.ContentLength(bytes.length.toLong)))
      } yield Response(
        status = Status.fromInt(response.status),
        headers = respHeaders,
        body = FetchBodyBatched(bytes, ct.map(Body.ContentType.fromHeader), cl),
      )
    }

  override def socket[Env1 <: Any](version: Version, url: URL, headers: Headers, app: WebSocketApp[Env1])(implicit
    trace: Trace,
    ev: Any =:= Scope,
  ): ZIO[Env1 & Any, Throwable, Response] =
    ZIO.die(new UnsupportedOperationException("Streaming is disabled"))
}
