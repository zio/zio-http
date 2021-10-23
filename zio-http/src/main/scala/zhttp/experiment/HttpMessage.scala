package zhttp.experiment

case class HttpMessage[A](private val a: A, private val bool: Boolean) { self =>
  private[zhttp] def raw: A                        = self.a
  def data(implicit ev: ConvertibleToChunkByte[A]) = ev.toChunkByte(self.a)
  def isLast: Boolean                              = self.bool
}
