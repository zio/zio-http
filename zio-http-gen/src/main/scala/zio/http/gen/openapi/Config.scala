package zio.http.gen.openapi

import zio.config.ConfigOps

import zio.http.gen.openapi.Config.NormalizeFields

// format: off
/**
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
 */
// format: on
final case class Config(
  commonFieldsOnSuperType: Boolean,
  generateSafeTypeAliases: Boolean,
  fieldNamesNormalization: NormalizeFields,
)
object Config {

  // format: off
  /**
   * @param enableAutomatic If enabled, the generator will attempt to normalize field names to camelCase,
   *                        unless original field is defined in the specialReplacements map.
   *
   * @param manualOverrides When normalization is enabled, a heuristic parser will attempt to normalize field names.
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
  object NormalizeFields {
    lazy val config: zio.Config[NormalizeFields] = (
      zio.Config.boolean("enabled").withDefault(Config.default.fieldNamesNormalization.enableAutomatic) ++
        zio.Config
          .table("special-replacements", zio.Config.string)
          .withDefault(Config.default.fieldNamesNormalization.manualOverrides)
    ).to[NormalizeFields]
  }

  val default: Config = Config(
    commonFieldsOnSuperType = false,
    generateSafeTypeAliases = false,
    fieldNamesNormalization = NormalizeFields(
      enableAutomatic = false,
      manualOverrides = Map.empty,
    ),
  )

  lazy val config: zio.Config[Config] = (
    zio.Config.boolean("common-fields-on-super-type").withDefault(Config.default.commonFieldsOnSuperType) ++
      zio.Config.boolean("generate-safe-type-aliases").withDefault(Config.default.generateSafeTypeAliases) ++
      NormalizeFields.config.nested("fields-normalization")
  ).to[Config]
}
