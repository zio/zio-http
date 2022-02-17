package zhttp.service.server.content.compression

import io.netty.handler.codec.compression.{CompressionOptions => JCompressionOptions, StandardCompressionOptions}

sealed trait CompressionOptions { self =>
  def toJava: JCompressionOptions = self match {
    case CompressionOptions.GZip(cl, wb, ml)    => StandardCompressionOptions.gzip(cl, wb, ml)
    case CompressionOptions.Deflate(cl, wb, ml) => StandardCompressionOptions.deflate(cl, wb, ml)
  }
}

object CompressionOptions {
  private final case class GZip(compressionLevel: Int, windowBits: Int, memLevel: Int)    extends CompressionOptions
  private final case class Deflate(compressionLevel: Int, windowBits: Int, memLevel: Int) extends CompressionOptions

  /**
   * Creates GZip Compression Options. Defines defaults as per
   * io.netty.handler.codec.compression.GzipOptions#DEFAULT
   */
  def gzip(compressionLevel: Int = 6, windowBits: Int = 15, memLevel: Int = 8): CompressionOptions =
    GZip(compressionLevel, windowBits, memLevel)

  /**
   * Creates Deflate Compression Options. Defines defaults as per
   * io.netty.handler.codec.compression.DeflateOptions#DEFAULT
   */
  def deflate(compressionLevel: Int = 6, windowBits: Int = 15, memLevel: Int = 8): CompressionOptions =
    Deflate(compressionLevel, windowBits, memLevel)
}
