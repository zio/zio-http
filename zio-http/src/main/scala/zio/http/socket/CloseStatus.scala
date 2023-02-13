package zio.http.socket

import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed abstract class CloseStatus(val asJava: WebSocketCloseStatus)
object CloseStatus {
  case object NormalClosure       extends CloseStatus(WebSocketCloseStatus.NORMAL_CLOSURE)
  case object EndpointUnavailable extends CloseStatus(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE)
  case object ProtocolError       extends CloseStatus(WebSocketCloseStatus.PROTOCOL_ERROR)
  case object InvalidMessageType  extends CloseStatus(WebSocketCloseStatus.INVALID_MESSAGE_TYPE)
  case object InvalidPayloadData  extends CloseStatus(WebSocketCloseStatus.INVALID_PAYLOAD_DATA)
  case object PolicyViolation     extends CloseStatus(WebSocketCloseStatus.POLICY_VIOLATION)
  case object MessageTooBig       extends CloseStatus(WebSocketCloseStatus.MESSAGE_TOO_BIG)
  case object MandatoryExtension  extends CloseStatus(WebSocketCloseStatus.MANDATORY_EXTENSION)
  case object InternalServerError extends CloseStatus(WebSocketCloseStatus.INTERNAL_SERVER_ERROR)
  case object ServiceRestart      extends CloseStatus(WebSocketCloseStatus.SERVICE_RESTART)
  case object TryAgainLater       extends CloseStatus(WebSocketCloseStatus.TRY_AGAIN_LATER)
  case object BadGateway          extends CloseStatus(WebSocketCloseStatus.BAD_GATEWAY)
  case object Empty               extends CloseStatus(WebSocketCloseStatus.EMPTY)
  case object AbnormalClosure     extends CloseStatus(WebSocketCloseStatus.ABNORMAL_CLOSURE)
  case object TlsHandshakeFailed  extends CloseStatus(WebSocketCloseStatus.TLS_HANDSHAKE_FAILED)
}
