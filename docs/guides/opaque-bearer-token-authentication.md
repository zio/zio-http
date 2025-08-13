---
id: opaque-bearer-token-authentication
title: "Securing Your APIs: Opaque Bearer Token Authentication"
sidebar_label: "Opaque Bearer Token Authentication"
---

## What is Bearer Token Authentication?

Bearer token authentication is an HTTP authentication scheme that provides secure access to resources by requiring clients to present a token with each request. The term "bearer" implies that whoever possesses the token (the bearer) can access the associated resources, similar to how a physical ticket grants entry to an event. In this authentication method, the client includes a token in the `Authorization` header of HTTP requests using the format: `Authorization: Bearer <token>`.

Unlike traditional session-based authentication where the server maintains session state through cookies, bearer token authentication enables stateless communication between clients and servers. The token itself serves as the credential, eliminating the need for the server to look up session information in a centralized session store for every request—though as we'll see with opaque tokens, some server-side state management is still involved.

## Why Use Token-Based Authentication?

Token-based authentication offers several compelling advantages over traditional session-based approaches:

**Stateless Architecture Support**: Tokens enable truly stateless server architectures (especially with self-contained tokens), where each request contains all the information needed for authentication. This eliminates server-side session storage requirements and simplifies horizontal scaling, as any server in a cluster can handle any request without sharing session state.

**Cross-Domain and CORS Friendly**: Unlike cookies, which are bound to specific domains and can create complications with Cross-Origin Resource Sharing (CORS), tokens can be easily sent to any domain. This makes them ideal for scenarios where your API serves multiple client applications hosted on different domains, or when building microservices that need to communicate across service boundaries.

**Mobile and IoT Ready**: Token-based authentication works seamlessly across different platforms and devices. Mobile applications, IoT devices, and desktop applications can all use the same authentication mechanism without dealing with cookie storage limitations or browser-specific behaviors. Tokens can be stored using platform-specific secure storage mechanisms.

**Fine-Grained Access Control**: Tokens can encode or reference specific permissions, scopes, and claims about the user. This enables sophisticated authorization schemes where different tokens can provide different levels of access to resources, supporting principles like least privilege access and temporary elevated permissions.

**Improved Security Posture**: When properly implemented, token-based authentication can offer better security than traditional cookie-based sessions. Tokens can have precise expiration times, be revoked immediately when compromised, and don't suffer from CSRF attacks that plague cookie-based authentication (though they require careful protection against XSS attacks).

**API Economy Integration**: Tokens are the foundation of modern API authentication standards like OAuth 2.0 and OpenID Connect. They enable secure third-party integrations, allowing your application to interact with external services or expose your own APIs to partners and developers.

### Overview of the Authentication Flow

The bearer token authentication flow with opaque tokens follows a well-defined sequence of interactions between the client and server. Let's examine this flow as implemented in our ZIO HTTP example:

**1. Initial Authentication (Login)**
```
Client                          Server (AuthenticationServer)
  |                                |
  |----POST /login---------------->|
  |    {username, password}        |
  |                                |
  |                          Validate credentials
  |                          Generate opaque token
  |                          Store token with metadata
  |                                |
  |<---200 OK----------------------|
  |    {token: "abc123..."}       |
```

The client initiates authentication by sending credentials to the `/login` endpoint. In our example, the server validates that the password is the reverse of the username (a simplified validation for demonstration purposes). Upon successful validation, the server generates a random UUID-based token, stores it with the username and expiration time, and returns it to the client.

**2. Accessing Protected Resources**

```
Client                          Server
  |                                |
  |----GET /profile/me------------>|
  |    Authorization: Bearer token |
  |                                |
  |                          Extract token from header
  |                          Validate token exists
  |                          Check expiration
  |                          Retrieve user context
  |                                |
  |<---200 OK----------------------|
  |    {data: "Welcome John!"}     |
```

When accessing protected routes, the client includes the token in the Authorization header. The server's authentication middleware intercepts the request, validates the token against its storage, and either allows the request to proceed with the user context or rejects it with a 401 Unauthorized response.

**3. Token Lifecycle Management**
```
Client                          Server
  |                                |
  |----POST /logout--------------->|
  |    Authorization: Bearer token |
  |                                |
  |                          Validate token
  |                          Revoke all user tokens
  |                          Clean expired tokens
  |                                |
  |<---200 OK----------------------|
  |    {message: "Logged out"}    |
```

The authentication flow includes token lifecycle management through logout (explicit revocation) and automatic cleanup of expired tokens. This ensures that the token storage doesn't grow indefinitely and that users can immediately invalidate their sessions when needed.

## Understanding Opaque Tokens

### What are Opaque Tokens?

Opaque tokens are authentication tokens that appear as random, meaningless strings to clients. The term "opaque" signifies that the token's content is not transparent or readable to the client—it's simply an identifier that references authentication information stored on the server. In our ZIO HTTP implementation, these tokens are generated as modified UUIDs:

```scala
ZIO.randomWith(_.nextUUID).map(_.toString.replace("-", ""))
```

The resulting token might look like: `f47ac10b58cc4372a5670e02b2c3d479`

Unlike self-contained tokens that carry encoded information, opaque tokens are merely references or pointers to server-side data. When a client presents an opaque token, the server must look up the associated information in its storage system. This lookup reveals the token's validity, associated user, permissions, and other metadata. The client cannot decode, modify, or extract any information from the token itself—it's just a random string that has meaning only to the server that issued it.

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
- **JWTs**: Can become quite large (hundreds of characters) especially with multiple claims, potentially causing issues with HTTP header size limits.

**Visibility and Debugging**:
- **Opaque Tokens**: Provide no information to clients or intermediaries, making debugging more challenging but improving security through obscurity.
- **JWTs**: Claims are readable (though not modifiable) by anyone with the token, which aids debugging but may leak information if sensitive data is included in claims.

### Advantages and Disadvantages

**Advantages of Opaque Tokens:**

1. **Immediate Revocation**: The server can instantly invalidate tokens by removing them from storage, providing real-time access control. This is crucial for security incidents where compromised tokens must be invalidated immediately.

2. **Complete Server Control**: The server maintains full authority over token validity and can implement complex validation rules, update permissions dynamically, or modify token metadata without client awareness.

3. **Information Security**: No sensitive information is exposed in the token itself. Even if intercepted, attackers learn nothing about the system, user roles, or internal structures.

4. **Flexible Storage of Metadata**: The server can associate unlimited metadata with tokens without size constraints. User preferences, session data, and audit information can be stored and modified server-side.

5. **Simplified Client Implementation**: Clients treat tokens as simple strings without needing libraries for parsing or validation. This reduces client-side complexity and potential security vulnerabilities.

**Disadvantages of Opaque Tokens:**

1. **Scalability Challenges**: Every request requires server-side token lookup, creating potential bottlenecks. In high-traffic scenarios, token validation can become a performance limitation requiring careful infrastructure planning.

2. **Distributed System Complexity**: In microservices architectures, all services need access to the central token store, introducing network latency and potential points of failure. This often necessitates distributed caching solutions like Redis.

3. **Stateful Architecture**: Despite using tokens, the server remains stateful, maintaining token storage that must be synchronized across server instances. This complicates horizontal scaling and disaster recovery.

4. **Storage Requirements**: Token storage requires memory or database resources that grow with active users. Our in-memory implementation, while simple, doesn't survive server restarts and doesn't scale across multiple instances.

5. **Network Dependency**: Token validation requires network calls to the storage system, adding latency and creating dependencies on storage availability. Network partitions can cause authentication failures.

### When to Use Opaque Tokens

Opaque tokens are the optimal choice in several scenarios:

**High Security Requirements**: When your application handles sensitive data (financial, healthcare, government), the ability to immediately revoke access and maintain complete server-side control over authentication state is paramount. Opaque tokens ensure that compromised credentials can be invalidated instantly.

**Session-like Behavior Needs**: Applications requiring traditional session management features—such as tracking active sessions, forcing single sign-on, or implementing "logout from all devices"—benefit from opaque tokens' server-side state management.

**Monolithic or Tightly Coupled Architectures**: When your application runs on a single server or a tightly coupled cluster with shared storage, opaque tokens provide simplicity without the complexity of distributed token validation. Our ZIO HTTP example demonstrates this scenario perfectly.

**Dynamic Permission Systems**: If user permissions change frequently or need real-time updates, opaque tokens allow immediate permission changes without waiting for token expiration. This is crucial for applications with complex, dynamic authorization requirements.

**Regulatory Compliance**: Industries with strict audit requirements benefit from opaque tokens' centralized validation, providing complete audit trails of all authentication attempts and token usage patterns.

**Limited Token Lifespan Applications**: For use cases where tokens are short-lived (minutes to hours), the overhead of server-side storage is minimal, and the benefits of immediate revocation outweigh scalability concerns.

**Internal APIs and Microservices**: Within trusted network boundaries where all services have access to the same token store, opaque tokens provide simpler implementation than managing JWT signing keys across services.

Consider avoiding opaque tokens when building globally distributed systems with millions of users, implementing stateless microservices, or when client-side token inspection is beneficial for performance optimization. In these cases, self-contained tokens like JWTs, possibly combined with refresh token patterns, may be more appropriate.

The choice between opaque and self-contained tokens isn't always binary—many production systems implement hybrid approaches, using JWTs for stateless authentication while maintaining a small set of opaque refresh tokens for revocation capabilities. This combines the scalability of JWTs with the control of opaque tokens, providing a balanced solution for complex authentication requirements.

## Implementation

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

It consists of four key operations: creating, validating, revoking, and cleaning up tokens. The `create` method generates a new token for a given user with a specified lifetime, while `validate` checks if a token is valid and returns the associated username if it is. The `revoke` invalidates all tokens for a specific user and the `cleanup` method removes expired tokens.

For simplicity, we will implement the `TokenService` using an in-memory store:

```
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
        case Some(_)                                                       =>
          // Token expired, remove it
          (None, tokens - token)
        case None                                                          =>
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

The next step is to define the `UserService` which is the same as in the digest authentication guide, but we will include it here for completeness. The `UserService` manages user accounts and their associated data, such as usernames, passwords, and emails:

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

To initiate the `UserService`, we can use a simple in-memory store with a predefined set of users. This is useful for testing and demonstration purposes, but in a real application, you would typically connect to a database or another persistent storage solution.

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

We instantiate the service using a predefined set of users, which allows us to test the authentication flow without doing registration or user creation.

:::note
In real production applications, we shouldn't store passwords in plain text. Instead, we should use a secure hashing algorithm like bcrypt or Argon2 to hash passwords before storing them.
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

This login route processes POST requests with URL-encoded username and password fields, extracts the form data, retrieves the user from `UserService`, and generate an authentication token via `TokenService`. If the credentials are valid, it returns the token in the response; otherwise, it responds with a 401 Unauthorized status.

### Protected Route: Profile

Let's write the protected route `GET /profile/me` which returns the profile of the user:

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

This route is protected by the `authenticate` middleware we defined earlier. It retrieves the authenticated user from the request context and returns their profile information. The client should use the token issued by the server after login by including it in the `Authorization` header to access this protected route, e.g. API call:

```http
GET /profile/me HTTP/1.1
Authorization: Bearer pC7SRyZ_WK5TbIml1coCTC4NwnE4nSHwEjlSkH__z_A
```

### Logging Out and Revoking Tokens

One of the key benefits of opaque tokens is that they can be easily revoked by the server. To implement a logout route that revokes the user's token, we can define the following route:

```
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

As we don't require the returned user context for the logout operation, convert the `HandlerAspect[TokenService & UserService, User]` to `HandlerAspect[TokenService & UserService, Unit]` using `as[Unit](())`. This allows us to focus solely on the token revocation logic without requiring user detail from the context.


## Conclustion
