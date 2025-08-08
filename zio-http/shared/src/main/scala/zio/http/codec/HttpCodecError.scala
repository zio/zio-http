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

package zio.http.codec

import scala.util.control.NoStackTrace

import zio.{Cause, Chunk}

import zio.schema.codec.DecodeError
import zio.schema.validation.ValidationError

import zio.http.{Path, Status}

sealed trait HttpCodecError extends Exception with NoStackTrace with Product with Serializable {
  override def getMessage(): String = message
  def message: String
}
object HttpCodecError {
  sealed trait QueryParamError extends HttpCodecError
  sealed trait HeaderError     extends HttpCodecError

  final case class MissingHeader(headerName: String)                                           extends HeaderError     {
    def message = s"Missing header $headerName"
  }
  final case class MissingHeaders(headerNames: Chunk[String])                                  extends HeaderError     {
    def message = s"Missing headers ${headerNames.mkString(", ")}"
  }
  final case class MalformedMethod(expected: zio.http.Method, actual: zio.http.Method)         extends HttpCodecError  {
    def message = s"Expected $expected but found $actual"
  }
  final case class PathTooShort(path: Path, textCodec: TextCodec[_])                           extends HttpCodecError  {
    def message = s"Expected to find ${textCodec} but found pre-mature end to the path ${path}"
  }
  final case class MalformedPath(path: Path, pathCodec: PathCodec[_], error: String)           extends HttpCodecError  {
    def message = s"Malformed path ${path} failed to decode using $pathCodec: $error"
  }
  final case class MalformedStatus(expected: Status, actual: Status)                           extends HttpCodecError  {
    def message = s"Expected status code ${expected} but found ${actual}"
  }
  final case class MalformedHeader(headerName: String, textCodec: TextCodec[_])                extends HttpCodecError  {
    def message = s"Malformed header $headerName failed to decode using $textCodec"
  }
  final case class DecodingErrorHeader(headerName: String, cause: DecodeError)                 extends HeaderError     {
    def message = s"Malformed header $headerName could not be decoded: $cause"
  }
  final case class MissingQueryParam(queryParamName: String)                                   extends QueryParamError {
    def message = s"Missing query parameter $queryParamName"
  }
  final case class MissingQueryParams(queryParamNames: Chunk[String])                          extends QueryParamError {
    def message = s"Missing query parameters ${queryParamNames.mkString(", ")}"
  }
  final case class MalformedQueryParam(queryParamName: String, cause: DecodeError)             extends QueryParamError {
    def message = s"Malformed query parameter $queryParamName could not be decoded: $cause"
  }
  final case class MalformedBody(details: String, cause: Option[Throwable] = None)             extends HttpCodecError  {
    def message = s"Malformed request body failed to decode: $details"
  }
  final case class InvalidEntity(details: String, cause: Chunk[ValidationError] = Chunk.empty) extends HttpCodecError  {
    def message = s"A well-formed entity failed validation: $details"
  }
  object InvalidEntity {
    def wrap(errors: Chunk[ValidationError]): InvalidEntity =
      InvalidEntity(
        errors.map(err => err.message).mkString("\n"),
        errors,
      )
  }
  final case class InvalidQueryParamCount(name: String, expected: Int, actual: Int)            extends QueryParamError {
    def message = s"Invalid query parameter count for $name: expected $expected but found $actual."
  }
  final case class InvalidHeaderCount(name: String, expected: Int, actual: Int)                extends HeaderError     {
    def message = s"Invalid query parameter count for $name: expected $expected but found $actual."
  }
  final case class CustomError(name: String, message: String)                                  extends HttpCodecError

  final case class UnsupportedContentType(contentType: String) extends HttpCodecError {
    def message = s"Unsupported content type $contentType"
  }

  def asHttpCodecError(cause: Cause[Any]): Option[HttpCodecError] = {
    if (!cause.isFailure && cause.defects.forall(e => e.isInstanceOf[HttpCodecError]))
      cause.defects.headOption.asInstanceOf[Option[HttpCodecError]]
    else
      None
  }

  def isHttpCodecError(cause: Cause[Any]): Boolean = {
    !cause.isFailure && cause.defects.forall(e => e.isInstanceOf[HttpCodecError])
  }

  def isMissingDataOnly(cause: Cause[Any]): Boolean =
    !cause.isFailure && cause.defects.forall(e =>
      e.isInstanceOf[HttpCodecError.MissingHeader]
        || e.isInstanceOf[HttpCodecError.MissingQueryParam]
        || e.isInstanceOf[HttpCodecError.MissingQueryParams]
        || e.isInstanceOf[HttpCodecError.MissingHeaders],
    )
}
