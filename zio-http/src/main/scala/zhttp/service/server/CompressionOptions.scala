package zhttp.service.server

import io.netty.handler.codec.compression.{CompressionOptions => JCompressionOptions, StandardCompressionOptions}

final case class CompressionOptions(level: Int, bits: Int, mem: Int, kind: CompressionOptions.CompressionType) { self =>
  def toJava: JCompressionOptions = self.kind match {
    case CompressionOptions.GZip    => StandardCompressionOptions.gzip(self.level, self.bits, self.mem)
    case CompressionOptions.Deflate => StandardCompressionOptions.deflate(self.level, self.bits, self.mem)
  }
}

object CompressionOptions {
  val Level = 6
  val Bits  = 15
  val Mem   = 8

  /**
   * Creates GZip CompressionOptions. Defines defaults as per
   * io.netty.handler.codec.compression.GzipOptions#DEFAULT
   */
  def gzip(level: Int = Level, bits: Int = Bits, mem: Int = Mem): CompressionOptions =
    CompressionOptions(level, bits, mem, GZip)

  /**
   * Creates Deflate CompressionOptions. Defines defaults as per
   * io.netty.handler.codec.compression.DeflateOptions#DEFAULT
   */
  def deflate(level: Int = Level, bits: Int = Bits, mem: Int = Mem): CompressionOptions =
    CompressionOptions(level, bits, mem, Deflate)

  sealed trait CompressionType
  private case object GZip    extends CompressionType
  private case object Deflate extends CompressionType
}
