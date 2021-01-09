package zio.web.websockets.internal

sealed abstract class CloseCode(code: Int) {

  def toBinary: Array[Byte] = {
    val arr = new Array[Byte](2)
    arr.update(0, ((code >> 8) & 0xFF).toByte)
    arr.update(1, (code & 0xFF).toByte)
    arr
  }
}

object CloseCode {
  case object NormalClosure       extends CloseCode(1000)
  case object GoingAway           extends CloseCode(1001)
  case object ProtocolError       extends CloseCode(1002)
  case object UnsupportedData     extends CloseCode(1003)
  case object NoStatusReceived    extends CloseCode(1005)
  case object AbnormalClosure     extends CloseCode(1006)
  case object InvalidFrame        extends CloseCode(1007)
  case object PolicyViolation     extends CloseCode(1008)
  case object MessageTooBig       extends CloseCode(1009)
  case object MandatoryExtension  extends CloseCode(1010)
  case object InternalServerError extends CloseCode(1011)
  case object TLSHandshake        extends CloseCode(1015)
}
