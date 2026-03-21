/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

sealed trait CompressionOptions {
  val name: String
}

object CompressionOptions {

  final case class GZip(cfg: DeflateConfig)    extends CompressionOptions { val name = "gzip"    }
  final case class Deflate(cfg: DeflateConfig) extends CompressionOptions { val name = "deflate" }
  final case class Brotli(cfg: BrotliConfig)   extends CompressionOptions { val name = "brotli"  }

  /**
   * @param level
   *   defines compression level, {@code 1} yields the fastest compression and
   *   {@code 9} yields the best compression. {@code 0} means no compression.
   * @param bits
   *   defines windowBits, The base two logarithm of the size of the history
   *   buffer. The value should be in the range {@code 9} to {@code 15}
   *   inclusive. Larger values result in better compression at the expense of
   *   memory usage
   * @param mem
   *   defines memlevel, How much memory should be allocated for the internal
   *   compression state. {@code 1} uses minimum memory and {@code 9} uses
   *   maximum memory. Larger values result in better and faster compression at
   *   the expense of memory usage
   */
  final case class DeflateConfig(
    level: Int,
    bits: Int,
    mem: Int,
  )

  object DeflateConfig {
    val DefaultLevel = 6
    val DefaultBits  = 15
    val DefaultMem   = 8
  }

  final case class BrotliConfig(
    quality: Int,
    lgwin: Int,
    mode: Mode,
  )

  object BrotliConfig {
    val DefaultQuality = 4
    val DefaultLgwin   = -1
    val DefaultMode    = Mode.Text
  }

  sealed trait Mode
  object Mode {
    case object Generic extends Mode
    case object Text    extends Mode
    case object Font    extends Mode

    def fromString(s: String): Mode = s.toLowerCase match {
      case "generic" => Generic
      case "text"    => Text
      case "font"    => Font
      case _         => Text
    }
  }

  /** Creates GZip CompressionOptions with default settings. */
  def gzip(
    level: Int = DeflateConfig.DefaultLevel,
    bits: Int = DeflateConfig.DefaultBits,
    mem: Int = DeflateConfig.DefaultMem,
  ): CompressionOptions =
    CompressionOptions.GZip(DeflateConfig(level, bits, mem))

  /** Creates Deflate CompressionOptions with default settings. */
  def deflate(
    level: Int = DeflateConfig.DefaultLevel,
    bits: Int = DeflateConfig.DefaultBits,
    mem: Int = DeflateConfig.DefaultMem,
  ): CompressionOptions =
    CompressionOptions.Deflate(DeflateConfig(level, bits, mem))

  /** Creates Brotli CompressionOptions with default settings. */
  def brotli(
    quality: Int = BrotliConfig.DefaultQuality,
    lgwin: Int = BrotliConfig.DefaultLgwin,
    mode: Mode = BrotliConfig.DefaultMode,
  ): CompressionOptions =
    CompressionOptions.Brotli(BrotliConfig(quality, lgwin, mode))

  def config: zio.Config[CompressionOptions] =
    (
      (zio.Config.int("level").withDefault(DeflateConfig.DefaultLevel) ++
        zio.Config.int("bits").withDefault(DeflateConfig.DefaultBits) ++
        zio.Config.int("mem").withDefault(DeflateConfig.DefaultMem)) ++
        zio.Config.int("quantity").withDefault(BrotliConfig.DefaultQuality) ++
        zio.Config.int("lgwin").withDefault(BrotliConfig.DefaultLgwin) ++
        zio.Config.string("mode").map(Mode.fromString).withDefault(BrotliConfig.DefaultMode) ++
        zio.Config.string("type")
    ).map { case (level, bits, mem, quantity, lgwin, mode, typ) =>
      typ.toLowerCase match {
        case "gzip"    => gzip(level, bits, mem)
        case "deflate" => deflate(level, bits, mem)
        case "brotli"  => brotli(quantity, lgwin, mode)
      }
    }
}
