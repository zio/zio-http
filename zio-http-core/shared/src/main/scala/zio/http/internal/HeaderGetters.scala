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

package zio.http.internal

import zio._

import zio.schema.Schema

import zio.http.Header.HeaderType
import zio.http.Headers
import zio.http.codec.HttpCodecError

/**
 * Maintains a list of operators that parse and extract data from the headers.
 *
 * NOTE: Add methods here if it performs some kind of processing on the header
 * and returns the result.
 */
trait HeaderGetters { self =>

  /**
   * Gets a header or returns None if the header was not present or it could not
   * be parsed
   */
  final def header(headerType: HeaderType): Option[headerType.HeaderValue] =
    headers.get(headerType.name).flatMap { raw =>
      val parsed = headerType.parse(raw)
      parsed.toOption
    }

  /**
   * Retrieves the header with the specified name as a value of the specified
   * type. The type must have a schema and can be a primitive type (e.g. Int,
   * String, UUID, Instant etc.), a case class with a single field or a
   * collection of either of these.
   */
  final def header[T](name: String)(implicit schema: Schema[T]): Either[HttpCodecError.HeaderError, T] =
    try
      Right(
        StringSchemaCodec
          .headerFromSchema(schema, ErrorConstructor.header, name)
          .decode(headers),
      )
    catch {
      case e: HttpCodecError.HeaderError => Left(e)
    }

  /**
   * Retrieves headers as a value of the specified type. The type must have a
   * schema and be a case class and all fields must be headers. So fields must
   * be of primitive types (e.g. Int, String, UUID, Instant etc.), a case class
   * with a single field or a collection of either of these. Headers are
   * selected by field names.
   */
  final def header[T](implicit schema: Schema[T]): Either[HttpCodecError.HeaderError, T] =
    try
      Right(
        StringSchemaCodec
          .headerFromSchema(schema, ErrorConstructor.header, null)
          .decode(headers),
      )
    catch {
      case e: HttpCodecError.HeaderError => Left(e)
    }

  /**
   * Retrieves the header with the specified name as a value of the specified
   * type T, or returns a default value if the header is not present or could
   * not be parsed. The type T must have a schema and can be a primitive type
   * (e.g. Int, String, UUID, Instant etc.), a case class with a single field or
   * a collection of either of these.
   */
  final def headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T =
    header[T](name).getOrElse(default)

  /**
   * Retrieves headers as a value of the specified type T, or returns a default
   * value if the headers are not present or could not be parsed. The type T
   * must have a schema and be a case class and all fields must be headers. So
   * fields must be of primitive types (e.g. Int, String, UUID, Instant etc.), a
   * case class with a single field or a collection of either of these. Headers
   * are selected by field names.
   */
  final def headerOrElse[T](default: => T)(implicit schema: Schema[T]): T =
    header[T].getOrElse(default)

  final def headerZIO[T](name: String)(implicit schema: Schema[T]): IO[HttpCodecError.HeaderError, T] =
    ZIO.fromEither(header[T](name))

  final def headers(headerType: HeaderType): Chunk[headerType.HeaderValue] =
    Chunk.fromIterator(
      headers.iterator
        .filter(header =>
          CharSequenceExtensions
            .equals(header.headerNameAsCharSequence, headerType.name, CaseMode.Insensitive),
        )
        .flatMap { raw =>
          val parsed = headerType.parse(raw.renderedValue)
          parsed.toOption
        },
    )

  /**
   * Gets a header. If the header is not present, returns None. If the header
   * could not be parsed it returns the parsing error
   */
  final def headerOrFail(headerType: HeaderType): Option[Either[String, headerType.HeaderValue]] =
    headers.get(headerType.name).map(headerType.parse(_))

  /**
   * Returns the headers
   */
  def headers: Headers

  /** Gets the raw unparsed header value */
  final def rawHeader(name: CharSequence): Option[String] = headers.get(name)

  def rawHeaders(name: CharSequence): Chunk[String] =
    Chunk.fromIterator(
      headers.iterator
        .filter(header => CharSequenceExtensions.equals(header.headerNameAsCharSequence, name, CaseMode.Insensitive))
        .map(_.renderedValue),
    )

  /** Gets the raw unparsed header value */
  final def rawHeader(headerType: HeaderType): Option[String] =
    rawHeader(headerType.name)
}
