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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Cause, Chunk}

import zio.schema.validation.ValidationError

import zio.http.{Path, Status}

sealed trait HttpCodecError extends Exception with NoStackTrace {
  override def getMessage(): String = message
  def message: String
}
object HttpCodecError {
  final case class MissingHeader(headerName: String)                                   extends HttpCodecError {
    def message = s"Missing header $headerName"
  }
  final case class MalformedMethod(expected: zio.http.Method, actual: zio.http.Method) extends HttpCodecError {
    def message = s"Expected $expected but found $actual"
  }

  final case class MalformedPath(path: Path, pathCodec: PathCodec[_], error: String) extends HttpCodecError {
    def message = s"Malformed path ${path} failed to decode using $pathCodec: $error"
  }
  final case class MalformedStatus(expected: Status, actual: Status)                 extends HttpCodecError {
    def message = s"Expected status code ${expected} but found ${actual}"
  }

  final case class MissingQueryParam(queryParamName: String) extends HttpCodecError {
    def message = s"Missing query parameter $queryParamName"
  }

  final case class MalformedBody(details: String, cause: Option[Throwable] = None)             extends HttpCodecError {
    def message = s"Malformed request body failed to decode: $details"
  }
  final case class InvalidEntity(details: String, cause: Chunk[ValidationError] = Chunk.empty) extends HttpCodecError {
    def message = s"A well-formed entity failed validation: $details"
  }
  object InvalidEntity {
    def wrap(errors: Chunk[ValidationError]): InvalidEntity =
      InvalidEntity(
        errors.foldLeft("")((acc, err) => acc + err.message + "\n"),
        errors,
      )
  }
  final case class CustomError(message: String)                                                extends HttpCodecError

  def isHttpCodecError(cause: Cause[Any]): Boolean = {
    !cause.isFailure && cause.defects.forall(e => e.isInstanceOf[HttpCodecError])
  }
}
