package zio.http.gen.openapi


final case class Config(commonFieldsOnSuperType: Boolean)
object Config {

  val default: Config = Config(
    commonFieldsOnSuperType = false,
  )

  lazy val config: zio.Config[Config] =
    zio.Config.boolean("common-fields-on-super-type")
      .withDefault(Config.default.commonFieldsOnSuperType)
      .map(Config.apply)
}
