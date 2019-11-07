package zio.http.authentication

sealed trait AuthenticationScheme
case object Basic       extends AuthenticationScheme
case object Bearer      extends AuthenticationScheme
case object Digest      extends AuthenticationScheme
case object Hoba        extends AuthenticationScheme
case object Mutual      extends AuthenticationScheme
case object Negotiate   extends AuthenticationScheme
case object OAuth       extends AuthenticationScheme
case object ScramSha1   extends AuthenticationScheme
case object ScramSha256 extends AuthenticationScheme
case object Vapid       extends AuthenticationScheme
