package zio.web.websockets

package object internal {
  final val CONTINUATION = 0x0
  final val TEXT         = 0x1
  final val BINARY       = 0x2
  final val CLOSE        = 0x8
  final val PING         = 0x9
  final val PONG         = 0xA
}
