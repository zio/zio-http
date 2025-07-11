package example.auth.bearer.jwt.asymmetric

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zio._
import zio.http._

import java.security.{KeyPairGenerator, PrivateKey, PublicKey}
import java.time.Clock
import scala.util.Try

/**
 * This is an example to demonstrate bearer Authentication middleware. The
 * Server has 2 routes. The first one is for login, Upon a successful login, it
 * will return a jwt token for accessing protected routes. The second route is a
 * protected route that is accessible only if the request has a valid jwt token.
 * AuthenticationClient example can be used to makes requests to this server.
 */
object AuthenticationServer extends ZIOAppDefault {
  implicit val clock: Clock = Clock.systemUTC


  // Generate the RSA key pair programmatically
  private def generateRSAKeyPair(): (PrivateKey, PublicKey) = {
    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048) // 2048-bit key size
    val keyPair = keyGen.generateKeyPair()
    (keyPair.getPrivate, keyPair.getPublic)
  }

  // Generate keys once and reuse
  private val (generatedPrivateKey, generatedPublicKey) = generateRSAKeyPair()

  // Secret Authentication key
  val SECRET_KEY = "secretKey"
  val PRIVATE_KEY: PrivateKey = generatedPrivateKey
  val PUBLIC_KEY: PublicKey = generatedPublicKey

  def jwtEncode(username: String): String =
    Jwt.encode(
      claim =
      JwtClaim(subject = Some(username)).issuedNow.expiresIn(300),
      key = PRIVATE_KEY,
      algorithm = pdi.jwt.JwtAlgorithm.RS256
    )

  def jwtDecode(token: String): Try[JwtClaim] =
    Jwt.decode(token = token, key = PUBLIC_KEY, algorithms = Seq(JwtAlgorithm.RS256))

  val bearerAuthWithContext: HandlerAspect[Any, String] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) =>
          ZIO
            .fromTry(jwtDecode(token.value.asString))
            .orElseFail(Response.badRequest("Invalid or expired token!"))
            .flatMap(claim => ZIO.fromOption(claim.subject).orElseFail(Response.badRequest("Missing subject claim!")))
            .map(u => (request, u))

        case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))))
      }
    })

  def routes: Routes[Any, Response] =
    Routes(
      // A route that is accessible only via a jwt token
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[String](name => Response.text(s"Welcome $name!"))
      } @@ bearerAuthWithContext,

      // A login route that is successful only if the password is the reverse of the username
      Method.GET / "login" ->
        handler { (request: Request) =>
          val form = request.body.asMultipartForm.orElseFail(Response.badRequest)
          for {
            username <- form
              .map(_.get("username"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing username field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing username value!")))
            password <- form
              .map(_.get("password"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing password field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing password value!")))
          } yield
            if (password.reverse.hashCode == username.hashCode)
              Response.text(jwtEncode(username))
            else
              Response.unauthorized("Invalid username or password.")
        },
    ) @@ Middleware.debug

  override val run = Server.serve(routes).provide(Server.default)
}
