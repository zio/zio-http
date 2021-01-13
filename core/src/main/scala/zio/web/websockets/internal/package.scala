package zio.web.websockets

package object internal {
  final private[internal] val CONTINUATION = 0x00
  final private[internal] val TEXT         = 0x01
  final private[internal] val BINARY       = 0x02
  final private[internal] val CLOSE        = 0x08
  final private[internal] val PING         = 0x09
  final private[internal] val PONG         = 0x0A

  final private[internal] val MASK = 0x80
}
