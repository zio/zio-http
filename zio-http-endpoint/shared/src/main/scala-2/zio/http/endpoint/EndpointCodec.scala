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

import zio.blocks.endpoint.{CodecKind, HttpCodec}
import zio.blocks.mediatype.MediaType
import zio.blocks.schema.Schema
import zio.http.{Body, ContentType, Request, Response}

/**
 * Internal bridge between a zio-blocks [[HttpCodec]] description and the
 * concrete `zio.http` wire types ([[Request]] / [[Response]] / [[Body]]).
 *
 * zio-blocks' `HttpCodec` is a pure description (schema + declared media
 * types); it carries no HTTP encode/decode of its own. This object supplies
 * that, driven by the codec's [[Schema]] (via its derived JSON codec) and
 * negotiated against the codec's declared `mediaTypes`.
 *
 * The proof-of-concept handles the dominant body-valued shape (`HttpCodec.Body`
 * and the unit `HttpCodec.Empty`); richer shapes
 * (query/header/combine/fallback) are a follow-up and fall through to the JSON
 * body path so nothing silently misbehaves.
 */
private[endpoint] object EndpointCodec {

  /** JSON content type used when a codec declares no usable media type. */
  private val jsonContentType: ContentType = ContentType.`application/json`

  /**
   * Decodes a request body into the value described by a request-side codec.
   * Returns `Left(message)` when the bytes do not conform to the schema.
   */
  def decodeRequest[A](codec: HttpCodec[CodecKind.Request, A], request: Request): Either[String, A] =
    codec match {
      case HttpCodec.Empty                                           => Right(().asInstanceOf[A])
      case body: HttpCodec.Body[CodecKind.Request, A] @unchecked     =>
        decodeBody(body.schema, request.body)
      case header: HttpCodec.Header[CodecKind.Request, A] @unchecked =>
        decodeHeader(header, request)
      case other                                                     =>
        decodeBodyFromSchema(other, request.body)
    }

  def decodeHeader[A](header: HttpCodec.Header[CodecKind.Request, A], request: Request): Either[String, A] =
    request.headers.rawGet(header.name) match {
      case None            => Left(s"Missing header: ${header.name}")
      case Some(headerStr) =>
        try header.schema.fromDynamicValue(zio.blocks.schema.DynamicValue.string(headerStr)).left.map(_.getMessage)
        catch { case scala.util.control.NonFatal(e) => Left(e.getMessage) }
    }

  /**
   * Encodes a response-side value into an HTTP [[Response]], choosing the wire
   * media type from the codec's declared `mediaTypes` (falling back to JSON).
   */
  def encodeResponse[A](codec: HttpCodec[CodecKind.Response, A], value: A, status: Int): Response = {
    val body = codec match {
      case HttpCodec.Empty                                     => Body.empty
      case b: HttpCodec.Body[CodecKind.Response, A] @unchecked =>
        encodeBody(b.schema, b.mediaTypes, value)
      case other                                               =>
        encodeBodyFromSchema(other, value)
    }
    Response(
      status = zio.http.Status(status),
      headers = zio.http.Headers.empty,
      body = body,
      version = zio.http.Version.`HTTP/1.1`,
    )
  }

  /**
   * Encodes a request-side value into an HTTP request body.
   */
  def encodeRequestBody[A](codec: HttpCodec[CodecKind.Request, A], value: A): Body = {
    codec match {
      case HttpCodec.Empty                                    => Body.empty
      case b: HttpCodec.Body[CodecKind.Request, A] @unchecked =>
        encodeBody(b.schema, b.mediaTypes, value)
      case _                                                  => Body.empty
    }
  }

  /**
   * Decodes a response body into the value described by a response-side codec.
   * Returns `Left(message)` when the bytes do not conform to the schema.
   */
  def decodeResponse[A](codec: HttpCodec[CodecKind.Response, A], response: Response): Either[String, A] =
    codec match {
      case HttpCodec.Empty                                        => Right(().asInstanceOf[A])
      case body: HttpCodec.Body[CodecKind.Response, A] @unchecked =>
        decodeBody(body.schema, response.body)
      case _                                                      => Left("Unsupported codec type")
    }

  private def decodeBody[A](schema: Schema[A], body: Body): Either[String, A] =
    schema.jsonCodec.decode(body.toArray) match {
      case Right(a)    => Right(a)
      case Left(error) => Left(error.getMessage)
    }

  /**
   * Best-effort decode for non-`Body` request codecs: unit codecs decode to
   * `()`, everything else attempts a JSON body decode via the codec's schema if
   * one is reachable.
   */
  private def decodeBodyFromSchema[A](codec: HttpCodec[CodecKind.Request, A], body: Body): Either[String, A] =
    schemaOf(codec) match {
      case Some(schema) => decodeBody(schema, body)
      case None         => Right(().asInstanceOf[A])
    }

  private def encodeBody[A](schema: Schema[A], mediaTypes: zio.blocks.chunk.Chunk[MediaType], value: A): Body = {
    val bytes = schema.jsonCodec.encode(value)
    Body.fromArray(bytes, contentTypeFor(mediaTypes))
  }

  private def encodeBodyFromSchema[A](codec: HttpCodec[CodecKind.Response, A], value: A): Body =
    schemaOf(codec) match {
      case Some(schema) => Body.fromArray(schema.jsonCodec.encode(value), jsonContentType)
      case None         => Body.empty
    }

  /** Extracts the [[Schema]] backing a codec when it is body-shaped. */
  private def schemaOf[K <: CodecKind, A](codec: HttpCodec[K, A]): Option[Schema[A]] =
    codec match {
      case b: HttpCodec.Body[K, A] @unchecked => Some(b.schema)
      case _                                  => None
    }

  /**
   * Picks a [[ContentType]] from the codec's declared media types, honoring the
   * first declared entry and defaulting to `application/json`.
   */
  private def contentTypeFor(mediaTypes: zio.blocks.chunk.Chunk[MediaType]): ContentType =
    if (mediaTypes.isEmpty) jsonContentType
    else ContentType(mediaTypes.head, None, None)
}
