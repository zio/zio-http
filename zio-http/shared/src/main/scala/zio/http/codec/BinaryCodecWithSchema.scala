package zio.http.codec

import zio._

import zio.schema._
import zio.schema.codec._

import zio.http.{HandlerAspect, Middleware}

/**
 * A `BinaryCodec` for `A`, built from a [[CodecConfig]] by `codecFn`, together
 * with the `Schema` of `A`. An [[HttpContentCodec]] maps each media type it
 * supports to one of these.
 */
final case class BinaryCodecWithSchema[A](codecFn: CodecConfig => BinaryCodec[A], schema: Schema[A]) {
  private var cached = Map.empty[CodecConfig, BinaryCodec[A]]

  /**
   * Returns the codec for `config`. The codec built for each config is cached
   * and reused.
   */
  def codec(config: CodecConfig): BinaryCodec[A] =
    cached.getOrElse(
      config, {
        val codec = codecFn(config)
        cached += (config -> codec)
        codec
      },
    )
}

/** Constructors of [[BinaryCodecWithSchema]]. */
object BinaryCodecWithSchema {

  /**
   * Creates a [[BinaryCodecWithSchema]] that uses `codec` for every
   * [[CodecConfig]].
   */
  def apply[A](codec: BinaryCodec[A], schema: Schema[A]): BinaryCodecWithSchema[A] =
    BinaryCodecWithSchema(_ => codec, schema)

  /**
   * Creates a [[BinaryCodecWithSchema]] from a function that builds the codec
   * for a [[CodecConfig]], and the implicit schema.
   */
  def fromBinaryCodec[A](codec: CodecConfig => BinaryCodec[A])(implicit
    schema: Schema[A],
  ): BinaryCodecWithSchema[A] =
    BinaryCodecWithSchema(codec, schema)

  /**
   * Creates a [[BinaryCodecWithSchema]] that uses `codec` for every
   * [[CodecConfig]], with the implicit schema.
   */
  def fromBinaryCodec[A](codec: BinaryCodec[A])(implicit schema: Schema[A]): BinaryCodecWithSchema[A] =
    BinaryCodecWithSchema(_ => codec, schema)
}

/**
 * Configuration for the JSON codec. The configurations are overruled by the
 * annotations that configure the same behavior.
 *
 * Endpoint handlers and endpoint clients read the configuration of the current
 * fiber, which `CodecConfig.setConfig`, `CodecConfig.configLayer` and
 * `CodecConfig.withConfig` change. The default is `CodecConfig.defaultConfig`.
 *
 * {{{
 * import zio.http._
 * import zio.http.codec._
 *
 * val config: CodecConfig = CodecConfig(rejectExtraFields = true)
 *
 * val routes: Routes[Any, Response] = Routes(Method.GET / "ping" -> Handler.ok)
 * val strict: Routes[Any, Response] = routes @@ CodecConfig.withConfig(config)
 * }}}
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

  /**
   * Returns `true` when `explicitEmptyCollections` is set for both encoding and
   * decoding. With the default configuration it is `false`.
   */
  val ignoreEmptyCollections: Boolean = explicitEmptyCollections.encoding && explicitEmptyCollections.decoding

  /**
   * Returns this configuration as the `JsonCodec.Configuration` of ZIO Schema.
   */
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

/**
 * Predefined configurations, and ways to set the configuration of a fiber, a
 * layer or routes.
 */
object CodecConfig {

  /**
   * Creates a configuration whose `explicitEmptyCollections` is the opposite of
   * `ignoreEmptyCollections`, for both encoding and decoding.
   */
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

  /**
   * A configuration that omits empty collections and `None` values when
   * encoding, and accepts them missing when decoding. For example a field
   * `xs: List[Int]` set to `Nil` and a field `o: Option[Int]` set to `None` are
   * encoded as `{}`, instead of `{"xs":[],"o":null}` with `defaultConfig`.
   */
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

  /** The configuration with every parameter at its default value. */
  val defaultConfig: CodecConfig = CodecConfig()

  /** Sets the configuration of the current fiber to `config`. */
  def setConfig(config: CodecConfig): ZIO[Any, Nothing, Unit] =
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (state, _) =>
      val existing = state.getFiberRef(codecRef)
      if (existing != config) state.setFiberRef(codecRef, config)
      Exit.unit
    }

  /**
   * A layer that sets the configuration to `config` when it is built, for
   * example in the `bootstrap` of a `ZIOAppDefault`.
   */
  def configLayer(config: CodecConfig): ULayer[Unit] =
    ZLayer(setConfig(config))

  /**
   * A middleware that sets the configuration to `config` before each request is
   * handled.
   */
  def withConfig(config: CodecConfig): HandlerAspect[Any, Unit] =
    Middleware.runBefore(setConfig(config))

  /**
   * A middleware that sets the configuration to `ignoreEmptyFields` before each
   * request is handled.
   */
  def ignoringEmptyFields: HandlerAspect[Any, Unit] =
    Middleware.runBefore(setConfig(ignoreEmptyFields))

  private[http] val codecRef: FiberRef[CodecConfig] =
    FiberRef.unsafe.make(defaultConfig)(Unsafe)
}
