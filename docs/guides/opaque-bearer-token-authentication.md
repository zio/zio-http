---
id: opaque-bearer-token-authentication
title: "Securing Your APIs: Opaque Bearer Token Authentication"
sidebar_label: "Opaque Bearer Token Authentication"
---

## What is Bearer Token Authentication?

Bearer token authentication is an HTTP authentication scheme that provides secure access to resources by requiring clients to present a token with each request. The term "bearer" implies that whoever possesses the token (the bearer) can access the associated resources, similar to how a physical ticket grants entry to an event. In this authentication method, the client includes a token in the `Authorization` header of HTTP requests using the format: `Authorization: Bearer <token>`.

Unlike traditional session-based authentication where the server maintains session state through cookies, bearer token authentication enables stateless communication between clients and servers. The token itself serves as the credential, eliminating the need for the server to look up session information in a centralized session store for every request—though as you'll see with opaque tokens, some server-side state management is still involved.

## Why Use Token-Based Authentication?

Token-based authentication offers several compelling advantages over traditional session-based approaches:

- **Stateless Architecture Support**: Tokens enable truly stateless server architectures (especially with self-contained tokens), where each request contains all the information needed for authentication. This eliminates server-side session storage requirements and simplifies horizontal scaling, as any server in a cluster can handle any request without sharing session state.
- **Cross-Domain and CORS Friendly**: Unlike cookies, which are bound to specific domains and can create complications with Cross-Origin Resource Sharing (CORS), tokens can be easily sent to any domain. This makes them ideal for scenarios where your API serves multiple client applications hosted on different domains, or when building microservices that need to communicate across service boundaries.
- **Mobile and IoT Ready**: Token-based authentication works seamlessly across different platforms and devices. Mobile applications, IoT devices, and desktop applications can all use the same authentication mechanism without dealing with cookie storage limitations or browser-specific behaviors. Tokens can be stored using platform-specific secure storage mechanisms.
- **Fine-Grained Access Control**: Tokens can carry detailed authorization information, including user permissions, access scopes, and identity claims either embedded directly in the token or through database references. This allows for complex authorization strategies such as issuing tokens with minimal necessary permissions, creating temporary tokens for specific operations with elevated privileges, or generating scoped tokens that only grant access to particular resources.
- **API Economy Integration**: Tokens are the foundation of modern API authentication standards like OAuth 2.0 and OpenID Connect. They enable secure third-party integrations, allowing your application to interact with external services or expose your own APIs to partners and developers.

### Overview of the Authentication Flow

The bearer token authentication flow with opaque tokens follows a well-defined sequence of interactions between the client and server. Let's examine this flow as implemented in our ZIO HTTP example:

1. **Initial Authentication (Login)**: The client initiates the authentication process by sending credentials to the `/login` endpoint. The server validates that the password matches the one stored for the given username. Upon successful validation, the server generates a token, stores it with the username and expiration time, and returns the token to the client.
2. **Accessing Protected Resources**: When accessing protected routes, the client includes the token in the Authorization header. The server's authentication middleware intercepts the request, validates the token against its storage, and either allows the request to proceed with the user context or rejects it with a 401 Unauthorized response.
3. **Token Lifecycle Management**: The authentication flow includes token lifecycle management through logout (explicit revocation) and automatic cleanup of expired tokens. This ensures that users can invalidate their sessions immediately when they want to log out, and also that the token storage doesn't grow indefinitely.

This is the simple flow of how opaque token authentication works. It can be extended with additional features like refresh tokens and scopes and permissions, but the core principles remain the same. The server issues tokens that clients use to authenticate requests, and the server maintains those tokens to grant or deny access to resources.

## Understanding Opaque Tokens

### What are Opaque Tokens?

Opaque tokens are authentication tokens that appear as random, meaningless strings to clients. The term "opaque" signifies that the token's content is not transparent or readable to the client—it's simply an identifier that references authentication information stored on the server. The token generation can be a cryptographically secure random string like this:

```scala
def generateSecureToken: UIO[String] =
  ZIO.succeed {
    val random = new SecureRandom()
    val bytes  = new Array[Byte](32)
    random.nextBytes(bytes)
    java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
  }
```

The resulting token might look like: `pC7SRyZ_WK5TbIml1coCTC4NwnE4nSHwEjlSkH__z_A`.

When a client presents an opaque token, the server must look up the associated information in its storage system. This lookup reveals the token's validity, associated user, permissions, and other metadata. The client cannot decode, modify, or extract any information from the token itself—it's just a random string that has meaning only to the server that issued it.

This opacity provides an important security property: even if an attacker obtains a token, they cannot learn anything about the user, permissions, or system internals by examining the token itself. The token reveals nothing about its purpose, scope, or associated identity without server-side lookup.

### Opaque Tokens vs. Self-contained Tokens (JWT)

The fundamental distinction between opaque tokens and self-contained tokens like JSON Web Tokens (JWT) lies in where and how authentication information is stored and validated:

**Information Storage**:
- **Opaque Tokens**: All authentication data (user identity, permissions, expiration) is stored server-side. The token is just a reference key to this data.
- **JWTs**: Contain encoded claims directly within the token structure. The token itself carries user information, expiration time, issuer details, and custom claims in a Base64-encoded JSON payload.

**Validation Process**:
- **Opaque Tokens**: Require a server-side lookup for every validation. The server must query its token storage to verify validity and retrieve associated information.
- **JWTs**: Can be validated using cryptographic signatures without any database lookup. The server only needs the signing key to verify the token's authenticity and can trust the embedded claims.

**Revocation Capabilities**:
- **Opaque Tokens**: Can be immediately revoked by removing them from server storage. Once deleted, the token becomes invalid instantly across all services.
- **JWTs**: Cannot be easily revoked before expiration since they're self-contained. Revocation requires maintaining a blocklist (defeating the stateless advantage) or keeping tokens short-lived with refresh token patterns.

**Size and Transmission**:
- **Opaque Tokens**: Typically compact (32-64 characters), resulting in smaller HTTP headers and reduced bandwidth usage.
- **JWTs**: Can become quite large (hundreds of characters), especially with multiple claims, potentially causing issues with HTTP header size limits.

These are some of the key differences between opaque tokens and self-contained tokens like JWTs. The choice between them depends on the specific requirements of your application, such as security needs, performance considerations, and architectural constraints (such as whether you work with a monolithic or microservices architecture).

## Implementation of Opaque Bearer Token Authentication

Similar to previous guides, we will implement the authentication system using HandlerAspect/Middleware to intercept requests and authenticate users as they access protected resources. Before we dive into the implementation, let's outline the components we will need: a `TokenService` for managing opaque tokens and a `UserService` for handling user accounts.

### Token Service

The `TokenService` forms the backbone of our authentication system, managing the entire lifecycle of opaque tokens from creation to revocation.

Let's define an interface that outlines the essential operations for our token service:

```scala
trait TokenService {
  def create(username: String): UIO[String]
  def validate(token: String): UIO[Option[String]]
  def cleanup(): UIO[Unit]
  def revoke(username: String): UIO[Unit]
}
```

It consists of four key operations: creating, validating, revoking, and cleaning up tokens. The `create` method generates a new token for a given user with a specified lifetime, while `validate` checks if a token is valid and returns the associated username if it is. The `revoke` method invalidates all tokens for a specific user, and the `cleanup` method removes expired tokens.

For simplicity, we will implement the `TokenService` using an in-memory store:

```scala
case class TokenInfo(username: String, expiresAt: Instant)

class InmemoryTokenService(tokenStorage: Ref[Map[String, TokenInfo]]) extends TokenService {

  private val TOKEN_LIFETIME = 300.seconds

  override def create(username: String): UIO[String] =
    for {
      token <- generateSecureToken
      now   <- Clock.instant
      _     <- tokenStorage.update { tokens =>
        tokens + (token -> TokenInfo(
          username = username,
          expiresAt = now.plusSeconds(TOKEN_LIFETIME.toSeconds),
        ))
      }
    } yield token

  override def validate(token: String): UIO[Option[String]] =
    tokenStorage.modify { tokens =>
      tokens.get(token) match {
        case Some(tokenInfo) if tokenInfo.expiresAt.isAfter(Instant.now()) =>
          (Some(tokenInfo.username), tokens)
        case Some(_) =>
          // Token expired, remove it
          (None, tokens - token)
        case None =>
          (None, tokens)
      }
    }

  override def cleanup(): UIO[Unit] =
    tokenStorage.update {
      _.filter { case (_, tokenInfo) =>
        tokenInfo.expiresAt.isAfter(Instant.now())
      }
    }

  override def revoke(username: String): UIO[Unit] =
    tokenStorage.update {
      _.filter { case (_, tokenInfo) =>
        tokenInfo.username != username
      }
    }

  private def generateSecureToken: UIO[String] =
    ZIO.succeed {
      val random = new SecureRandom()
      val bytes  = new Array[Byte](32)
      random.nextBytes(bytes)
      java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
    }
}
```

In a production system, you would typically use a distributed cache like Redis or a database to persist tokens across server restarts and scale horizontally.

### User Service

The next step is to define the `UserService`, which is the same as in the digest authentication guide, but we will include it here for completeness. The `UserService` manages user accounts and their associated data, such as usernames, passwords, and emails:

```scala
case class User(username: String, password: Secret, email: String)

sealed trait UserServiceError
object UserServiceError {
  case class UserNotFound(username: String)      extends UserServiceError
  case class UserAlreadyExists(username: String) extends UserServiceError
}

trait UserService {
  def getUser(username: String): IO[UserNotFound, User]
  def addUser(user: User): IO[UserAlreadyExists, Unit]
  def updateEmail(username: String, newEmail: String): IO[UserNotFound, Unit]
}

case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {

  def getUser(username: String): IO[UserNotFound, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.get(username)).orElseFail(UserNotFound(username))
    }

  def addUser(user: User): IO[UserAlreadyExists, Unit] =
    users.get.flatMap { userMap =>
      ZIO.when(userMap.contains(user.username)) {
        ZIO.fail(UserAlreadyExists(user.username))
      } *> users.update(_.updated(user.username, user))
    }

  def updateEmail(username: String, newEmail: String): IO[UserNotFound, Unit] = for {
    currentUsers <- users.get
    user         <- ZIO.fromOption(currentUsers.get(username)).orElseFail(UserNotFound(username))
    _            <- users.update(_.updated(username, user.copy(email = newEmail)))
  } yield ()
}
```

To initialize the `UserService`, we can use a simple in-memory store with a predefined set of users. This is useful for testing and demonstration purposes, but in a real application, you would typically connect to a database or another persistent storage solution.

```scala
object UserService {
  private val initialUsers = Map(
    "john"  -> User("john", Secret("password123"), "john@example.com"),
    "jane"  -> User("jane", Secret("secret456"), "jane@example.com"),
    "admin" -> User("admin", Secret("admin123"), "admin@company.com"),
  )

  val live: ZLayer[Any, Nothing, UserService] =
    ZLayer.fromZIO {
      Ref.make(initialUsers).map(UserServiceLive(_))
    }
}
```

We instantiate the service using a predefined set of users, which allows us to test the authentication flow without handling registration or user creation.

:::note
In production applications, we shouldn't store passwords in plain text. Instead, we should use a secure hashing algorithm like bcrypt or Argon2 to hash passwords before storing them.
:::

### Authentication Middleware

The authentication middleware serves as the security gateway for protected resources, implementing a clean separation between authentication logic and business logic. It is responsible for intercepting incoming requests and authenticating users based on the provided bearer token. This middleware will use the `TokenService` to validate tokens and the `UserService` to retrieve user information associated with the token:

```scala
import zio._
import zio.http._

object AuthHandlerAspect {
  def authenticate: HandlerAspect[TokenService with UserService, User] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) =>
          ZIO.serviceWithZIO[TokenService](_.validate(token.stringValue)).flatMap {
            case Some(username) =>
              ZIO
                .serviceWithZIO[UserService](_.getUser(username))
                .map(user => (request, user))
                .orElse(
                  ZIO.fail(
                    Response.unauthorized("User not found!"),
                  ),
                )
            case None           =>
              ZIO.fail(Response.unauthorized("Invalid or expired token!"))
          }
        case _                                        =>
          ZIO.fail(
            Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))),
          )
      }
    })
}
```

This middleware checks for the presence of the `Authorization` header in the incoming request. If the header is present and contains a valid bearer token, it retrieves the associated username from the `TokenService`. Then, it uses the `UserService` to fetch the user details. If successful, it allows the request to proceed with the user context; otherwise, it returns an unauthorized response.

## Server Routes

All the components are in place, and now we can start defining the server routes. First, we will define a route for login, which will handle token generation, and then we will create a protected route that requires authentication to access user profile information. Finally, we'll implement logout functionality to revoke tokens.

### Login

The login route is responsible for taking user credentials (username and password) and generating an opaque token upon successful authentication:

```scala
val login =
  Method.POST / "login"         ->
    handler { (request: Request) =>
      val form = request.body.asURLEncodedForm.orElseFail(Response.badRequest)
      for {
        username <- form
          .map(_.get("username"))
          .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing username field!")))
          .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing username value!")))
        password <- form
          .map(_.get("password"))
          .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing password field!")))
          .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing password value!")))
        users    <- ZIO.service[UserService]
        user     <- users.getUser(username).orElseFail(Response.unauthorized(s"Username or password is incorrect."))
        tokenService <- ZIO.service[TokenService]
        response     <-
          if (user.password == Secret(password))
            tokenService.create(username).map(Response.text)
          else ZIO.fail(Response.unauthorized("Username or password is incorrect."))
      } yield response
    },
```

This login route processes POST requests with URL-encoded username and password fields, extracts the form data, retrieves the user from `UserService`, and generates an authentication token via `TokenService`. If the credentials are valid, it returns the token in the response; otherwise, it responds with a 401 Unauthorized status.

### Protected Route: Profile

Let's write the protected route `GET /profile/me`, which returns the profile of the user:

```scala
val profile = 
  Method.GET / "profile" / "me" -> handler { (_: Request) =>
    ZIO.serviceWith[User](user =>
      Response.text(
         s"This is your profile: \n Username: ${user.username} \n Email: ${user.email}",
      ),
    )
  } @@ authenticate
```

This route is protected by the `authenticate` middleware we defined earlier. It retrieves the authenticated user from the request context and returns their profile information. The client should use the token issued by the server after login by including it in the `Authorization` header to access this protected route, e.g., API call:

```http
GET /profile/me HTTP/1.1
Authorization: Bearer pC7SRyZ_WK5TbIml1coCTC4NwnE4nSHwEjlSkH__z_A
```

### Logging out and Revoking Tokens

One of the key benefits of opaque tokens is that they can be easily revoked by the server. To implement a logout route that revokes the user's token, we can define the following route:

```scala
val logout =
  Method.POST / "logout"        ->
    Handler.fromZIO(ZIO.service[TokenService]).flatMap { tokenService =>
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            tokenService.validate(token.stringValue).flatMap {
              case Some(username) =>
                tokenService.revoke(username).as(Response.text("Logged out successfully!"))
              case None           =>
                ZIO.fail(Response.unauthorized("Invalid or expired token!"))
            }
          case _                                        =>
            ZIO.fail(
              Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))),
            )
        }
      } @@ authenticate.as[Unit](())
    }
```

As we don't require the returned user context for the logout operation, we convert the `HandlerAspect[TokenService & UserService, User]` to `HandlerAspect[TokenService & UserService, Unit]` using `as[Unit](())`. This allows us to focus solely on the token revocation logic without requiring user details from the context.


## Writing the Client

The following ZIO HTTP client demonstrates how to interact with the authentication server we just built. It performs the login operation to obtain a token, then uses that token to access the protected profile route:

```scala
import zio._
import zio.http._

object AuthenticationClient extends ZIOAppDefault {
  val url = "http://localhost:8080"

  val loginUrl   = URL.decode(s"$url/login").toOption.get
  val profileUrl = URL.decode(s"$url/profile/me").toOption.get

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
  } yield ()

  override val run = program.provide(Client.default)

}
```

Using the same `token` we obtained, we can try to log out or revoke the token, so the client can't access the protected profile route anymore:

```scala
val logoutUrl  = URL.decode(s"$url/logout").toOption.get

for {
  _          <- ZIO.debug("Logging out...")
  logoutBody <- client
    .batched(Request.post(logoutUrl, Body.empty).addHeader(Header.Authorization.Bearer(token)))
    .flatMap(_.body.asString)
  _          <- ZIO.debug(s"Logout response: $logoutBody")

  _    <- ZIO.debug("Trying to access protected route after logout...")
  body <- client
    .batched(Request.get(profileUrl).addHeader(Header.Authorization.Bearer(token)))
    .flatMap(_.body.asString)
  _    <- ZIO.debug(s"Protected route response after logout: $body")

} yield ()
```

After logging out, the client attempts to access the profile route again, which should fail with an unauthorized response since the token has been revoked.

## Web Client Demo

To demonstrate the authentication flow in a web client, we've created a simple HTML page where users can log in, view their profile, and log out.

First, start the `AuthenticationServer`, which provides the authentication API and serves the HTML client (`opaque-bearer-token-authentication.html`) located in the resource folder:

```scala
sbt "zioHttpExample/runMain example.auth.bearer.opaque.AuthenticationServer"
```

Then open [http://localhost:8080](http://localhost:8080) in your browser to interact with the system using predefined credentials. You can log in, view your profile, and log out, showcasing the full opaque bearer token authentication flow.

The HTML file's source code can be found in the example project's resource folder.

## Conclusion

Opaque bearer token authentication provides a robust and flexible approach for securing APIs, offering clear advantages in scenarios where fine-grained control, immediate revocation, and server-managed session data are priorities. By storing all authentication details server-side, opaque tokens mitigate the risk of exposing sensitive information within the token itself and simplify revocation processes compared to self-contained tokens like JWTs.

The implementation outlined here demonstrates how to integrate opaque tokens into a ZIO HTTP application, from token generation and validation to middleware enforcement and route protection.