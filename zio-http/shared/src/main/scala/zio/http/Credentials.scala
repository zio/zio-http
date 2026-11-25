package zio.http

import zio.Config.Secret

/**
 * A user name and a password. `HandlerAspect.basicAuth` passes it to its check
 * function, and `Proxy` holds it as optional proxy credentials.
 *
 * @param uname
 *   the user name
 * @param upassword
 *   the password
 */
final case class Credentials(uname: String, upassword: Secret)
