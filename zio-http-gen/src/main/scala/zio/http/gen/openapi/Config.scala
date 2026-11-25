package zio.http.gen.openapi

import zio.config.ConfigOps

import zio.http.gen.openapi.Config.NormalizeFields

// format: off
/**
 * Options that control how `EndpointGen` turns an OpenAPI document into Scala
 * code. `Config.default` turns every option off.
 *
 * @param commonFieldsOnSuperType oneOf expressions in openapi result in sealed traits in generated scala code.
 *                                if this flag is set to true, and all oneOf's "subtypes" are defined in terms of
 *                                an allOf expression, and all share same object(s) included in the allOf expression,
 *                                then the common fields from that shared object(s) will result in abstract fields
 *                                defined on the sealed trait.
 *
 * @param generateSafeTypeAliases Referencing primitives, and giving them a name makes the openapi spec more readable.
 *                                By default, the generated scala code will resolve the referenced name,
 *                                and replace it with the primitive type.
 *                                By setting this flag to true, the generator will create components with zio.prelude Newtype
 *                                definitions wrapping over the aliased primitive type.
 *
 *                                Note: only aliased primitives are supported for now.
 *
 *                                TODO: in the future we can consider an enum instead of boolean for different aliased types.
 *                                      e.g: scala 3 opaque types, neotype, prelude's newtype, etc'…
 *
 * @param fieldNamesNormalization OpenAPI can declare fields that have unconventional "casing" in scala,
 *                                like snake_case, or kebab-case.
 *                                This configuration allows to normalize these fields.
 *                                The original casing will be preserved via a @fieldName("<original-name>") annotation.
 *
 * @param stringFormatTypes Maps the `format` of a string schema to the name of the Scala type to use for such a field.
 *                          The type is referenced by name and no import is generated for it.
 *                          An entry takes precedence over the built-in handling of the formats `uuid`, `date`, `date-time`, `time` and `duration`.
 */
// format: on
final case class Config(
  commonFieldsOnSuperType: Boolean,
  generateSafeTypeAliases: Boolean,
  fieldNamesNormalization: NormalizeFields,
  stringFormatTypes: Map[String, String],
)
/**
 * The default options and the `zio.Config` descriptors of `Config`.
 */
object Config {

  // format: off
  /**
   * Options that control how field names of an OpenAPI document become Scala field names.
   *
   * @param enableAutomatic If enabled, the generator will attempt to normalize field names to camelCase,
   *                        unless the original field name is a key of the manualOverrides map.
   *
   * @param manualOverrides Maps an original field name to the Scala name to use for it. An entry applies whether or not enableAutomatic is set.
   *                        When normalization is enabled, a heuristic parser will attempt to normalize the other field names.
   *                        But this is not always possible, or may not yield the desired result.
   *                        Consider field names that are defined in the JSON as `"1st"`, `"2nd"`, or `"3rd"`:
   *                        You may want to override auto normalization in this case and provide a map like: {{{
   *                        Map(
   *                          "1st" -> "first",
   *                          "2nd" -> "second",
   *                          "3rd" -> "third"
   *                        )
   *                        }}}
   */
  // format: on
  final case class NormalizeFields(
    enableAutomatic: Boolean,
    manualOverrides: Map[String, String],
  )
  /**
   * The `zio.Config` descriptor of `NormalizeFields`.
   */
  object NormalizeFields {
    /**
     * A `zio.Config` that reads `NormalizeFields` from the key `enabled` and the table `special-replacements`.
     *
     * A missing `enabled` defaults to `false` and a missing `special-replacements` to an empty map.
     */
    lazy val config: zio.Config[NormalizeFields] = (
      zio.Config.boolean("enabled").withDefault(Config.default.fieldNamesNormalization.enableAutomatic) ++
        zio.Config
          .table("special-replacements", zio.Config.string)
          .withDefault(Config.default.fieldNamesNormalization.manualOverrides)
    ).to[NormalizeFields]
  }

  /**
   * The default options: both flags are `false`, and there are no manual overrides and no string format types.
   */
  val default: Config = Config(
    commonFieldsOnSuperType = false,
    generateSafeTypeAliases = false,
    fieldNamesNormalization = NormalizeFields(
      enableAutomatic = false,
      manualOverrides = Map.empty,
    ),
    stringFormatTypes = Map.empty,
  )

  /**
   * A `zio.Config` that reads a `Config`.
   *
   * It reads the keys `common-fields-on-super-type` and `generate-safe-type-aliases`, the nested `fields-normalization` (with the keys `enabled` and `special-replacements`) and the table `string-format-types`. A missing key takes its value from `Config.default`.
   */
  def config: zio.Config[Config] = (
    zio.Config.boolean("common-fields-on-super-type").withDefault(Config.default.commonFieldsOnSuperType) ++
      zio.Config.boolean("generate-safe-type-aliases").withDefault(Config.default.generateSafeTypeAliases) ++
      NormalizeFields.config.nested("fields-normalization") ++ zio.Config.table(
        "string-format-types",
        zio.Config.string,
      )
  ).to[Config]
}
