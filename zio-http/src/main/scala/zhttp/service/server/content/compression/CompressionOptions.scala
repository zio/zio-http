package zhttp.service.server.content.compression

import io.netty.handler.codec.compression.{CompressionOptions => JCompressionOptions, StandardCompressionOptions}
import zhttp.service.server.content.compression.CompressionOptions.CompressionType

final case class CompressionOptions(level: Int, bits: Int, mem: Int, kind: CompressionType) { self =>
  def toJava: JCompressionOptions = self.kind match {
    case CompressionOptions.GZip    => StandardCompressionOptions.gzip(self.level, self.bits, self.mem)
    case CompressionOptions.Deflate => StandardCompressionOptions.deflate(self.level, self.bits, self.mem)
  }

}

object CompressionOptions {
  sealed trait CompressionType
  private case object GZip    extends CompressionType
  private case object Deflate extends CompressionType

  /**
   * Creates GZip CompressionOptions. Defines defaults as per
   * io.netty.handler.codec.compression.GzipOptions#DEFAULT
   */
  def gzip: CompressionOptions = CompressionOptions(6, 15, 8, GZip)

  /**
   * Creates GZip CompressionOptions with parameters.
   */
  def gzip(level: Int, bits: Int, mem: Int): CompressionOptions = CompressionOptions(level, bits, mem, GZip)

  /**
   * Creates Deflate CompressionOptions. Defines defaults as per
   * io.netty.handler.codec.compression.DeflateOptions#DEFAULT
   */
  def deflate: CompressionOptions = CompressionOptions(6, 15, 8, Deflate)

  /**
   * Creates Deflate CompressionOptions with parameters.
   */
  def deflate(level: Int, bits: Int, mem: Int): CompressionOptions = CompressionOptions(level, bits, mem, Deflate)
}
