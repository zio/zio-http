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

import zio.http.Server.Config.CompressionOptions
import zio.http._
import zio.http.internal.{CaseMode, CharSequenceExtensions}

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
      case Method.Default      => HttpMethod.GET
      case Method.CUSTOM(name) => new HttpMethod(name)
    }

  def headersToNetty(headers: Headers): HttpHeaders =
    headers match {
      case Headers.FromIterable(_)     => encodeHeaderListToNetty(headers.toList)
      case Headers.Native(value, _, _) => value.asInstanceOf[HttpHeaders]
      case Headers.Concat(_, _)        => encodeHeaderListToNetty(headers.toList)
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
      (headers: HttpHeaders, key: CharSequence) => {
        val iterator       = headers.iteratorCharSequence()
        var result: String = null
        while (iterator.hasNext && (result eq null)) {
          val entry = iterator.next()
          if (CharSequenceExtensions.equals(entry.getKey, key, CaseMode.Insensitive)) {
            result = entry.getValue.toString
          }
        }

        result
      },
    )

  private def encodeHeaderListToNetty(headers: Iterable[Header]): HttpHeaders = {
    val nettyHeaders = new DefaultHttpHeaders(true)
    for (header <- headers) {
      if (header.headerName == Header.SetCookie.name) {
        nettyHeaders.add(header.headerName, header.renderedValueAsCharSequence)
      } else {
        nettyHeaders.set(header.headerName, header.renderedValueAsCharSequence)
      }
    }
    nettyHeaders
  }

  def statusToNetty(status: Status): HttpResponseStatus =
    status match {
      case Status.Continue                      => HttpResponseStatus.CONTINUE                        // 100
      case Status.SwitchingProtocols            => HttpResponseStatus.SWITCHING_PROTOCOLS             // 101
      case Status.Processing                    => HttpResponseStatus.PROCESSING                      // 102
      case Status.Ok                            => HttpResponseStatus.OK                              // 200
      case Status.Created                       => HttpResponseStatus.CREATED                         // 201
      case Status.Accepted                      => HttpResponseStatus.ACCEPTED                        // 202
      case Status.NonAuthoritativeInformation   => HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   // 203
      case Status.NoContent                     => HttpResponseStatus.NO_CONTENT                      // 204
      case Status.ResetContent                  => HttpResponseStatus.RESET_CONTENT                   // 205
      case Status.PartialContent                => HttpResponseStatus.PARTIAL_CONTENT                 // 206
      case Status.MultiStatus                   => HttpResponseStatus.MULTI_STATUS                    // 207
      case Status.MultipleChoices               => HttpResponseStatus.MULTIPLE_CHOICES                // 300
      case Status.MovedPermanently              => HttpResponseStatus.MOVED_PERMANENTLY               // 301
      case Status.Found                         => HttpResponseStatus.FOUND                           // 302
      case Status.SeeOther                      => HttpResponseStatus.SEE_OTHER                       // 303
      case Status.NotModified                   => HttpResponseStatus.NOT_MODIFIED                    // 304
      case Status.UseProxy                      => HttpResponseStatus.USE_PROXY                       // 305
      case Status.TemporaryRedirect             => HttpResponseStatus.TEMPORARY_REDIRECT              // 307
      case Status.PermanentRedirect             => HttpResponseStatus.PERMANENT_REDIRECT              // 308
      case Status.BadRequest                    => HttpResponseStatus.BAD_REQUEST                     // 400
      case Status.Unauthorized                  => HttpResponseStatus.UNAUTHORIZED                    // 401
      case Status.PaymentRequired               => HttpResponseStatus.PAYMENT_REQUIRED                // 402
      case Status.Forbidden                     => HttpResponseStatus.FORBIDDEN                       // 403
      case Status.NotFound                      => HttpResponseStatus.NOT_FOUND                       // 404
      case Status.MethodNotAllowed              => HttpResponseStatus.METHOD_NOT_ALLOWED              // 405
      case Status.NotAcceptable                 => HttpResponseStatus.NOT_ACCEPTABLE                  // 406
      case Status.ProxyAuthenticationRequired   => HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   // 407
      case Status.RequestTimeout                => HttpResponseStatus.REQUEST_TIMEOUT                 // 408
      case Status.Conflict                      => HttpResponseStatus.CONFLICT                        // 409
      case Status.Gone                          => HttpResponseStatus.GONE                            // 410
      case Status.LengthRequired                => HttpResponseStatus.LENGTH_REQUIRED                 // 411
      case Status.PreconditionFailed            => HttpResponseStatus.PRECONDITION_FAILED             // 412
      case Status.RequestEntityTooLarge         => HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        // 413
      case Status.RequestUriTooLong             => HttpResponseStatus.REQUEST_URI_TOO_LONG            // 414
      case Status.UnsupportedMediaType          => HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          // 415
      case Status.RequestedRangeNotSatisfiable  => HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE // 416
      case Status.ExpectationFailed             => HttpResponseStatus.EXPECTATION_FAILED              // 417
      case Status.MisdirectedRequest            => HttpResponseStatus.MISDIRECTED_REQUEST             // 421
      case Status.UnprocessableEntity           => HttpResponseStatus.UNPROCESSABLE_ENTITY            // 422
      case Status.Locked                        => HttpResponseStatus.LOCKED                          // 423
      case Status.FailedDependency              => HttpResponseStatus.FAILED_DEPENDENCY               // 424
      case Status.UnorderedCollection           => HttpResponseStatus.UNORDERED_COLLECTION            // 425
      case Status.UpgradeRequired               => HttpResponseStatus.UPGRADE_REQUIRED                // 426
      case Status.PreconditionRequired          => HttpResponseStatus.PRECONDITION_REQUIRED           // 428
      case Status.TooManyRequests               => HttpResponseStatus.TOO_MANY_REQUESTS               // 429
      case Status.RequestHeaderFieldsTooLarge   => HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE // 431
      case Status.InternalServerError           => HttpResponseStatus.INTERNAL_SERVER_ERROR           // 500
      case Status.NotImplemented                => HttpResponseStatus.NOT_IMPLEMENTED                 // 501
      case Status.BadGateway                    => HttpResponseStatus.BAD_GATEWAY                     // 502
      case Status.ServiceUnavailable            => HttpResponseStatus.SERVICE_UNAVAILABLE             // 503
      case Status.GatewayTimeout                => HttpResponseStatus.GATEWAY_TIMEOUT                 // 504
      case Status.HttpVersionNotSupported       => HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      // 505
      case Status.VariantAlsoNegotiates         => HttpResponseStatus.VARIANT_ALSO_NEGOTIATES         // 506
      case Status.InsufficientStorage           => HttpResponseStatus.INSUFFICIENT_STORAGE            // 507
      case Status.NotExtended                   => HttpResponseStatus.NOT_EXTENDED                    // 510
      case Status.NetworkAuthenticationRequired => HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED // 511
      case Status.Custom(code)                  => HttpResponseStatus.valueOf(code)
    }

  def statusFromNetty(status: HttpResponseStatus): Status = (status: @unchecked) match {
    case HttpResponseStatus.CONTINUE                        => Status.Continue
    case HttpResponseStatus.SWITCHING_PROTOCOLS             => Status.SwitchingProtocols
    case HttpResponseStatus.PROCESSING                      => Status.Processing
    case HttpResponseStatus.OK                              => Status.Ok
    case HttpResponseStatus.CREATED                         => Status.Created
    case HttpResponseStatus.ACCEPTED                        => Status.Accepted
    case HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   => Status.NonAuthoritativeInformation
    case HttpResponseStatus.NO_CONTENT                      => Status.NoContent
    case HttpResponseStatus.RESET_CONTENT                   => Status.ResetContent
    case HttpResponseStatus.PARTIAL_CONTENT                 => Status.PartialContent
    case HttpResponseStatus.MULTI_STATUS                    => Status.MultiStatus
    case HttpResponseStatus.MULTIPLE_CHOICES                => Status.MultipleChoices
    case HttpResponseStatus.MOVED_PERMANENTLY               => Status.MovedPermanently
    case HttpResponseStatus.FOUND                           => Status.Found
    case HttpResponseStatus.SEE_OTHER                       => Status.SeeOther
    case HttpResponseStatus.NOT_MODIFIED                    => Status.NotModified
    case HttpResponseStatus.USE_PROXY                       => Status.UseProxy
    case HttpResponseStatus.TEMPORARY_REDIRECT              => Status.TemporaryRedirect
    case HttpResponseStatus.PERMANENT_REDIRECT              => Status.PermanentRedirect
    case HttpResponseStatus.BAD_REQUEST                     => Status.BadRequest
    case HttpResponseStatus.UNAUTHORIZED                    => Status.Unauthorized
    case HttpResponseStatus.PAYMENT_REQUIRED                => Status.PaymentRequired
    case HttpResponseStatus.FORBIDDEN                       => Status.Forbidden
    case HttpResponseStatus.NOT_FOUND                       => Status.NotFound
    case HttpResponseStatus.METHOD_NOT_ALLOWED              => Status.MethodNotAllowed
    case HttpResponseStatus.NOT_ACCEPTABLE                  => Status.NotAcceptable
    case HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   => Status.ProxyAuthenticationRequired
    case HttpResponseStatus.REQUEST_TIMEOUT                 => Status.RequestTimeout
    case HttpResponseStatus.CONFLICT                        => Status.Conflict
    case HttpResponseStatus.GONE                            => Status.Gone
    case HttpResponseStatus.LENGTH_REQUIRED                 => Status.LengthRequired
    case HttpResponseStatus.PRECONDITION_FAILED             => Status.PreconditionFailed
    case HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        => Status.RequestEntityTooLarge
    case HttpResponseStatus.REQUEST_URI_TOO_LONG            => Status.RequestUriTooLong
    case HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          => Status.UnsupportedMediaType
    case HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE => Status.RequestedRangeNotSatisfiable
    case HttpResponseStatus.EXPECTATION_FAILED              => Status.ExpectationFailed
    case HttpResponseStatus.MISDIRECTED_REQUEST             => Status.MisdirectedRequest
    case HttpResponseStatus.UNPROCESSABLE_ENTITY            => Status.UnprocessableEntity
    case HttpResponseStatus.LOCKED                          => Status.Locked
    case HttpResponseStatus.FAILED_DEPENDENCY               => Status.FailedDependency
    case HttpResponseStatus.UNORDERED_COLLECTION            => Status.UnorderedCollection
    case HttpResponseStatus.UPGRADE_REQUIRED                => Status.UpgradeRequired
    case HttpResponseStatus.PRECONDITION_REQUIRED           => Status.PreconditionRequired
    case HttpResponseStatus.TOO_MANY_REQUESTS               => Status.TooManyRequests
    case HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE => Status.RequestHeaderFieldsTooLarge
    case HttpResponseStatus.INTERNAL_SERVER_ERROR           => Status.InternalServerError
    case HttpResponseStatus.NOT_IMPLEMENTED                 => Status.NotImplemented
    case HttpResponseStatus.BAD_GATEWAY                     => Status.BadGateway
    case HttpResponseStatus.SERVICE_UNAVAILABLE             => Status.ServiceUnavailable
    case HttpResponseStatus.GATEWAY_TIMEOUT                 => Status.GatewayTimeout
    case HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      => Status.HttpVersionNotSupported
    case HttpResponseStatus.VARIANT_ALSO_NEGOTIATES         => Status.VariantAlsoNegotiates
    case HttpResponseStatus.INSUFFICIENT_STORAGE            => Status.InsufficientStorage
    case HttpResponseStatus.NOT_EXTENDED                    => Status.NotExtended
    case HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED => Status.NetworkAuthenticationRequired
    case status: HttpResponseStatus                         => Status.Custom(status.code)
  }

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
