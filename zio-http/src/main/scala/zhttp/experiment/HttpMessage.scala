package zhttp.experiment

case class HttpMessage[A](private val a: A, private val bool: Boolean) { self =>
  def raw: A = self.a //todo: Make this package private after converting multipart decoder to return stream
  def data(implicit ev: ConvertibleToChunkByte[A]) = ev.toChunkByte(self.a)
  def isLast: Boolean                              = self.bool
}
