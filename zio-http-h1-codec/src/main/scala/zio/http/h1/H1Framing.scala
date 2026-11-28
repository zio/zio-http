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

package zio.http.h1

object H1Framing {
  def resolve(
    headers: H1Headers,
    limits: H1Limits,
    isResponse: Boolean,
    statusCode: Int,
    requestMethod: String,
  ): Either[H1Error, H1BodyFraming] = {
    val contentLengths    = headers.headerValues("Content-Length")
    val transferEncodings = headers.headerValues("Transfer-Encoding")
    val trailerAnnounced  = headers.contains("Trailer")

    if (isNoBodyResponse(isResponse, statusCode, requestMethod)) {
      if (transferEncodings.nonEmpty)
        Left(H1Error.AmbiguousFraming("Transfer-Encoding is forbidden on a bodyless response"))
      else Right(H1BodyFraming.Empty)
    } else if (transferEncodings.nonEmpty && contentLengths.nonEmpty) {
      Left(H1Error.AmbiguousFraming("Transfer-Encoding and Content-Length must not be combined"))
    } else if (contentLengths.length > 1) {
      Left(H1Error.AmbiguousFraming("duplicate Content-Length"))
    } else if (transferEncodings.length > 1) {
      Left(H1Error.AmbiguousFraming("duplicate Transfer-Encoding"))
    } else if (trailerAnnounced && transferEncodings.isEmpty) {
      Left(H1Error.AmbiguousFraming("Trailer requires chunked framing"))
    } else if (transferEncodings.length == 1) {
      val coding = H1Validation.trimOWS(transferEncodings.head)
      if (!H1Validation.equalsIgnoreCase(coding, "chunked"))
        Left(H1Error.AmbiguousFraming("unsupported transfer coding: '" + transferEncodings.head + "'"))
      else Right(H1BodyFraming.Chunked)
    } else if (contentLengths.length == 1) {
      H1Validation.parseContentLength(H1Validation.trimOWS(contentLengths.head)) match {
        case Left(error)   => Left(error)
        case Right(length) =>
          if (length > limits.maxUndecodedBytes.toLong)
            Left(H1Error.BodyTooLarge("Content-Length " + length + " exceeds the retention bound"))
          else Right(H1BodyFraming.Fixed(length))
      }
    } else {
      Right(H1BodyFraming.Empty)
    }
  }

  private def isNoBodyResponse(isResponse: Boolean, statusCode: Int, requestMethod: String): Boolean =
    isResponse && ((statusCode >= 100 && statusCode <= 199) || statusCode == 204 || statusCode == 304 ||
      H1Validation.equalsIgnoreCase(requestMethod, "HEAD"))
}
