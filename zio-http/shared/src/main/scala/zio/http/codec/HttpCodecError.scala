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

/**
 * The error raised when an [[HttpCodec]] cannot decode or encode part of a
 * request or response, for example a missing header or a malformed query
 * parameter. It is an `Exception` without a stack trace. `HttpCodec` decoding
 * raises most of these errors as defects (`Cause.die`); a body that cannot be
 * decoded is a typed failure with [[HttpCodecError.MalformedBody]]. Encoding
 * throws them.
 */
sealed trait HttpCodecError extends Exception with NoStackTrace with Product with Serializable {
  override def getMessage(): String = message

  /**
   * The human-readable description of the error, also returned by `getMessage`.
   */
  def message: String
}

/** The cases of [[HttpCodecError]] and helpers to find them in a `Cause`. */
object HttpCodecError {

  /** The errors about query parameters. */
  sealed trait QueryParamError extends HttpCodecError

  /** The errors about headers. */
  sealed trait HeaderError extends HttpCodecError

  /** A required header is missing. */
  final case class MissingHeader(headerName: String) extends HeaderError {

    /** Returns `Missing header` followed by the header name. */
    def message = s"Missing header $headerName"
  }

  /** Some required headers are missing. */
  final case class MissingHeaders(headerNames: Chunk[String]) extends HeaderError {

    /**
     * Returns `Missing headers` followed by the header names, separated by
     * commas.
     */
    def message = s"Missing headers ${headerNames.mkString(", ")}"
  }

  /** The request method is not the `expected` one. */
  final case class MalformedMethod(expected: zio.http.Method, actual: zio.http.Method) extends HttpCodecError {

    /** Returns a message such as `Expected GET but found POST`. */
    def message = s"Expected $expected but found $actual"
  }

  /** The path ended before the segment described by `textCodec`. */
  final case class PathTooShort(path: Path, textCodec: TextCodec[_]) extends HttpCodecError {

    /** Returns a message that names the expected segment and the path. */
    def message = s"Expected to find ${textCodec} but found pre-mature end to the path ${path}"
  }

  /** The path could not be decoded by `pathCodec`; `error` says why. */
  final case class MalformedPath(path: Path, pathCodec: PathCodec[_], error: String) extends HttpCodecError {

    /** Returns a message that names the path, the path codec and the error. */
    def message = s"Malformed path ${path} failed to decode using $pathCodec: $error"
  }

  /** The response status is not the `expected` one. */
  final case class MalformedStatus(expected: Status, actual: Status) extends HttpCodecError {

    /**
     * Returns a message such as `Expected status code Created but found Ok`.
     */
    def message = s"Expected status code ${expected} but found ${actual}"
  }

  /**
   * The value of the header `headerName` could not be decoded by `textCodec`.
   */
  final case class MalformedHeader(headerName: String, textCodec: TextCodec[_]) extends HttpCodecError {

    /** Returns a message that names the header and the text codec. */
    def message = s"Malformed header $headerName failed to decode using $textCodec"
  }

  /**
   * The value of the header `headerName` could not be decoded with its schema;
   * `cause` says why.
   */
  final case class DecodingErrorHeader(headerName: String, cause: DecodeError) extends HeaderError {

    /** Returns a message that names the header and the decoding error. */
    def message = s"Malformed header $headerName could not be decoded: $cause"
  }

  /** A required query parameter is missing. */
  final case class MissingQueryParam(queryParamName: String) extends QueryParamError {

    /** Returns `Missing query parameter` followed by the parameter name. */
    def message = s"Missing query parameter $queryParamName"
  }

  /** Some required query parameters are missing. */
  final case class MissingQueryParams(queryParamNames: Chunk[String]) extends QueryParamError {

    /**
     * Returns `Missing query parameters` followed by the parameter names,
     * separated by commas.
     */
    def message = s"Missing query parameters ${queryParamNames.mkString(", ")}"
  }

  /**
   * The value of the query parameter `queryParamName` could not be decoded;
   * `cause` says why.
   */
  final case class MalformedQueryParam(queryParamName: String, cause: DecodeError) extends QueryParamError {

    /**
     * Returns a message that names the query parameter and the decoding error.
     */
    def message = s"Malformed query parameter $queryParamName could not be decoded: $cause"
  }

  /**
   * The body could not be decoded; `details` says why. It is used for request
   * and response bodies, although its message says "request body".
   */
  final case class MalformedBody(details: String, cause: Option[Throwable] = None) extends HttpCodecError {

    /**
     * Returns `Malformed request body failed to decode:` followed by `details`.
     */
    def message = s"Malformed request body failed to decode: $details"
  }

  /**
   * A decoded value failed the validation of its schema; `cause` holds the
   * validation errors.
   */
  final case class InvalidEntity(details: String, cause: Chunk[ValidationError] = Chunk.empty) extends HttpCodecError {

    /**
     * Returns `A well-formed entity failed validation:` followed by `details`.
     */
    def message = s"A well-formed entity failed validation: $details"
  }

  /** Constructors of [[InvalidEntity]]. */
  object InvalidEntity {

    /**
     * Creates an [[InvalidEntity]] whose details are the messages of `errors`,
     * one per line.
     */
    def wrap(errors: Chunk[ValidationError]): InvalidEntity =
      InvalidEntity(
        errors.map(err => err.message).mkString("\n"),
        errors,
      )
  }

  /**
   * The query parameter `name` has `actual` values, but `expected` were
   * required.
   */
  final case class InvalidQueryParamCount(name: String, expected: Int, actual: Int) extends QueryParamError {

    /**
     * Returns a message that names the parameter, the expected count and the
     * actual count.
     */
    def message = s"Invalid query parameter count for $name: expected $expected but found $actual."
  }

  /**
   * The header `name` has `actual` values, but `expected` were required. Its
   * message starts with "Invalid query parameter count", the same text as
   * [[InvalidQueryParamCount]].
   */
  final case class InvalidHeaderCount(name: String, expected: Int, actual: Int) extends HeaderError {

    /**
     * Returns a message that names the header, the expected count and the
     * actual count.
     */
    def message = s"Invalid query parameter count for $name: expected $expected but found $actual."
  }

  /**
   * An error with a free-form `name` and `message`. A failing `transformOrFail`
   * of an [[HttpCodec]] raises one named `SchemaTransformationFailure`.
   */
  final case class CustomError(name: String, message: String) extends HttpCodecError

  /** The content type `contentType` is not supported. */
  final case class UnsupportedContentType(contentType: String) extends HttpCodecError {

    /** Returns `Unsupported content type` followed by the content type. */
    def message = s"Unsupported content type $contentType"
  }

  /**
   * Encoding a response failed. The handlers built from an `Endpoint` respond
   * with this error when the output or the error of the endpoint cannot be
   * encoded.
   */
  case object EncodingResponseError extends HttpCodecError {
    override def message: String = "Unexpected error happened when encoding response"
  }

  /**
   * Returns the first defect of `cause` when `cause` has no typed failure and
   * all its defects are [[HttpCodecError]]s, and `None` otherwise.
   */
  def asHttpCodecError(cause: Cause[Any]): Option[HttpCodecError] = {
    if (!cause.isFailure && cause.defects.forall(e => e.isInstanceOf[HttpCodecError]))
      cause.defects.headOption.asInstanceOf[Option[HttpCodecError]]
    else
      None
  }

  /**
   * Returns `true` when `cause` has no typed failure and all its defects are
   * [[HttpCodecError]]s. It also returns `true` for a cause with no defects,
   * such as `Cause.empty`.
   */
  def isHttpCodecError(cause: Cause[Any]): Boolean = {
    !cause.isFailure && cause.defects.forall(e => e.isInstanceOf[HttpCodecError])
  }

  /**
   * Returns `true` when `cause` has no typed failure and all its defects are
   * [[MissingHeader]], [[MissingHeaders]], [[MissingQueryParam]] or
   * [[MissingQueryParams]] errors. `HttpCodec.optional` uses it to decide when
   * to fall back to `None`.
   */
  def isMissingDataOnly(cause: Cause[Any]): Boolean =
    !cause.isFailure && cause.defects.forall(e =>
      e.isInstanceOf[HttpCodecError.MissingHeader]
        || e.isInstanceOf[HttpCodecError.MissingQueryParam]
        || e.isInstanceOf[HttpCodecError.MissingQueryParams]
        || e.isInstanceOf[HttpCodecError.MissingHeaders],
    )
}
