package zhttp.service.server.content.compression

import io.netty.handler.codec.compression.{CompressionOptions => JCompressionOptions, StandardCompressionOptions}
import zhttp.service.server.content.compression.CompressionOptions.Kind

final case class CompressionOptions(level: Int, bits: Int, mem: Int, kind: Kind) { self =>
  def toJava: JCompressionOptions = self.kind match {
    case CompressionOptions.GZip    => StandardCompressionOptions.gzip(self.level, self.bits, self.mem)
    case CompressionOptions.Deflate => StandardCompressionOptions.deflate(self.level, self.bits, self.mem)
  }

}

object CompressionOptions {
  sealed trait Kind
  private case object GZip    extends Kind
  private case object Deflate extends Kind

  /**
   * Creates GZip Compression Options. Defines defaults as per
   * io.netty.handler.codec.compression.GzipOptions#DEFAULT
   */
  def gzip: CompressionOptions = CompressionOptions(6, 15, 8, GZip)

  def gzip(compressionLevel: Int, windowBits: Int, memLevel: Int): CompressionOptions =
    CompressionOptions(compressionLevel, windowBits, memLevel, GZip)

  /**
   * Creates Deflate Compression Options. Defines defaults as per
   * io.netty.handler.codec.compression.DeflateOptions#DEFAULT
   */
  def deflate: CompressionOptions = CompressionOptions(6, 15, 8, Deflate)

  def deflate(compressionLevel: Int, windowBits: Int, memLevel: Int): CompressionOptions =
    CompressionOptions(compressionLevel, windowBits, memLevel, Deflate)

}
