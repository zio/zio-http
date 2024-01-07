package zio.http

import zio.Config.Secret

final case class Credentials(uname: String, upassword: Secret)
