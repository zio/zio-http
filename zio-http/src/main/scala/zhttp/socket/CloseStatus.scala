package zhttp.socket

import io.netty.handler.codec.http.websocketx.{WebSocketCloseStatus => JWebSocketCloseStatus}

sealed abstract class CloseStatus(val asJava: JWebSocketCloseStatus)
object CloseStatus {
  case object NormalClosure       extends CloseStatus(JWebSocketCloseStatus.NORMAL_CLOSURE)
  case object EndpointUnavailable extends CloseStatus(JWebSocketCloseStatus.ENDPOINT_UNAVAILABLE)
  case object ProtocolError       extends CloseStatus(JWebSocketCloseStatus.PROTOCOL_ERROR)
  case object InvalidMessageType  extends CloseStatus(JWebSocketCloseStatus.INVALID_MESSAGE_TYPE)
  case object InvalidPayloadData  extends CloseStatus(JWebSocketCloseStatus.INVALID_PAYLOAD_DATA)
  case object PolicyViolation     extends CloseStatus(JWebSocketCloseStatus.POLICY_VIOLATION)
  case object MessageTooBig       extends CloseStatus(JWebSocketCloseStatus.MESSAGE_TOO_BIG)
  case object MandatoryExtension  extends CloseStatus(JWebSocketCloseStatus.MANDATORY_EXTENSION)
  case object InternalServerError extends CloseStatus(JWebSocketCloseStatus.INTERNAL_SERVER_ERROR)
  case object ServiceRestart      extends CloseStatus(JWebSocketCloseStatus.SERVICE_RESTART)
  case object TryAgainLater       extends CloseStatus(JWebSocketCloseStatus.TRY_AGAIN_LATER)
  case object BadGateway          extends CloseStatus(JWebSocketCloseStatus.BAD_GATEWAY)
  case object Empty               extends CloseStatus(JWebSocketCloseStatus.EMPTY)
  case object AbnormalClosure     extends CloseStatus(JWebSocketCloseStatus.ABNORMAL_CLOSURE)
  case object TlsHandshakeFailed  extends CloseStatus(JWebSocketCloseStatus.TLS_HANDSHAKE_FAILED)
}
