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
import zio.schema.codec.DecodeError
import zio.schema.validation.ValidationError

import zio.http._
import zio.http.codec.{HttpCodecError, TextCodec}
import zio.http.internal.QueryGetters.emptyValue

trait QueryGetters[+A] { self: QueryOps[A] =>

  def queryParameters: QueryParams

  /**
   * Retrieves all query parameter values having the specified name.
   */
  def queryParams(key: String): Chunk[String] =
    queryParameters.getAll(key)

  /**
   * Retrieves all typed query parameter values having the specified name.
   */
  @deprecated("Use query(key)[Chunk[T]", "3.1.0")
  def queryParamsTo[T](key: String)(implicit codec: TextCodec[T]): Either[QueryParamsError, Chunk[T]] =
    for {
      params <- if (hasQueryParam(key)) Right(queryParams(key)) else Left(QueryParamsError.Missing(key))
      (failed, typed) = params.partitionMap(p => codec.decode(p).toRight(p))
      result <- NonEmptyChunk
        .fromChunk(failed)
        .map(fails => QueryParamsError.Malformed(key, codec, fails))
        .toLeft(typed)
    } yield result

  /**
   * Retrieves all typed query parameter values having the specified name as
   * ZIO.
   */
  @deprecated("Use queryZIO(key)[Chunk[T]", "3.1.0")
  def queryParamsToZIO[T](key: String)(implicit codec: TextCodec[T]): IO[QueryParamsError, Chunk[T]] =
    ZIO.fromEither(queryParamsTo[T](key))

  /**
   * Retrieves the query parameter with the specified name as a value of the
   * specified type. The type must have a schema and can be a primitive type
   * (e.g. Int, String, UUID, Instant etc.), a case class with a single field or
   * a collection of either of these.
   */
  def query[T](key: String)(implicit schema: Schema[T]): Either[HttpCodecError.QueryParamError, T] =
    try
      Right(
        StringSchemaCodec
          .queryFromSchema(schema, ErrorConstructor.query, key)
          .decode(queryParameters),
      )
    catch {
      case e: HttpCodecError.QueryParamError => Left(e)
    }

  /**
   * Retrieves query parameters as a value of the specified type. The type must
   * have a schema and be a case class and all fields must be query parameters.
   * So fields must be of primitive types (e.g. Int, String, UUID, Instant
   * etc.), a case class with a single field or a collection of either of these.
   * Query parameters are selected by field names.
   */
  def query[T](implicit schema: Schema[T]): Either[HttpCodecError.QueryParamError, T] =
    try
      Right(
        StringSchemaCodec
          .queryFromSchema(schema, ErrorConstructor.query, null)
          .decode(queryParameters),
      )
    catch {
      case e: HttpCodecError.QueryParamError => Left(e)
    }

  def queryOrElse[T](key: String, default: => T)(implicit schema: Schema[T]): T =
    query[T](key).getOrElse(default)

  def queryOrElse[T](default: => T)(implicit schema: Schema[T]): T =
    query[T].getOrElse(default)

  /**
   * Retrieves all typed query parameter values having the specified name as ZIO
   */
  def queryZIO[T](key: String)(implicit schema: Schema[T]): IO[HttpCodecError.QueryParamError, T] =
    ZIO.fromEither(query[T](key))

  /**
   * Retrieves the first query parameter value having the specified name.
   */
  def queryParam(key: String): Option[String] = {
    val queryParam = unsafeQueryParam(key)
    if (queryParam == null) None
    else if (queryParam.isBlank) emptyValue
    else Some(queryParam)
  }

  /**
   * Retrieves the first typed query parameter value having the specified name.
   */
  @deprecated("Use query(key)[T]", "3.1.0")
  def queryParamTo[T](key: String)(implicit codec: TextCodec[T]): Either[QueryParamsError, T] = for {
    param      <- queryParam(key).toRight(QueryParamsError.Missing(key))
    typedParam <- codec.decode(param).toRight(QueryParamsError.Malformed(key, codec, NonEmptyChunk(param)))
  } yield typedParam

  /**
   * Retrieves the first typed query parameter value having the specified name
   * as ZIO.
   */
  @deprecated("Use queryZIO(key)[T]", "3.1.0")
  def queryParamToZIO[T](key: String)(implicit codec: TextCodec[T]): IO[QueryParamsError, T] =
    ZIO.fromEither(queryParamTo[T](key))

  /**
   * Retrieves all query parameter values having the specified name, or else
   * uses the default iterable.
   */
  def queryParamsOrElse(key: String, default: => Iterable[String]): Chunk[String] =
    if (hasQueryParam(key)) queryParams(key) else Chunk.fromIterable(default)

  /**
   * Retrieves all query parameter values having the specified name, or else
   * uses the default iterable.
   */
  @deprecated("Use queryParamsOrElse(key, default)", "3.1.0")
  def queryParamsToOrElse[T](key: String, default: => Iterable[T])(implicit codec: TextCodec[T]): Chunk[T] =
    queryParamsTo[T](key).getOrElse(Chunk.fromIterable(default))

  /**
   * Retrieves the first query parameter value having the specified name, or
   * else uses the default value.
   */
  def queryParamOrElse(key: String, default: => String): String =
    queryParam(key).getOrElse(default)

  /**
   * Retrieves the first typed query parameter value having the specified name,
   * or else uses the default value.
   */
  @deprecated("Use queryParamOrElse(key, default)", "3.1.0")
  def queryParamToOrElse[T](key: String, default: => T)(implicit codec: TextCodec[T]): T =
    queryParamTo[T](key).getOrElse(default)

  private[http] def unsafeQueryParam(key: String): String = {
    val params = queryParams(key)
    if (params.isEmpty) null else params.head
  }

}

object QueryGetters {
  private[http] val emptyValue = Some("")
}
