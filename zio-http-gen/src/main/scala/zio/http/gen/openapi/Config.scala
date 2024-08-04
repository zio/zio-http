package zio.http.gen.openapi

final case class Config(
  commonFieldsOnSuperType: Boolean,
  generateSafeTypeAliases: Boolean, // in the future we can consider enum instead of boolean for different aliased types
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
