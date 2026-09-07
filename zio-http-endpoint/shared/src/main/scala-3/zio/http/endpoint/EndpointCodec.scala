/*
 * Copyright 2026 the ZIO HTTP contributors.
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
package zio.http.endpoint

import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Tuples
import zio.blocks.endpoint.{Alternator, CodecKind, HttpCodec}
import zio.blocks.mediatype.MediaType
import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import zio.http.{Body, ContentType, Request, Response, Status}

/**
 * Internal bridge between a zio-blocks [[HttpCodec]] description and the
 * concrete `zio.http` wire types ([[Request]] / [[Response]] / [[Body]]).
 *
 * zio-blocks' `HttpCodec` is a pure description (schema + declared media
 * types); it carries no HTTP encode/decode of its own. This object supplies
 * that, driven by each node's [[Schema]] and negotiated against the codec's
 * declared `mediaTypes`.
 *
 * The request side mirrors [[EndpointCodecWalker]]: `Combine` splits through
 * its [[Tuples]] combiner, `Fallback` dispatches through its [[Alternator]]
 * (left first, right on left-failure, so `Fallback(query, Empty)` models an
 * absent optional), `Query` / `Header` parse their wire string back through
 * the node's own schema, and `Body` decodes through its schema's JSON codec.
 * The response side additionally renders `Header` nodes onto the real
 * [[Response]] headers instead of dropping them.
 *
 * Bodies always attempt a JSON decode, whatever the codec's declared media
 * types say: a declared `text/csv` body carrying JSON still decodes, and
 * garbage fails with an error naming both the JSON attempt and the declared
 * media type rather than succeeding silently with an empty value.
 */
private[endpoint] object EndpointCodec {

  /** JSON content type used when a codec declares no usable media type. */
  private val jsonContentType: ContentType = ContentType.`application/json`

  /**
   * Decodes a request into the value described by a request-side codec.
   * Returns `Left(message)` when the bytes do not conform to the schema.
   */
  def decodeRequest[A](codec: HttpCodec[CodecKind.Request, A], request: Request): Either[String, A] =
    decodeRequestValue(codec, request)

  def decodeHeader[A](header: HttpCodec.Header[CodecKind.Request, A], request: Request): Either[String, A] =
    parseLeaf("header", header.name, header.schema, request.headers.rawGet(header.name))

  /**
   * Encodes a response-side value into an HTTP [[Response]] with a 200
   * status. Shared-syntax seam for cross-toolchain tests (Scala 3 takes a
   * [[Status]], Scala 2.13 takes an `Int`).
   */
  def encodeResponse[A](codec: HttpCodec[CodecKind.Response, A], value: A): Response =
    encodeResponse(codec, value, Status.Ok)

  /**
   * Encodes a response-side value into an HTTP [[Response]], choosing the wire
   * media type from the codec's declared `mediaTypes` (falling back to JSON).
   *
   * Throws [[java.lang.IllegalArgumentException]] naming the offending node
   * when the value cannot be encoded (for example a codec tree containing more
   * than one body) — headers and bodies are never silently dropped.
   */
  def encodeResponse[A](codec: HttpCodec[CodecKind.Response, A], value: A, status: Status): Response =
    encodeResponseValue(codec, value) match {
      case Right(parts) =>
        parts.headers.foldLeft(Response(status, body = parts.body.getOrElse(Body.empty))) {
          case (response, (name, rendered)) => response.addHeader(name, rendered)
        }
      case Left(message) => throw new IllegalArgumentException(message)
    }

  /**
   * Encodes a client-side input value into a request [[Body]], negotiating the
   * content type from the codec's declared media types (falling back to JSON).
   */
  def encodeRequestBody[A](codec: HttpCodec[CodecKind.Request, A], value: A): Body =
    codec match {
      case HttpCodec.Empty                                    => Body.empty
      case b: HttpCodec.Body[CodecKind.Request, A] @unchecked =>
        encodeBody(b.schema, b.mediaTypes, value)
      case other                                              =>
        encodeBodyFromSchemaRequest(other, value)
    }

  /**
   * Decodes a response into the value described by a response-side codec.
   * Returns `Left(message)` when the bytes do not conform to the schema.
   */
  def decodeResponse[A](codec: HttpCodec[CodecKind.Response, A], response: Response): Either[String, A] =
    decodeResponseValue(codec, response)

  private final case class ResponseParts(
    headers: Map[String, String],
    body: Option[Body],
  )

  private val emptyParts: ResponseParts =
    ResponseParts(Map.empty[String, String], None)

  private def decodeRequestValue[A](
    codec: HttpCodec[CodecKind.Request, A],
    request: Request,
  ): Either[String, A] =
    // The match is exhaustive for request-side codecs (`StatusCodec` is
    // response-only); `@unchecked` keeps both toolchains' exhaustivity
    // checkers quiet (Scala 3 proves it, Scala 2.13 cannot), with `null`
    // handled explicitly above the match.
    if (codec == null) Left("Cannot decode request: codec is null")
    else
      (codec: HttpCodec[CodecKind.Request, A] @unchecked) match {
        case HttpCodec.Empty                                       => Right(().asInstanceOf[A])
        case body: HttpCodec.Body[CodecKind.Request, A] @unchecked =>
          decodeBody(body.schema, body.mediaTypes, request.body)
        case header: HttpCodec.Header[CodecKind.Request, A] @unchecked =>
          parseLeaf("header", header.name, header.schema, request.headers.rawGet(header.name))
        case query: HttpCodec.Query[A] @unchecked =>
          parseLeaf("query", query.name, query.schema, request.url.queryParams.getFirst(query.name))
        case combine: HttpCodec.Combine[CodecKind.Request, a, b, c] @unchecked =>
          decodeRequestCombine(combine.left, combine.right, combine.combiner, request)
        case fallback: HttpCodec.Fallback[CodecKind.Request, a, b, c] @unchecked =>
          decodeRequestFallback(fallback.left, fallback.right, fallback.alternator, request)
      }

  private def decodeRequestCombine[A, B, C](
    left: HttpCodec[CodecKind.Request, A],
    right: HttpCodec[CodecKind.Request, B],
    combiner: Tuples.Tuples.WithOut[A, B, C],
    request: Request,
  ): Either[String, C] =
    for {
      first  <- decodeRequestValue(left, request)
      second <- decodeRequestValue(right, request)
    } yield combiner.combine(first, second)

  private def decodeRequestFallback[A, B, C](
    left: HttpCodec[CodecKind.Request, A],
    right: HttpCodec[CodecKind.Request, B],
    alternator: Alternator.WithOut[A, B, C],
    request: Request,
  ): Either[String, C] =
    decodeRequestValue(left, request) match {
      case Right(first) => Right(alternator.combine(Left(first): Either[A, B]))
      case Left(firstError) =>
        decodeRequestValue(right, request) match {
          case Right(second) => Right(alternator.combine(Right(second): Either[A, B]))
          case Left(secondError) =>
            Left(s"Cannot decode request: neither alternative matched ($firstError; $secondError)")
        }
    }

  private def decodeResponseValue[A](
    codec: HttpCodec[CodecKind.Response, A],
    response: Response,
  ): Either[String, A] =
    if (codec == null) Left("Cannot decode response: codec is null")
    else
      (codec: HttpCodec[CodecKind.Response, A] @unchecked) match {
        case HttpCodec.Empty                                        => Right(().asInstanceOf[A])
        case body: HttpCodec.Body[CodecKind.Response, A] @unchecked =>
          decodeBody(body.schema, body.mediaTypes, response.body)
        case header: HttpCodec.Header[CodecKind.Response, A] @unchecked =>
          parseLeaf("header", header.name, header.schema, response.headers.rawGet(header.name))
        case _: HttpCodec.StatusCodec =>
          Right(().asInstanceOf[A])
        case combine: HttpCodec.Combine[CodecKind.Response, a, b, c] @unchecked =>
          decodeResponseCombine(combine.left, combine.right, combine.combiner, response)
        case fallback: HttpCodec.Fallback[CodecKind.Response, a, b, c] @unchecked =>
          decodeResponseFallback(fallback.left, fallback.right, fallback.alternator, response)
      }

  private def decodeResponseCombine[A, B, C](
    left: HttpCodec[CodecKind.Response, A],
    right: HttpCodec[CodecKind.Response, B],
    combiner: Tuples.Tuples.WithOut[A, B, C],
    response: Response,
  ): Either[String, C] =
    for {
      first  <- decodeResponseValue(left, response)
      second <- decodeResponseValue(right, response)
    } yield combiner.combine(first, second)

  private def decodeResponseFallback[A, B, C](
    left: HttpCodec[CodecKind.Response, A],
    right: HttpCodec[CodecKind.Response, B],
    alternator: Alternator.WithOut[A, B, C],
    response: Response,
  ): Either[String, C] =
    decodeResponseValue(left, response) match {
      case Right(first) => Right(alternator.combine(Left(first): Either[A, B]))
      case Left(firstError) =>
        decodeResponseValue(right, response) match {
          case Right(second) => Right(alternator.combine(Right(second): Either[A, B]))
          case Left(secondError) =>
            Left(s"Cannot decode response: neither alternative matched ($firstError; $secondError)")
        }
    }

  private def encodeResponseValue[A](
    codec: HttpCodec[CodecKind.Response, A],
    value: A,
  ): Either[String, ResponseParts] =
    if (codec == null) Left("Cannot encode response: codec is null")
    else
      (codec: HttpCodec[CodecKind.Response, A] @unchecked) match {
        case HttpCodec.Empty => Right(emptyParts)
        case body: HttpCodec.Body[CodecKind.Response, A] @unchecked =>
          encodeResponseBody(body.schema, body.mediaTypes, value).map(encoded =>
            emptyParts.copy(body = Some(encoded)),
          )
        case header: HttpCodec.Header[CodecKind.Response, A] @unchecked =>
          renderResponseHeader(header.name, header.schema, value).map {
            case Some(rendered) => emptyParts.copy(headers = Map(header.name -> rendered))
            case None           => emptyParts
          }
        case _: HttpCodec.StatusCodec =>
          Right(emptyParts)
        case combine: HttpCodec.Combine[CodecKind.Response, a, b, c] @unchecked =>
          encodeResponseCombine(combine.left, combine.right, combine.combiner, value)
        case fallback: HttpCodec.Fallback[CodecKind.Response, a, b, c] @unchecked =>
          encodeResponseFallback(fallback.left, fallback.right, fallback.alternator, value)
      }

  private def encodeResponseCombine[A, B, C](
    left: HttpCodec[CodecKind.Response, A],
    right: HttpCodec[CodecKind.Response, B],
    combiner: Tuples.Tuples.WithOut[A, B, C],
    value: C,
  ): Either[String, ResponseParts] =
    for {
      pair   <- splitResponseTuple(combiner, value)
      first  <- encodeResponseValue(left, pair._1)
      second <- encodeResponseValue(right, pair._2)
      merged <- first.merge(second)
    } yield merged

  private def splitResponseTuple[A, B, C](
    combiner: Tuples.Tuples.WithOut[A, B, C],
    value: C,
  ): Either[String, (A, B)] =
    try Right(combiner.separate(value))
    catch { case scala.util.control.NonFatal(e) => Left(s"Cannot split combined response: ${e.getMessage}") }

  private def encodeResponseFallback[A, B, C](
    left: HttpCodec[CodecKind.Response, A],
    right: HttpCodec[CodecKind.Response, B],
    alternator: Alternator.WithOut[A, B, C],
    value: C,
  ): Either[String, ResponseParts] =
    try alternator.separate(value) match {
      case Left(first)   => encodeResponseValue(left, first).left.map(message =>
          s"Fallback (first alternative) failed: $message",
        )
      case Right(second) => encodeResponseValue(right, second).left.map(message =>
          s"Fallback (second alternative) failed: $message",
        )
    } catch {
      case scala.util.control.NonFatal(e) => Left(s"Fallback dispatch failed: ${e.getMessage}")
    }

  /**
   * Parses one query/header wire string back through the node's own schema.
   * A missing entry decodes through `Null` first so optional (`Option`)
   * schemas read as absent; anything else is a missing-param error naming the
   * entry.
   */
  private def parseLeaf[A](
    kind: String,
    name: String,
    schema: Schema[A],
    raw: Option[String],
  ): Either[String, A] =
    raw match {
      case Some(rendered) => parseRenderedLeaf(kind, name, schema, rendered)
      case None           =>
        try schema.fromDynamicValue(DynamicValue.Null) match {
          case Right(absent) => Right(absent)
          case Left(_)       => Left(s"Missing $kind '$name'")
        } catch {
          case scala.util.control.NonFatal(_) => Left(s"Missing $kind '$name'")
        }
    }

  /**
   * Reverse of the walker's `renderLeaf`: the wire holds plain strings, but
   * primitive schemas expect their own [[PrimitiveValue]] shape, so each
   * plausible shape is attempted in turn (plain string first, then boolean /
   * numeric parses). Mirrors exactly what `renderLeaf` can produce.
   */
  private def parseRenderedLeaf[A](
    kind: String,
    name: String,
    schema: Schema[A],
    rendered: String,
  ): Either[String, A] = {
    val candidates = renderedCandidates(rendered)
    var index      = 0
    while (index < candidates.length) {
      try schema.fromDynamicValue(candidates(index)) match {
        case Right(parsed) => return Right(parsed)
        case Left(_)       => ()
      } catch {
        case scala.util.control.NonFatal(_) => ()
      }
      index += 1
    }
    Left(s"Cannot decode $kind '$name' from '$rendered'")
  }

  private def renderedCandidates(rendered: String): List[DynamicValue] = {
    val builder = List.newBuilder[DynamicValue]
    builder += DynamicValue.string(rendered)
    if (rendered == "true") builder += DynamicValue.Primitive(PrimitiveValue.Boolean(true))
    else if (rendered == "false") builder += DynamicValue.Primitive(PrimitiveValue.Boolean(false))
    try builder += DynamicValue.Primitive(PrimitiveValue.Int(rendered.toInt))
    catch { case _: NumberFormatException => () }
    try builder += DynamicValue.Primitive(PrimitiveValue.Long(rendered.toLong))
    catch { case _: NumberFormatException => () }
    try builder += DynamicValue.Primitive(PrimitiveValue.Double(rendered.toDouble))
    catch { case _: NumberFormatException => () }
    builder.result()
  }

  /**
   * Renders one response header to its wire string through the node's own
   * schema, mirroring the walker's request-side `renderLeaf`: primitives
   * render as their plain value, `None`-shaped values (dynamic `Null`) render
   * as absent so the header is omitted, anything else is a descriptive error.
   */
  private def renderResponseHeader[A](
    name: String,
    schema: Schema[A],
    value: A,
  ): Either[String, Option[String]] =
    try schema.toDynamicValue(value) match {
      case DynamicValue.Primitive(PrimitiveValue.String(rendered)) =>
        Right(Some(rendered))
      case DynamicValue.Primitive(single: Product) if single.productArity == 1 =>
        Right(Some(String.valueOf(single.productElement(0))))
      case DynamicValue.Primitive(other) =>
        Left(s"Cannot render response header '$name': unsupported primitive ${other.getClass.getSimpleName}")
      case DynamicValue.Null =>
        Right(None)
      case other =>
        Left(
          s"Cannot render response header '$name': expected a primitive value but found " +
            s"${other.getClass.getSimpleName}",
        )
    } catch {
      case scala.util.control.NonFatal(e) => Left(s"Cannot render response header '$name': ${e.getMessage}")
    }

  private def encodeResponseBody[A](
    schema: Schema[A],
    mediaTypes: Chunk[MediaType],
    value: A,
  ): Either[String, Body] =
    try {
      val bytes = schema.jsonCodec.encode(value)
      Right(Body.fromArray(bytes, contentTypeFor(mediaTypes)))
    } catch {
      case scala.util.control.NonFatal(e) => Left(s"Cannot encode response body: ${e.getMessage}")
    }

  private def encodeBodyFromSchemaRequest[A](codec: HttpCodec[CodecKind.Request, A], value: A): Body =
    schemaOf(codec) match {
      case Some(schema) => Body.fromArray(schema.jsonCodec.encode(value), jsonContentType)
      case None         => Body.empty
    }

  /**
   * Decodes a body through its schema's JSON codec whatever the codec's
   * declared media types say (JSON-attempt fallback): a declared `text/csv`
   * body carrying JSON still decodes, and anything else fails with an error
   * naming both the JSON attempt and the declared media type — never a silent
   * empty value.
   */
  private def decodeBody[A](schema: Schema[A], mediaTypes: Chunk[MediaType], body: Body): Either[String, A] =
    schema.jsonCodec.decode(body.toArray) match {
      case Right(parsed) => Right(parsed)
      case Left(error)   =>
        Left(
          s"Cannot decode body as JSON (declared media type: ${describeMediaTypes(mediaTypes)}): " +
            s"${error.getMessage}",
        )
    }

  private def describeMediaTypes(mediaTypes: Chunk[MediaType]): String =
    if (mediaTypes.isEmpty) jsonContentType.mediaType.fullType
    else mediaTypes.map(_.fullType).mkString(",")

  private def encodeBody[A](schema: Schema[A], mediaTypes: Chunk[MediaType], value: A): Body = {
    val bytes = schema.jsonCodec.encode(value)
    Body.fromArray(bytes, contentTypeFor(mediaTypes))
  }

  /** Extracts the [[Schema]] backing a codec when it is body-shaped. */
  private def schemaOf[K <: CodecKind, A](codec: HttpCodec[K, A]): Option[Schema[A]] =
    codec match {
      case b: HttpCodec.Body[K, A] @unchecked => Some(b.schema)
      case _                                  => None
    }

  private implicit class ResponsePartsOps(private val self: ResponseParts) extends AnyVal {
    def merge(that: ResponseParts): Either[String, ResponseParts] =
      (self.body, that.body) match {
        case (Some(_), Some(_)) =>
          Left("Cannot encode response: codec tree contains more than one body")
        case _ =>
          Right(
            ResponseParts(
              self.headers ++ that.headers,
              self.body.orElse(that.body),
            ),
          )
      }
  }

  /**
   * Picks a [[ContentType]] from the codec's declared media types, honoring the
   * first declared entry and defaulting to `application/json`.
   */
  private def contentTypeFor(mediaTypes: Chunk[MediaType]): ContentType =
    if (mediaTypes.isEmpty) jsonContentType
    else ContentType(mediaTypes.head, None, None)
}
