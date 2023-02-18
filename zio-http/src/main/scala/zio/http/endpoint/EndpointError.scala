package zio.http.endpoint

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import zio.http.codec.TextCodec
import zio.http.Path

/**
 * Describes an endpoint error. All endpoint errors are either server-side or
 * client-side.
 */
sealed trait EndpointError extends Exception {
  def message: String

  override def getMessage: String = message
}

object EndpointError {
  sealed trait ClientError                                                           extends EndpointError
  final case class EndpointNotFound(message: String, api: Endpoint[_, _, _, _])      extends ClientError
  final case class MalformedResponseBody(message: String, api: Endpoint[_, _, _, _]) extends ClientError

  sealed trait ServerError                                                                   extends EndpointError
  final case class MissingHeader(headerName: String)                                         extends ServerError {
    def message = s"Missing header $headerName"
  }
  final case class MalformedMethod(methodName: String, textCodec: TextCodec[_])              extends ServerError {
    def message = s"Malformed method $methodName failed to decode using $textCodec"
  }
  final case class PathTooShort(path: Path, textCodec: TextCodec[_])                         extends ServerError {
    def message = s"Expected to find ${textCodec} but found pre-mature end to the path ${path}"
  }
  final case class MalformedPath(path: Path, segment: Path.Segment, textCodec: TextCodec[_]) extends ServerError {
    def message = "Malformed path segment \"" + segment.text + "\" of " + s"${path} failed to decode using $textCodec"
  }
  final case class MalformedStatus(status: String, textCodec: TextCodec[_])                  extends ServerError {
    def message = s"Malformed status $status failed to decode using $textCodec"
  }
  final case class MalformedHeader(headerName: String, textCodec: TextCodec[_])              extends ServerError {
    def message = s"Malformed header $headerName failed to decode using $textCodec"
  }
  final case class MissingQueryParam(queryParamName: String)                                 extends ServerError {
    def message = s"Missing query parameter $queryParamName"
  }
  final case class MalformedQueryParam(queryParamName: String, textCodec: TextCodec[_])      extends ServerError {
    def message = s"Malformed query parameter $queryParamName failed to decode using $textCodec"
  }
  final case class MalformedRequestBody(api: Endpoint[_, _, _, _])                           extends ServerError {
    def message = s"Malformed request body failed to decode"
  }
}
