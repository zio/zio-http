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
  def gzip: CompressionOptions =
    GZip(compressionLevel = 6, windowBits = 15, memLevel = 8)

  def gzip(compressionLevel: Int, windowBits: Int, memLevel: Int): CompressionOptions =
    GZip(compressionLevel, windowBits, memLevel)

  /**
   * Creates Deflate Compression Options. Defines defaults as per
   * io.netty.handler.codec.compression.DeflateOptions#DEFAULT
   */
  def deflate: CompressionOptions =
    Deflate(compressionLevel = 6, windowBits = 15, memLevel = 8)

  def deflate(compressionLevel: Int, windowBits: Int, memLevel: Int): CompressionOptions =
    Deflate(compressionLevel, windowBits, memLevel)
}
