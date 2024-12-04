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

import zio.http._
import zio.http.codec.TextCodec

trait QueryGetters[+A] { self: QueryOps[A] =>

  def queryParameters: QueryParams

  /**
   * Retrieves all query parameter values having the specified name.
   */
  def queryParams(key: String): Chunk[String] =
    queryParameters.getAll(key)

  /**
   * Retrieves all typed query parameter values having the specified name, with schema validation.
   */
  def queryParamsTo[T](key: String, schema: Option[TextCodec[T]] = None)
                      (implicit codec: TextCodec[T]): Either[QueryParamsError, Chunk[T]] =
    for {
      params <- if (hasQueryParam(key)) Right(queryParams(key)) else Left(QueryParamsError.Missing(key))
      validatedCodec = schema.getOrElse(codec)
      (failed, typed) = params.partitionMap(p => validatedCodec.decode(p).toRight(p))
      result <- NonEmptyChunk
        .fromChunk(failed)
        .map(fails => QueryParamsError.Malformed(key, validatedCodec, fails))
        .toLeft(typed)
    } yield result

  /**
   * Retrieves all typed query parameter values having the specified name as ZIO, with schema validation.
   */
  def queryParamsToZIO[T](key: String, schema: Option[TextCodec[T]] = None)
                         (implicit codec: TextCodec[T]): IO[QueryParamsError, Chunk[T]] =
    ZIO.fromEither(queryParamsTo[T](key, schema))

  /**
   * Retrieves the first query parameter value having the specified name.
   */
  def queryParam(key: String): Option[String] =
    if (hasQueryParam(key)) Some(queryParams(key).head) else None

  /**
   * Retrieves the first typed query parameter value having the specified name, with schema validation.
   */
  def queryParamTo[T](key: String, schema: Option[TextCodec[T]] = None)
                     (implicit codec: TextCodec[T]): Either[QueryParamsError, T] = for {
    param      <- queryParam(key).toRight(QueryParamsError.Missing(key))
    validatedCodec = schema.getOrElse(codec)
    typedParam <- validatedCodec.decode(param).toRight(QueryParamsError.Malformed(key, validatedCodec, NonEmptyChunk(param)))
  } yield typedParam

  /**
   * Retrieves the first typed query parameter value having the specified name as ZIO, with schema validation.
   */
  def queryParamToZIO[T](key: String, schema: Option[TextCodec[T]] = None)
                        (implicit codec: TextCodec[T]): IO[QueryParamsError, T] =
    ZIO.fromEither(queryParamTo[T](key, schema))

  /**
   * Retrieves all query parameter values having the specified name, or else uses the default iterable.
   */
  def queryParamsOrElse(key: String, default: => Iterable[String]): Chunk[String] =
    if (hasQueryParam(key)) queryParams(key) else Chunk.fromIterable(default)

  /**
   * Retrieves all query parameter values having the specified name, or else uses the default iterable.
   */
  def queryParamsToOrElse[T](key: String, default: => Iterable[T])(implicit codec: TextCodec[T]): Chunk[T] =
    queryParamsTo[T](key).getOrElse(Chunk.fromIterable(default))

  /**
   * Retrieves the first query parameter value having the specified name, or else uses the default value.
   */
  def queryParamOrElse(key: String, default: => String): String =
    queryParam(key).getOrElse(default)

  /**
   * Retrieves the first typed query parameter value having the specified name, or else uses the default value.
   */
  def queryParamToOrElse[T](key: String, default: => T)(implicit codec: TextCodec[T]): T =
    queryParamTo[T](key).getOrElse(default)

  private[http] def unsafeQueryParam(key: String): String =
    queryParams(key).head

}
