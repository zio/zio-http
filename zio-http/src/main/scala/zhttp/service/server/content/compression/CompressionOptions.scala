package zhttp.service.server.content.compression

import io.netty.handler.codec.compression.{StandardCompressionOptions, CompressionOptions => JCompressionOptions}

sealed trait CompressionOptions { self =>
  def toJava: JCompressionOptions = self match {
    case CompressionOptions.GZip    => StandardCompressionOptions.gzip()
    case CompressionOptions.Deflate => StandardCompressionOptions.deflate()
  }
}

object CompressionOptions {
  final case object GZip    extends CompressionOptions
  final case object Deflate extends CompressionOptions

  /**
   * Creates GZip Compression Options
   */
  def gzip: CompressionOptions = GZip

  /**
   * Creates Deflate Compression Options
   */
  def deflate: CompressionOptions = Deflate
}
