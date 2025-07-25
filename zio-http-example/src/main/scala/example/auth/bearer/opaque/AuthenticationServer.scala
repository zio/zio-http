package example.auth.bearer.opaque

import zio._
import zio.http._

import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.io.Source

/**
 * Service responsible for managing token lifecycle including creation, storage,
 * validation, and cleanup of bearer tokens.
 */
trait TokenService {
  def create(username: String, lifeTime: Duration): UIO[String]
  def validate(token: String): UIO[Option[String]]
  def cleanup(): UIO[Unit]
  def revoke(username: String): UIO[Unit]
}

object TokenService {
  case class TokenInfo(username: String, expiresAt: Instant)

  /**
   * In-memory implementation of TokenService using ZIO Ref. In production,
   * consider using a distributed cache like Redis.
   */
  class InmemoryTokenService(tokenStorage: Ref[Map[String, TokenInfo]]) extends TokenService {

    override def create(username: String, lifeTime: Duration): UIO[String] =
      ZIO.randomWith(_.nextUUID).map(_.toString.replace("-", "")).flatMap { token =>
        tokenStorage.update {
          _ + (token ->
            TokenInfo(username = username, expiresAt = Instant.now().plusSeconds(lifeTime.toSeconds)))
        }.as(token)
      }

    override def validate(token: String): UIO[Option[String]] = {
      tokenStorage.modify { tokens =>
        tokens.get(token) match {
          case Some(tokenInfo) if tokenInfo.expiresAt.isAfter(Instant.now()) =>
            (Some(tokenInfo.username), tokens)
          case Some(_)                                                       =>
            // Token expired, remove it
            (None, tokens - token)
          case None                                                          =>
            (None, tokens)
        }
      }
    }

    override def cleanup(): UIO[Unit] =
      tokenStorage.update(_.filter { case (_, tokenInfo) =>
        tokenInfo.expiresAt.isAfter(Instant.now())
      })

    override def revoke(username: String): UIO[Unit] =
      tokenStorage.update(_.filter { case (_, tokenInfo) =>
        tokenInfo.username != username
      })
  }

  val inmemory: ULayer[TokenService] =
    ZLayer.fromZIO(
      Ref.make(Map.empty[String, TokenInfo]).map(new InmemoryTokenService(_)),
    )
}

/**
 * This is an example to demonstrate bearer Authentication middleware using
 * opaque tokens. The Server has 3 routes: login, protected profile, and logout.
 * AuthenticationClient example can be used to make requests to this server.
 */
object AuthenticationServer extends ZIOAppDefault {

  private val TOKEN_VALIDITY_SECONDS = 300.seconds

  val authenticate: HandlerAspect[TokenService, String] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) =>
          ZIO.serviceWithZIO[TokenService](_.validate(token.stringValue)).flatMap {
            case Some(username) =>
              ZIO.succeed((request, username))
            case None           =>
              ZIO.fail(Response.unauthorized("Invalid or expired token!"))
          }
        case _                                        =>
          ZIO.fail(
            Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))),
          )
      }
    })

  def routes: Routes[TokenService, Response] =
    Routes(

      Method.GET / Root -> handler { (_: Request) =>
        for {
          html <- loadHtmlFromResources("/opaque-token-client.html").orDie
        } yield Response(
          status = Status.Ok,
          headers = Headers(Header.ContentType(MediaType.text.html)),
          body = Body.fromString(html),
        )
      },

      // A route that is accessible only via a valid token
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[String](name => Response.text(s"Welcome $name!"))
      } @@ authenticate,

      // A login route that is successful only if the password is the reverse of the username
      Method.POST / "login"   ->
        handler { (request: Request) =>
          val form = request.body.asURLEncodedForm.orElseFail(Response.badRequest)
          for {
            username     <- form
              .map(_.get("username"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing username field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing username value!")))
            password     <- form
              .map(_.get("password"))
              .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing password field!")))
              .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing password value!")))
            tokenService <- ZIO.service[TokenService]
            token        <- tokenService.create(username, TOKEN_VALIDITY_SECONDS)
            _            <- tokenService.cleanup() // Clean up expired tokens
          } yield
            if (password.reverse.hashCode == username.hashCode)
              Response.text(token)
            else
              Response.unauthorized("Invalid username or password.")
        },
      Method.POST / "logout" ->
        handler { (request: Request) =>
          request.header(Header.Authorization) match {
            case Some(Header.Authorization.Bearer(token)) =>
              ZIO.serviceWithZIO[TokenService](_.validate(token.stringValue)).flatMap {
                case Some(username) =>
                  ZIO.service[TokenService].flatMap(_.revoke(username)).as(Response.text(username))
                case None           =>
                  ZIO.fail(Response.unauthorized("Invalid or expired token!"))
              }
            case _                                        =>
              ZIO.fail(
                Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))),
              )
          }
        },
    ) @@ Middleware.debug

  override val run =
    Server
      .serve(routes)
      .provide(
        Server.default,
        TokenService.inmemory,
      )


  /**
   * Loads HTML content from the resources directory
   */
  def loadHtmlFromResources(resourcePath: String): ZIO[Any, Throwable, String] = {
    ZIO.attempt {
      val inputStream = getClass.getResourceAsStream(resourcePath)
      if (inputStream == null) throw new RuntimeException(s"Resource not found: $resourcePath")

      val source = Source.fromInputStream(inputStream, StandardCharsets.UTF_8.name())
      try source.mkString
      finally {
        source.close()
        inputStream.close()
      }
    }
  }

}
