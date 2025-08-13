package example.auth.bearer.opaque

import zio._
import zio.http._

object AuthenticationClient extends ZIOAppDefault {
  val url = "http://localhost:8080"

  val loginUrl   = URL.decode(s"$url/login").toOption.get
  val profileUrl = URL.decode(s"$url/profile/me").toOption.get
  val logoutUrl  = URL.decode(s"$url/logout").toOption.get

  val program = for {
    client <- ZIO.service[Client]
    token  <- client
      .batched(
        Request
          .post(
            loginUrl,
            Body.fromURLEncodedForm(
              Form(
                FormField.simpleField("username", "john"),
                FormField.simpleField("password", "password123"),
              ),
            ),
          ),
      )
      .flatMap(_.body.asString)

    profileBody <- client
      .batched(Request.get(profileUrl).addHeader(Header.Authorization.Bearer(token)))
      .flatMap(_.body.asString)
    _           <- ZIO.debug(s"Protected route response: $profileBody")

    _          <- ZIO.debug("Logging out...")
    logoutBody <- client
      .batched(Request.post(logoutUrl, Body.empty).addHeader(Header.Authorization.Bearer(token)))
      .flatMap(_.body.asString)
    _          <- ZIO.debug(s"Logout response: $logoutBody")

    _    <- ZIO.debug("Trying to access protected route after logout...")
    res  <- client
      .batched(Request.get(profileUrl).addHeader(Header.Authorization.Bearer(token)))
    body <- res.body.asString
    _    <- ZIO.debug(s"Protected route response after logout: $body")

  } yield ()

  override val run = program.provide(Client.default)

}
