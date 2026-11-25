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

package zio.http

/**
 * Represents a form decoding error.
 */
sealed trait FormDecodingError extends Exception { self =>
  import FormDecodingError._

  /** Returns `message`. */
  override def getMessage(): String = message

  /** The description of the error. */
  def message: String = self match {
    case ContentDispositionMissingName     => "Content-Disposition header is missing 'name' field"
    case FormDataMissingContentDisposition => "Form data is missing Content-Disposition header"
    case InvalidCharset(msg)               => s"Invalid charset: $msg"
    case InvalidURLEncodedFormat(msg)      => s"Invalid URL encoded format: $msg"
    case BoundaryNotFoundInContent         => "Boundary not found in content or was malformed."
  }

  /** Wraps this error in a `FormDecodingException`. */
  def asException: FormDecodingException = FormDecodingException(self)
}

/**
 * The form decoding errors.
 */
object FormDecodingError {

  /**
   * An exception that carries a `FormDecodingError` in `error`. Its
   * `getMessage` is `null`.
   */
  final case class FormDecodingException(error: FormDecodingError) extends Exception

  /** A part has a `Content-Disposition` header without a `name`. */
  case object ContentDispositionMissingName extends FormDecodingError

  /** A part has no `Content-Disposition` header. */
  case object FormDataMissingContentDisposition extends FormDecodingError

  /**
   * The charset of a part, or of the URL encoded data, is not supported. `msg`
   * is the reason.
   */
  final case class InvalidCharset(msg: String) extends FormDecodingError

  /** The URL encoded data could not be decoded. `msg` is the reason. */
  final case class InvalidURLEncodedFormat(msg: String) extends FormDecodingError

  /** The multipart content has no boundary, or it is malformed. */
  case object BoundaryNotFoundInContent extends FormDecodingError
}
