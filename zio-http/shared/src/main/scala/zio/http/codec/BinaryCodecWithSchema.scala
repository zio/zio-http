package zio.http.codec

import zio._

import zio.schema._
import zio.schema.codec._

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
 * Configuration for the JSON codec. The configurations are overruled by the
 * annotations that configure the same behavior.
 *
 * @param explicitEmptyCollections
 *   whether to encode empty collections as `[]` or omit the field and decode
 *   the field when it is missing as an empty collection or fail
 * @param explicitNulls
 *   whether to encode empty Options as `null` or omit the field and decode the
 *   field when it is missing to None or fail
 * @param discriminatorSettings
 *   set up how to handle discriminators
 * @param fieldNameFormat
 *   format for the field names
 * @param treatStreamsAsArrays
 *   whether to treat streams as arrays when encoding/decoding
 * @param rejectExtraFields
 *   whether to reject extra fields during decoding
 */
final case class CodecConfig(
  explicitEmptyCollections: JsonCodec.ExplicitConfig = JsonCodec.ExplicitConfig(),
  explicitNulls: JsonCodec.ExplicitConfig = JsonCodec.ExplicitConfig(),
  discriminatorSettings: JsonCodec.DiscriminatorSetting = JsonCodec.DiscriminatorSetting.default,
  fieldNameFormat: NameFormat = NameFormat.Identity,
  treatStreamsAsArrays: Boolean = false,
  rejectExtraFields: Boolean = false,
) {
  val ignoreEmptyCollections: Boolean = explicitEmptyCollections.encoding && explicitEmptyCollections.decoding

  def schemaConfig: JsonCodec.Configuration =
    JsonCodec.Configuration(
      explicitEmptyCollections = explicitEmptyCollections,
      explicitNulls = explicitNulls,
      discriminatorSettings = discriminatorSettings,
      fieldNameFormat = fieldNameFormat,
      treatStreamsAsArrays = treatStreamsAsArrays,
      rejectExtraFields = rejectExtraFields,
    )
}

object CodecConfig {
  @deprecated("Use CodecConfig.apply instead", "3.3.0")
  def apply(
    ignoreEmptyCollections: Boolean,
  ): CodecConfig =
    new CodecConfig(explicitEmptyCollections =
      JsonCodec.ExplicitConfig(
        encoding = !ignoreEmptyCollections,
        decoding = !ignoreEmptyCollections,
      ),
    )

  val ignoreEmptyFields: CodecConfig =
    CodecConfig(
      explicitEmptyCollections = JsonCodec.ExplicitConfig(
        encoding = false,
        decoding = false,
      ),
      explicitNulls = JsonCodec.ExplicitConfig(
        encoding = false,
        decoding = false,
      ),
    )

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

  def ignoringEmptyFields: HandlerAspect[Any, Unit] =
    Middleware.runBefore(setConfig(ignoreEmptyFields))

  private[http] val codecRef: FiberRef[CodecConfig] =
    FiberRef.unsafe.make(defaultConfig)(Unsafe)
}
