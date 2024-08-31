package zio.http.gen.openapi

import zio.config.ConfigOps

import zio.http.gen.openapi.Config.NormalizeFields

final case class Config(
  commonFieldsOnSuperType: Boolean /*
   * oneOf expressions in openapi result in sealed traits in generated scala code.
   * if this flag is set to true, and all oneOf's "sub types" are defined in terms of
   * an allOf expression, and all share same object(s) included in the allOf expression,
   * then the common fields from that shared object(s) will result in abstract fields
   * defined on the sealed trait.
   */,
  generateSafeTypeAliases: Boolean /*
   * Referencing primitives, and giving them a name makes the openapi spec more readable.
   * By default, the generated scala code will resolve the referenced name,
   * and replace it with the primitive type.
   * By setting this flag to true, the generator will create components with zio.prelude Newtype
   * definitions wrapping over the aliased primitive type.
   *
   * Note: only aliased primitives are supported for now.
   *
   * TODO: in the future we can consider an enum instead of boolean for different aliased types.
   *       e.g: scala 3 opaque types, neotype, prelude's newtype, etc'…
   */,
  fieldsNormalizationConf: NormalizeFields, /*
   * OpenAPI can declare fields that have unconventional "casing" in scala,
   * like snake_case, or kebab-case.
   * This configuration allows to normalize these fields.
   * The original casing will be preserved via a @fieldName("<original-name>") annotation.
   */
)
object Config {

  final case class NormalizeFields(
    enabled: Boolean /*
     * If enabled, the generator will attempt to normalize field names to camelCase,
     * unless original field is defined in the specialReplacements map.
     */,
    specialReplacements: Map[String, String], /*
     * When normalization is enabled, a heuristic parser will attempt to normalize field names.
     * But this is not always possible, or may not yield the desired result.
     * Consider field names that are defined in the JSON as `"1st"`, `"2nd"`, or `"3rd"`:
     * You may want to override auto normalization in this case and provide a map like: {{{
     * Map(
     *   "1st" -> "first",
     *   "2nd" -> "second",
     *   "3rd" -> "third"
     * )
     * }}}
     */
  )
  object NormalizeFields {
    lazy val config: zio.Config[NormalizeFields] = (
      zio.Config.boolean("enabled").withDefault(Config.default.fieldsNormalizationConf.enabled) ++
        zio.Config
          .table("special-replacements", zio.Config.string)
          .withDefault(Config.default.fieldsNormalizationConf.specialReplacements)
    ).to[NormalizeFields]
  }

  val default: Config = Config(
    commonFieldsOnSuperType = false,
    generateSafeTypeAliases = false,
    fieldsNormalizationConf = NormalizeFields(
      enabled = false,
      specialReplacements = Map.empty,
    ),
  )

  lazy val config: zio.Config[Config] = (
    zio.Config.boolean("common-fields-on-super-type").withDefault(Config.default.commonFieldsOnSuperType) ++
      zio.Config.boolean("generate-safe-type-aliases").withDefault(Config.default.generateSafeTypeAliases) ++
      NormalizeFields.config.nested("fields-normalization")
  ).to[Config]
}
