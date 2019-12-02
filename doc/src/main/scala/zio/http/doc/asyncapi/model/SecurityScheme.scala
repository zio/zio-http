package zio.http.doc.asyncapi.model

/*
  Security schema for operations
 */
sealed abstract class SecurityScheme(value: String) {
  override def toString = value
}

object SecurityScheme {
  final case object UserPassword         extends SecurityScheme("userPassword")
  final case object ApiKey               extends SecurityScheme("apiKey")
  final case object X509                 extends SecurityScheme("X509")
  final case object SymmetricEncryption  extends SecurityScheme("symmetricEncryption")
  final case object AsymmetricEncryption extends SecurityScheme("asymmetricEncryption")
  final case object HttpApiKey           extends SecurityScheme("httpApiKey")
  final case object Http                 extends SecurityScheme("http")
  final case object OAuth2               extends SecurityScheme("oauth2")
  final case object OpenIdConnect        extends SecurityScheme("openIdConnect")
}
