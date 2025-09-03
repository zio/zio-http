package example.auth.basic

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}

import zio.Config.Secret
import zio._

import zio.http._

/**
 * This is an example to demonstrate basic Authentication middleware that passes
 * the authenticated username to the handler. The server has routes that can
 * access the authenticated user's information through the context.
 *
 * Enhanced with password hashing for security.
 */
object AuthenticationServer extends ZIOAppDefault {

  case class User(username: String, passwordHash: String, salt: String, email: String, role: String)

  trait UserService {
    def authenticate(username: String, password: Secret): UIO[Option[User]]
  }

  object PasswordHasher {
    private val random = new SecureRandom()

    /**
     * Generates a random salt for password hashing
     */
    def generateSalt(): String = {
      val saltBytes = new Array[Byte](16)
      random.nextBytes(saltBytes)
      saltBytes.map("%02x".format(_)).mkString
    }

    /**
     * Hashes a password with the given salt using SHA-256
     */
    def hashPassword(password: Secret, salt: String): String = {
      val md             = MessageDigest.getInstance("SHA-256")
      val saltedPassword = password.stringValue + salt
      val hashedBytes    = md.digest(saltedPassword.getBytes(StandardCharsets.UTF_8))
      hashedBytes.map("%02x".format(_)).mkString
    }

    /**
     * Verifies a password against a stored hash and salt
     */
    def verifyPassword(password: Secret, storedHash: String, storedSalt: String): Boolean = {
      hashPassword(password, storedSalt) == storedHash
    }

    /**
     * Helper method to create a user with hashed password
     */
    def createUser(username: String, password: String, email: String, role: String): User = {
      val salt         = generateSalt()
      val passwordHash = hashPassword(Secret(password), salt)
      User(username, passwordHash, salt, email, role)
    }
  }

  case class InMemoryUserService(private val users: Ref[Map[String, User]]) extends UserService {
    def authenticate(username: String, password: Secret): UIO[Option[User]] =
      users.get.map(
        _.get(username).filter(user => PasswordHasher.verifyPassword(password, user.passwordHash, user.salt)),
      )
  }

  object InMemoryUserService {
    def make(users: Map[String, User]): UIO[UserService] =
      Ref.make(users).map(new InMemoryUserService(_))

    val live: ZLayer[Any, Nothing, UserService] =
      ZLayer.fromZIO(make(users))
  }

  // Sample user database with hashed passwords
  val users = Map(
    "john"  -> PasswordHasher.createUser("john", "secret123", "john@example.com", "user"),
    "jane"  -> PasswordHasher.createUser("jane", "password456", "jane@example.com", "user"),
    "admin" -> PasswordHasher.createUser("admin", "admin123", "admin@example.com", "admin"),
  )

  // Custom basic auth that passes the full User object to the handler
  val basicAuthWithUserContext: HandlerAspect[UserService, User] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      ZIO.serviceWithZIO[UserService] { userService =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Basic(username, password)) =>
            userService.authenticate(username, password).flatMap {
              case Some(user) =>
                ZIO.succeed((request, user))
              case None       =>
                ZIO.fail(
                  Response
                    .unauthorized("Invalid username or password")
                    .addHeaders(Headers(Header.WWWAuthenticate.Basic(realm = Some("Access")))),
                )

            }
          case _                                                    =>
            ZIO.fail(
              Response
                .unauthorized("Authentication required")
                .addHeaders(Headers(Header.WWWAuthenticate.Basic(realm = Some("Access")))),
            )
        }
      }

    })

  def routes: Routes[UserService, Response] =
    Routes(
      // Serve the web client interface from resources
      Method.GET / Root -> Handler
        .fromResource("basic-auth-client.html")
        .orElse(
          Handler.internalServerError("Failed to load HTML file"),
        ),

      // Public route - no authentication required
      Method.GET / "public" -> handler { (_: Request) =>
        ZIO.succeed(Response.text("This is a public endpoint accessible to everyone"))
      },

      // Route that uses the full User object
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[User](user =>
          Response.text(s"Welcome ${user.username}!\nEmail: ${user.email}\nRole: ${user.role}"),
        )
      } @@ basicAuthWithUserContext,

      // Admin-only route
      Method.GET / "admin" / "users" -> handler { (_: Request) =>
        ZIO.serviceWith[User] { user =>
          if (user.role != "admin")
            Response.forbidden("Admin access required")
          else {
            // List all users for admin (excluding sensitive data)
            val userList = users.values.map(u => s"${u.username} (${u.email}) - Role: ${u.role}").mkString("\n")
            Response.text(s"User List (accessed by ${user.username}):\n$userList")
          }
        }
      } @@ basicAuthWithUserContext,

      // Debug route to show password hashing (for development only)
      Method.GET / "debug" / "hash" -> handler { (_: Request) =>
        ZIO.succeed {
          val testPassword = "testpassword"
          val salt         = PasswordHasher.generateSalt()
          val hash         = PasswordHasher.hashPassword(Secret(testPassword), salt)
          Response.text(s"Password: $testPassword\nSalt: $salt\nHash: $hash")
        }
      },
    ) @@ Middleware.debug

  override val run = Server.serve(routes).provide(Server.default, InMemoryUserService.live)

}
