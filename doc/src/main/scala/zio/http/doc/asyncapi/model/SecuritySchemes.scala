package zio.http.doc.asyncapi.model

sealed abstract class SecuritySchemes(value: String) {
  override def toString = value
}

object SecuritySchemes {
  final case object USER_PASSWORD         extends SecuritySchemes("userPassword")
  final case object API_KEY               extends SecuritySchemes("apiKey")
  final case object X509                  extends SecuritySchemes("X509")
  final case object SYMMETRIC_ENCRYPTION  extends SecuritySchemes("symmetricEncryption")
  final case object ASYMMETRIC_ENCRYPTION extends SecuritySchemes("asymmetricEncryption")
  final case object HTTP_API_KEY          extends SecuritySchemes("httpApiKey")
  final case object HTTP                  extends SecuritySchemes("http")
  final case object OAUTH2                extends SecuritySchemes("oauth2")
  final case object OPEN_ID_CONNECT       extends SecuritySchemes("openIdConnect")
}
