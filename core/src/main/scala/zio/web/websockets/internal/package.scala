package zio.web.websockets

package object internal {

  private[internal] object OpCode {
    final val Continuation = 0x00
    final val Text         = 0x01
    final val Binary       = 0x02
    final val Close        = 0x08
    final val Ping         = 0x09
    final val Pong         = 0x0A
  }
}
