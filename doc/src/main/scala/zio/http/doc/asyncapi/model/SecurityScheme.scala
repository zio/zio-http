package zio.http.doc.asyncapi.model

sealed abstract class SecurityScheme(value: String) {
  override def toString = value
}

object SecurityScheme {
  final case object USER_PASSWORD         extends SecurityScheme("userPassword")
  final case object API_KEY               extends SecurityScheme("apiKey")
  final case object X509                  extends SecurityScheme("X509")
  final case object SYMMETRIC_ENCRYPTION  extends SecurityScheme("symmetricEncryption")
  final case object ASYMMETRIC_ENCRYPTION extends SecurityScheme("asymmetricEncryption")
  final case object HTTP_API_KEY          extends SecurityScheme("httpApiKey")
  final case object HTTP                  extends SecurityScheme("http")
  final case object OAUTH2                extends SecurityScheme("oauth2")
  final case object OPEN_ID_CONNECT       extends SecurityScheme("openIdConnect")
}
