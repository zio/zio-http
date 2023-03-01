package zio.http.netty.model

import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders, HttpMethod}
import zio.http.model.{Header, HeaderNames, Headers, Method}

import scala.jdk.CollectionConverters.CollectionHasAsScala

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
      case Method.CUSTOM(name) => new HttpMethod(name)
    }

  def headersToNetty(headers: Headers): HttpHeaders =
    headers match {
      case Headers.Header(_, _)        => encodeHeaderListToNetty(headers.toList)
      case Headers.FromIterable(_)     => encodeHeaderListToNetty(headers.toList)
      case Headers.Native(value, _, _) => value.asInstanceOf[HttpHeaders]
      case Headers.Concat(_, _)        => encodeHeaderListToNetty(headers.toList)
      case Headers.EmptyHeaders        => new DefaultHttpHeaders()
    }

  def headersFromNetty(headers: HttpHeaders): Headers =
    Headers.Native(
      headers,
      (headers: HttpHeaders) => headers.entries().asScala.map(e => Header(e.getKey, e.getValue)).iterator,
      (headers: HttpHeaders, key: String) => {
        val iterator       = headers.iteratorCharSequence()
        var result: String = null
        while (iterator.hasNext && (result eq null)) {
          val entry = iterator.next()
          if (entry.getKey.toString == key) {
            result = entry.getValue.toString
          }
        }

        result
      },
    )

  private def encodeHeaderListToNetty(headersList: List[Product2[CharSequence, CharSequence]]): HttpHeaders = {
    val (exceptions, regularHeaders) =
      headersList.partition(h => h._1.toString.contains(HeaderNames.setCookie.toString))
    val combinedHeaders              = regularHeaders
      .groupBy(_._1)
      .map { case (key, tuples) =>
        key -> tuples.map(_._2).mkString(",")
      }
    (exceptions ++ combinedHeaders)
      .foldLeft[HttpHeaders](new DefaultHttpHeaders(true)) { case (headers, entry) =>
        headers.add(entry._1, entry._2)
      }
  }
}
