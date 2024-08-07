package zio.http.gen.openapi

final case class Config(
  commonFieldsOnSuperType: Boolean /*
   * oneOf expressions in openapi result in sealed traits in generated scala code.
   * if this flag is set to true, and all oneOf's "sub types" are defined in terms of
   * an allOf expression, and all share same object(s) included in the allOf expression,
   * then the common fields from that shared object(s) will result in abstract fields
   * defined on the sealed trait.
   */,
  generateSafeTypeAliases: Boolean, /*
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
   */
)
object Config {

  val default: Config = Config(
    commonFieldsOnSuperType = false,
    generateSafeTypeAliases = false,
  )

  lazy val config: zio.Config[Config] = {
    val c =
      zio.Config.boolean("common-fields-on-super-type").withDefault(Config.default.commonFieldsOnSuperType) ++
        zio.Config.boolean("generate-safe-type-aliases").withDefault(Config.default.generateSafeTypeAliases)

    c.map { case (a, b) => Config(a, b) }
  }
}
