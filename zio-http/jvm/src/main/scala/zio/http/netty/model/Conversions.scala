/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty.model

import scala.collection.AbstractIterator

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Server.Config.CompressionOptions
import zio.http._

import io.netty.handler.codec.compression.{DeflateOptions, StandardCompressionOptions}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketScheme

private[netty] object Conversions {
  def methodFromNetty(method: HttpMethod): Method =
    method match {
      case HttpMethod.OPTIONS => Method.OPTIONS
      case HttpMethod.GET     => Method.GET
      case HttpMethod.HEAD    => Method.HEAD
      case HttpMethod.POST    => Method.POST
      case HttpMethod.PUT     => Method.PUT
      case HttpMethod.PATCH   => Method.PATCH
      case HttpMethod.DELETE  => Method.DELETE
      case HttpMethod.TRACE   => Method.TRACE
      case HttpMethod.CONNECT => Method.CONNECT
      case method             => Method.CUSTOM(method.name())
    }

  def methodToNetty(method: Method): HttpMethod =
    method match {
      case Method.OPTIONS      => HttpMethod.OPTIONS
      case Method.GET          => HttpMethod.GET
      case Method.HEAD         => HttpMethod.HEAD
      case Method.POST         => HttpMethod.POST
      case Method.PUT          => HttpMethod.PUT
      case Method.PATCH        => HttpMethod.PATCH
      case Method.DELETE       => HttpMethod.DELETE
      case Method.TRACE        => HttpMethod.TRACE
      case Method.CONNECT      => HttpMethod.CONNECT
      case Method.ANY          => HttpMethod.GET
      case Method.CUSTOM(name) => new HttpMethod(name)
    }

  def headersToNetty(headers: Headers): HttpHeaders =
    headers match {
      case Headers.FromIterable(_)     => encodeHeaderListToNetty(headers)
      case Headers.Native(value, _, _) => value.asInstanceOf[HttpHeaders]
      case Headers.Concat(_, _)        => encodeHeaderListToNetty(headers)
      case Headers.Empty               => new DefaultHttpHeaders()
    }

  private def nettyHeadersIterator(headers: HttpHeaders): Iterator[Header] =
    new AbstractIterator[Header] {
      private val nettyIterator = headers.iteratorCharSequence()

      override def hasNext: Boolean = nettyIterator.hasNext

      override def next(): Header = {
        val entry = nettyIterator.next()
        Header.Custom(entry.getKey, entry.getValue)
      }
    }

  def headersFromNetty(headers: HttpHeaders): Headers =
    Headers.Native(
      headers,
      (headers: HttpHeaders) => nettyHeadersIterator(headers),
      // NOTE: Netty's headers.get is case-insensitive
      (headers: HttpHeaders, key: CharSequence) => headers.get(key),
    )

  private def encodeHeaderListToNetty(headers: Iterable[Header]): HttpHeaders = {
    val nettyHeaders  = new DefaultHttpHeaders(true)
    val setCookieName = Header.SetCookie.name
    val iter          = headers.iterator
    while (iter.hasNext) {
      val header = iter.next()
      val name   = header.headerName
      if (name == setCookieName) {
        nettyHeaders.add(name, header.renderedValueAsCharSequence)
      } else {
        nettyHeaders.set(name, header.renderedValueAsCharSequence)
      }
    }
    nettyHeaders
  }

  def statusToNetty(status: Status): HttpResponseStatus =
    HttpResponseStatus.valueOf(status.code)

  def statusFromNetty(status: HttpResponseStatus): Status =
    Status.fromInt(status.code)

  def schemeToNetty(scheme: Scheme): Option[HttpScheme] = scheme match {
    case Scheme.HTTP  => Option(HttpScheme.HTTP)
    case Scheme.HTTPS => Option(HttpScheme.HTTPS)
    case _            => None
  }

  def schemeToNettyWebSocketScheme(scheme: Scheme): Option[WebSocketScheme] = scheme match {
    case Scheme.WS  => Option(WebSocketScheme.WS)
    case Scheme.WSS => Option(WebSocketScheme.WSS)
    case _          => None
  }

  def schemeFromNetty(scheme: HttpScheme): Option[Scheme] = scheme match {
    case HttpScheme.HTTPS => Option(Scheme.HTTPS)
    case HttpScheme.HTTP  => Option(Scheme.HTTP)
    case _                => None
  }

  def schemeFromNetty(scheme: WebSocketScheme): Option[Scheme] = scheme match {
    case WebSocketScheme.WSS => Option(Scheme.WSS)
    case WebSocketScheme.WS  => Option(Scheme.WS)
    case _                   => None
  }

  def compressionOptionsToNetty(compressionOptions: CompressionOptions): DeflateOptions =
    compressionOptions.kind match {
      case CompressionOptions.CompressionType.GZip    =>
        StandardCompressionOptions.gzip(compressionOptions.level, compressionOptions.bits, compressionOptions.mem)
      case CompressionOptions.CompressionType.Deflate =>
        StandardCompressionOptions.deflate(compressionOptions.level, compressionOptions.bits, compressionOptions.mem)
    }

  def versionToNetty(version: Version): HttpVersion = version match {
    case Version.Http_1_0 => HttpVersion.HTTP_1_0
    case Version.Http_1_1 => HttpVersion.HTTP_1_1
    case Version.Default  => HttpVersion.HTTP_1_1
  }
}
