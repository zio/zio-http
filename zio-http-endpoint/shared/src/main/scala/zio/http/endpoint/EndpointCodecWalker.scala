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
import zio.blocks.endpoint.{Alternator, AuthType, CodecKind, Endpoint, HttpCodec, RoutePattern}
import zio.blocks.mediatype.MediaType
import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import zio.http.{Body, ContentType, Path}

/**
 * Walks a request-side [[HttpCodec]] tree to decompose an endpoint `Input`
 * value into its wire slots: path params, query params, headers, and body.
 *
 * Each codec node is handled explicitly — `Combine` splits the tuple `Input`
 * via its [[Tuples]] combiner, `Fallback` dispatches via its [[Alternator]],
 * `Query` / `Header` render their fragment to a string through the node's own
 * [[Schema]] (schema-aware, no type-name heuristics), `Body` encodes through
 * its schema's JSON codec, and `Empty` contributes nothing. Path params come
 * from the endpoint's [[RoutePattern]] (rendered via `RoutePattern.format`,
 * which delegates to `PathCodec` / `PathCodecRuntime` / `SegmentCodec`), never
 * inferred from the input codec tree.
 *
 * The result is consumed by `EndpointBridge.buildRequest` (Todo 11), which
 * renders the decomposed slots into a real [[zio.http.Request]].
 *
 * Cross-version note: this file lives in the version-shared sources and must
 * compile on BOTH Scala 2.13 and Scala 3 — shared syntax only.
 */
object EndpointCodecWalker {

  /**
   * An endpoint `Input` (plus its `PathInput`) decomposed into wire slots.
   *
   * @param pathParams
   *   the typed path input, as declared by the endpoint's `RoutePattern`
   * @param path
   *   the rendered path (`RoutePattern.format(pathParams)`)
   * @param queryParams
   *   rendered query params, one entry per `Query` node
   * @param headers
   *   rendered headers, one entry per request `Header` node
   * @param body
   *   the encoded body when the codec tree contains a `Body` node
   */
  final case class DecomposedInput[PathInput](
    pathParams: PathInput,
    path: Path,
    queryParams: Map[String, String],
    headers: Map[String, String],
    body: Option[Body],
  )

  /**
   * Decomposes `input` (with `pathInput` for the route's path params) against
   * an endpoint's route and input codec.
   */
  def decompose[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    pathInput: PathInput,
    input: Input,
  ): Either[String, DecomposedInput[PathInput]] =
    decompose(endpoint.route, endpoint.input, pathInput, input)

  /**
   * Decomposes `input` (with `pathInput` for the route's path params) against
   * a route and a request-side input codec.
   */
  def decompose[PathInput, Input](
    route: RoutePattern[PathInput],
    inputCodec: HttpCodec[CodecKind.Request, Input],
    pathInput: PathInput,
    input: Input,
  ): Either[String, DecomposedInput[PathInput]] =
    for {
      path      <- renderPath(route, pathInput)
      fragments <- walk(inputCodec, input)
    } yield DecomposedInput(pathInput, path, fragments.queryParams, fragments.headers, fragments.body)

  private final case class Fragments(
    queryParams: Map[String, String],
    headers: Map[String, String],
    body: Option[Body],
  )

  private val emptyFragments: Fragments =
    Fragments(Map.empty[String, String], Map.empty[String, String], None)

  private def renderPath[PathInput](
    route: RoutePattern[PathInput],
    pathInput: PathInput,
  ): Either[String, Path] =
    try route.format(pathInput).left.map(message => s"Cannot render path params: $message")
    catch { case scala.util.control.NonFatal(e) => Left(s"Cannot render path params: ${e.getMessage}") }

  private def walk[A](codec: HttpCodec[CodecKind.Request, A], value: A): Either[String, Fragments] =
    // The match is exhaustive for request-side codecs (`StatusCodec` is
    // response-only); `@unchecked` keeps both toolchains' exhaustivity
    // checkers quiet (Scala 3 proves it, Scala 2.13 cannot), with `null`
    // handled explicitly above the match.
    if (codec == null) Left("Cannot decompose input: codec is null")
    else
      (codec: HttpCodec[CodecKind.Request, A] @unchecked) match {
        case HttpCodec.Empty =>
          Right(emptyFragments)
        case query: HttpCodec.Query[a] @unchecked =>
          renderLeaf("query", query.name, query.schema, value).map {
            case Some(rendered) => emptyFragments.copy(queryParams = Map(query.name -> rendered))
            case None           => emptyFragments
          }
        case header: HttpCodec.Header[CodecKind.Request, a] @unchecked =>
          renderLeaf("header", header.name, header.schema, value).map {
            case Some(rendered) => emptyFragments.copy(headers = Map(header.name -> rendered))
            case None           => emptyFragments
          }
        case body: HttpCodec.Body[CodecKind.Request, a] @unchecked =>
          encodeBody(body.schema, body.mediaTypes, value).map(encoded =>
            emptyFragments.copy(body = Some(encoded)),
          )
        case combine: HttpCodec.Combine[CodecKind.Request, a, b, c] @unchecked =>
          walkCombine(combine.left, combine.right, combine.combiner, value)
        case fallback: HttpCodec.Fallback[CodecKind.Request, a, b, c] @unchecked =>
          walkFallback(fallback.left, fallback.right, fallback.alternator, value)
      }

  private def walkCombine[A, B, C](
    left: HttpCodec[CodecKind.Request, A],
    right: HttpCodec[CodecKind.Request, B],
    combiner: Tuples.Tuples.WithOut[A, B, C],
    input: C,
  ): Either[String, Fragments] =
    for {
      pair  <- splitTuple(combiner, input)
      first <- walk(left, pair._1)
      second <- walk(right, pair._2)
      merged <- first.merge(second)
    } yield merged

  private def splitTuple[A, B, C](
    combiner: Tuples.Tuples.WithOut[A, B, C],
    input: C,
  ): Either[String, (A, B)] =
    try Right(combiner.separate(input))
    catch { case scala.util.control.NonFatal(e) => Left(s"Cannot split combined input: ${e.getMessage}") }

  private def walkFallback[A, B, C](
    left: HttpCodec[CodecKind.Request, A],
    right: HttpCodec[CodecKind.Request, B],
    alternator: Alternator.WithOut[A, B, C],
    input: C,
  ): Either[String, Fragments] =
    try alternator.separate(input) match {
      case Left(first)  => walk(left, first).left.map(message => s"Fallback (first alternative) failed: $message")
      case Right(second) => walk(right, second).left.map(message => s"Fallback (second alternative) failed: $message")
    } catch {
      case scala.util.control.NonFatal(e) => Left(s"Fallback dispatch failed: ${e.getMessage}")
    }

  /**
   * Renders one query/header fragment to its wire string through the node's
   * own schema: primitives render as their plain value, `None`-shaped values
   * (dynamic `Null`) render as absent so the entry is omitted, anything else
   * is a descriptive error.
   */
  private def renderLeaf[A](
    kind: String,
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
        Left(s"Cannot render $kind '$name': unsupported primitive ${other.getClass.getSimpleName}")
      case DynamicValue.Null =>
        Right(None)
      case other =>
        Left(s"Cannot render $kind '$name': expected a primitive value but found ${other.getClass.getSimpleName}")
    } catch {
      case scala.util.control.NonFatal(e) => Left(s"Cannot render $kind '$name': ${e.getMessage}")
    }

  private def encodeBody[A](
    schema: Schema[A],
    mediaTypes: Chunk[MediaType],
    value: A,
  ): Either[String, Body] =
    try {
      val bytes = schema.jsonCodec.encode(value)
      Right(Body.fromArray(bytes, contentTypeFor(mediaTypes)))
    } catch {
      case scala.util.control.NonFatal(e) => Left(s"Cannot encode body: ${e.getMessage}")
    }

  private implicit class FragmentsOps(private val self: Fragments) extends AnyVal {
    def merge(that: Fragments): Either[String, Fragments] =
      (self.body, that.body) match {
        case (Some(_), Some(_)) =>
          Left("Cannot decompose input: codec tree contains more than one body")
        case _ =>
          Right(
            Fragments(
              self.queryParams ++ that.queryParams,
              self.headers ++ that.headers,
              self.body.orElse(that.body),
            ),
          )
      }
  }

  private val jsonContentType: ContentType = ContentType.`application/json`

  private def contentTypeFor(mediaTypes: Chunk[MediaType]): ContentType =
    if (mediaTypes.isEmpty) jsonContentType
    else ContentType(mediaTypes.head, None, None)
}
