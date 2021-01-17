package zio.web.websockets.internal

import zio.{ Chunk, ChunkBuilder }

import scala.annotation.switch

sealed abstract class CloseCode(code: Int) {

  def toBinary: Chunk[Byte] = {
    val bytes = ChunkBuilder.make[Byte](2)
    bytes.addOne(((code >> 8) & 0xFF).toByte)
    bytes.addOne((code & 0xFF).toByte)
    bytes.result()
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

  def fromInt(code: Int): Option[CloseCode] =
    (code: @switch) match {
      case 1000 => Some(NormalClosure)
      case 1001 => Some(GoingAway)
      case 1002 => Some(ProtocolError)
      case 1003 => Some(UnsupportedData)
      case 1005 => Some(NoStatusReceived)
      case 1006 => Some(AbnormalClosure)
      case 1007 => Some(InvalidFrame)
      case 1008 => Some(PolicyViolation)
      case 1009 => Some(MessageTooBig)
      case 1010 => Some(MandatoryExtension)
      case 1011 => Some(InternalServerError)
      case 1015 => Some(TLSHandshake)
      case _    => None
    }
}
