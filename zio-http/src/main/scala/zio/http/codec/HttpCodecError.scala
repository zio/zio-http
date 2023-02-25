package zio.http.codec

import scala.util.control.NoStackTrace

import zio.Cause

import zio.http.Path
import zio.http.model.Status

sealed trait HttpCodecError extends Exception with NoStackTrace {
  def message: String
}
object HttpCodecError {
  final case class MissingHeader(headerName: String)                                    extends HttpCodecError {
    def message = s"Missing header $headerName"
  }
  final case class MalformedMethod(expected: zio.http.model.Method, actual: zio.http.model.Method)
      extends HttpCodecError {
    def message = s"Expected $expected but found $actual"
  }
  final case class PathTooShort(path: Path, textCodec: TextCodec[_])                    extends HttpCodecError {
    def message = s"Expected to find ${textCodec} but found pre-mature end to the path ${path}"
  }
  final case class MalformedPath(path: Path, segment: String, textCodec: TextCodec[_])  extends HttpCodecError {
    def message = "Malformed path segment \"" + segment + "\" of " + s"${path} failed to decode using $textCodec"
  }
  final case class MalformedStatus(expected: Status, actual: Status)                    extends HttpCodecError {
    def message = s"Expected status code ${expected} but found ${actual}"
  }
  final case class MalformedHeader(headerName: String, textCodec: TextCodec[_])         extends HttpCodecError {
    def message = s"Malformed header $headerName failed to decode using $textCodec"
  }
  final case class MissingQueryParam(queryParamName: String)                            extends HttpCodecError {
    def message = s"Missing query parameter $queryParamName"
  }
  final case class MalformedQueryParam(queryParamName: String, textCodec: TextCodec[_]) extends HttpCodecError {
    def message = s"Malformed query parameter $queryParamName failed to decode using $textCodec"
  }
  final case class MalformedBody(details: String)                                       extends HttpCodecError {
    def message = s"Malformed request body failed to decode: $details"
  }

  def isHttpCodecError(cause: Cause[Any]): Boolean = {
    !cause.isFailure && cause.defects.forall(e => e.isInstanceOf[HttpCodecError])
  }
}
