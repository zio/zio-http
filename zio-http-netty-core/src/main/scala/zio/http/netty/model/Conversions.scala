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

import zio.http._

import io.netty.handler.codec.compression.{BrotliMode, StandardCompressionOptions}
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
      case method             => Method.fromString(method.name()).getOrElse(Method.GET)
    }

  def methodToNetty(method: Method): HttpMethod =
    method match {
      case Method.OPTIONS => HttpMethod.OPTIONS
      case Method.GET     => HttpMethod.GET
      case Method.HEAD    => HttpMethod.HEAD
      case Method.POST    => HttpMethod.POST
      case Method.PUT     => HttpMethod.PUT
      case Method.PATCH   => HttpMethod.PATCH
      case Method.DELETE  => HttpMethod.DELETE
      case Method.TRACE   => HttpMethod.TRACE
      case Method.CONNECT => HttpMethod.CONNECT
      case Method.ANY     => HttpMethod.GET
      case other          => new HttpMethod(other.name)
    }

  def headersToNetty(headers: Headers): HttpHeaders =
    if (headers.isEmpty) new DefaultHttpHeaders()
    else encodeHeaderListToNetty(headers)

  def urlToNetty(url: URL): String = {
    // As per the spec, the path should contain only the relative path.
    // Host and port information should be in the headers.
    val url0 = if (url.path.isEmpty) url.addLeadingSlash else url
    url0.relative.addLeadingSlash.encode
  }

  def headersFromNetty(headers: HttpHeaders): Headers = {
    val builder = HeadersBuilder.make(headers.size())
    val iter    = headers.iteratorCharSequence()
    while (iter.hasNext) {
      val entry = iter.next()
      builder.add(entry.getKey.toString, entry.getValue.toString)
    }
    builder.build()
  }

  private val multiValueHeaders: java.util.HashSet[String] = {
    val set = new java.util.HashSet[String](8)
    set.add("set-cookie")
    set.add("www-authenticate")
    set.add("proxy-authenticate")
    set.add("via")
    set
  }

  private def encodeHeaderListToNetty(headers: Headers): HttpHeaders = {
    val nettyHeaders = new DefaultHttpHeaders()
    val entries      = headers.toList
    val iter         = entries.iterator
    while (iter.hasNext) {
      val (name, value) = iter.next()
      if (multiValueHeaders.contains(name)) {
        nettyHeaders.add(name, value)
      } else {
        nettyHeaders.set(name, value)
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

  def compressionOptionsToNetty(
    compressionOptions: CompressionOptions,
  ): io.netty.handler.codec.compression.CompressionOptions =
    compressionOptions match {
      case CompressionOptions.GZip(cfg)    =>
        StandardCompressionOptions.gzip(cfg.level, cfg.bits, cfg.mem)
      case CompressionOptions.Deflate(cfg) =>
        StandardCompressionOptions.deflate(cfg.level, cfg.bits, cfg.mem)
      case CompressionOptions.Brotli(cfg)  =>
        StandardCompressionOptions.brotli(cfg.quality, cfg.lgwin, brotliModeToJava(cfg.mode))
    }

  def brotliModeToJava(brotli: CompressionOptions.Mode): BrotliMode = brotli match {
    case CompressionOptions.Mode.Font    => BrotliMode.FONT
    case CompressionOptions.Mode.Text    => BrotliMode.TEXT
    case CompressionOptions.Mode.Generic => BrotliMode.GENERIC
  }

  def versionToNetty(version: Version): HttpVersion = version match {
    case Version.`HTTP/1.0` => HttpVersion.HTTP_1_0
    case Version.`HTTP/1.1` => HttpVersion.HTTP_1_1
    case _                  => HttpVersion.HTTP_1_1
  }
}
