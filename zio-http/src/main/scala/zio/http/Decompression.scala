package zio.http

sealed trait Decompression {
  def enabled: Boolean
  def strict: Boolean
}

object Decompression {
  case object No        extends Decompression {
    override def enabled: Boolean = false
    override def strict: Boolean  = false
  }
  case object Strict    extends Decompression {
    override def enabled: Boolean = true
    override def strict: Boolean  = true
  }
  case object NonStrict extends Decompression {
    override def enabled: Boolean = true
    override def strict: Boolean  = false
  }
}
