package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
sealed trait APIError extends Exception {
  def message: String

  override def getMessage: String = message
}

object APIError {
  sealed trait ClientError                                                extends APIError
  final case class NotFound(message: String, api: API[_, _])              extends ClientError
  final case class MalformedResponseBody(message: String, api: API[_, _]) extends ClientError

  sealed trait ServerError                                                              extends APIError
  final case class MissingHeader(headerName: String)                                    extends ServerError {
    def message = s"Missing header $headerName"
  }
  final case class MalformedHeader(headerName: String, textCodec: TextCodec[_])         extends ServerError {
    def message = s"Malformed header $headerName failed to decode using $textCodec"
  }
  final case class MissingQueryParam(queryParamName: String)                            extends ServerError {
    def message = s"Missing query parameter $queryParamName"
  }
  final case class MalformedQueryParam(queryParamName: String, textCodec: TextCodec[_]) extends ServerError {
    def message = s"Malformed query parameter $queryParamName failed to decode using $textCodec"
  }
  final case class MalformedRequestBody(api: API[_, _])                                 extends ServerError {
    def message = s"Malformed request body failed to decode using ${api.input.bodySchema}"
  }
}
