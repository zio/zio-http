package zio.http.codec

import zio._

import zio.schema.Schema
import zio.schema.codec.BinaryCodec

import zio.http.{HandlerAspect, Middleware}

final case class BinaryCodecWithSchema[A](codecFn: CodecConfig => BinaryCodec[A], schema: Schema[A]) {
  private var cached                             = Map.empty[CodecConfig, BinaryCodec[A]]
  def codec(config: CodecConfig): BinaryCodec[A] =
    cached.getOrElse(
      config, {
        val codec = codecFn(config)
        cached += (config -> codec)
        codec
      },
    )
}

object BinaryCodecWithSchema {
  def apply[A](codec: BinaryCodec[A], schema: Schema[A]): BinaryCodecWithSchema[A] =
    BinaryCodecWithSchema(_ => codec, schema)

  def fromBinaryCodec[A](codec: CodecConfig => BinaryCodec[A])(implicit
    schema: Schema[A],
  ): BinaryCodecWithSchema[A] =
    BinaryCodecWithSchema(codec, schema)

  def fromBinaryCodec[A](codec: BinaryCodec[A])(implicit schema: Schema[A]): BinaryCodecWithSchema[A] =
    BinaryCodecWithSchema(_ => codec, schema)
}

/**
 * Configuration that is handed over when creating a binary codec
 * @param ignoreEmptyCollections
 *   if true, empty collections will be ignored when encoding. This is currently
 *   only used for the JSON codec
 */

final case class CodecConfig(
  ignoreEmptyCollections: Boolean = true,
)

object CodecConfig {
  val defaultConfig: CodecConfig = CodecConfig()

  def setConfig(config: CodecConfig): ZIO[Any, Nothing, Unit] =
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (state, _) =>
      val existing = state.getFiberRef(codecRef)
      if (existing != config) state.setFiberRef(codecRef, config)
      Exit.unit
    }

  def configLayer(config: CodecConfig): ULayer[Unit] =
    ZLayer(setConfig(config))

  def withConfig(config: CodecConfig): HandlerAspect[Any, Unit] =
    Middleware.runBefore(setConfig(config))

  private[http] val codecRef: FiberRef[CodecConfig] =
    FiberRef.unsafe.make(defaultConfig)(Unsafe)
}
