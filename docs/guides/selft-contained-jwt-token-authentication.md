---
id: authentication-with-jwt-bearer-tokens
title: "Securing Your APIs: Authentication with JWT Bearer Tokens"
sidebar_label: "Authentication with JWT Bearer Tokens"
---

Self-contained tokens are authentication tokens that carry all the necessary information within themselves, eliminating the need for server-side storage or database lookups during validation. Unlike traditional session identifiers that merely point to data stored on the server, self-contained tokens embed the actual user information, permissions, and metadata directly within the token structure.

Think of it this way: a session ID is like a driver's license—it contains all the relevant information right there on the card itself.

A JSON Web Token (JWT) is the most widely adopted standard for self-contained tokens. It packages user claims and metadata into a compact, URL-safe string that consists of three parts: a header (describing the token type and signing algorithm), a payload (containing the actual claims), and a signature (ensuring the token's integrity).

## The evolution from session-based to token-based authentication

### The Session Era

In the early days of web applications, session-based authentication reigned supreme. The flow was straightforward:

1. User logs in with credentials
2. Server creates a session and stores it in memory or database
3. Server sends a session ID to the client (usually as a cookie)
4. Client includes session ID with every request
5. Server looks up session data for each request

This approach worked well for monolithic applications running on single servers. However, as applications grew more complex, several challenges emerged:

- **Scalability issues**: Every authenticated request required a database lookup or shared memory access
- **Server affinity**: Load-balanced environments needed sticky sessions or distributed session stores
- **Cross-domain limitations**: Cookies don't work well across different domains
- **Mobile app friction**: Native mobile apps don't handle cookies as naturally as browsers

### The Shift to Stateless Authentication

As RESTful APIs and microservices architectures gained popularity, the industry needed a more scalable, stateless approach. Token-based authentication emerged as the solution, with JWTs becoming the de facto standard by offering:

- **Statelessness**: No server-side session storage required
- **Decentralization**: Any server can verify a token without accessing a central store
- **Cross-domain friendly**: Tokens work seamlessly across different domains and platforms
- **Mobile-ready**: Easy to implement in native mobile applications

## When JWTs are the Right Choice

Picture building a modern RESTful API for a React application. This is JWT territory. The stateless nature aligns perfectly with REST principles, and your frontend can store and send tokens without wrestling with cookies or CORS.

In microservices architectures, JWTs truly shine. Your authentication service issues a token, and every other service—user profiles, orders, notifications—validates it independently using a shared secret. Beautiful simplicity.

For temporary operations like password resets or file download links, JWTs provide elegant solutions. Embed the expiration and permissions in the token itself. When it expires, access stops—no cleanup required. And when providing API access to external consumers, JWTs offer a clean, standard approach without exposing internal session mechanisms.

## When to Reconsider JWTs

But JWTs aren't always the answer. Sometimes they create more problems than they solve.

Need immediate revocation for security incidents? JWTs remain valid until expiration. You could maintain a blacklist, but then you've reinvented sessions with extra steps. Thinking about storing sensitive data? Don't. JWTs are encoded, not encrypted—anyone can decode them.

Watch the token size too. If your JWT grows to several kilobytes with extensive permissions and preferences, you're adding that overhead to every single request. A simple session ID might serve you better.

For traditional server-rendered applications, JWTs often add unnecessary complexity. Cookie-based sessions work naturally with form submissions and page navigations—why complicate things using JWT tokens?

When permissions must change instantly, session-based systems offer better control. You modify server-side data, and changes take effect immediately. With JWTs, you're waiting for expiration or building complex revocation systems.

## Anatomy of a JWT (Header, Payload, Signature)

If you open any JWT in a decoder, you'll see something that looks like this: three chunks of gibberish separated by dots. Something like `eyJhbGciOiJIUzI1NiIs...`. But there's a beautiful structure underneath.

A JWT consists of three distinct parts, joined together with periods: `header.payload.signature`. Each part serves a specific purpose, and together they create a self-contained, verifiable token.

### 1. The Header

The header is JWT's metadata—it tells receivers how to handle the token. Typically, it contains just two pieces of information: the token type (JWT) and the signing algorithm (like HS256 or RS256).

```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

This JSON object gets Base64URL-encoded to become the first part of your token. It's like the envelope of a letter, telling the recipient how to open and verify what's inside.

### 2. The Payload

The payload is where the magic happens—it contains the actual **claims** about the user and additional metadata. These claims might include who the user is (`subject`), when the token was issued (`iat`), when it expires (`exp`), and any custom data your application needs:

```json
{
  "sub": "1234567890",
  "name": "Jane Doe",
  "email": "jane@example.com",
  "roles": ["admin", "user"], // e.g. of custom field
  "iat": 1516239022,
  "exp": 1516242622
}
```

The payload carries your application's truth about the authenticated user. It's Base64URL-encoded but not encrypted—anyone can decode and read it. This transparency is by design, not oversight.

### 3. The Signature

The signature is JWT's tamper-proof seal. It's created by taking the encoded header, the encoded payload, a **secret key**, and running them through the algorithm specified in the header.

```
HMACSHA256(
  base64UrlEncode(header) + "." +
  base64UrlEncode(payload),
  secret
)
```

This signature ensures that if anyone modifies the header or payload, the signature won't match, and token validation will fail. It's the cryptographic guarantee that the token hasn't been tampered with since its creation.

## How JWTs work under the hood

Understanding JWT mechanics helps you appreciate both their elegance and their limitations.

When a server creates a JWT, it starts by constructing the header and payload JSON objects. These get Base64URL-encoded separately, creating the first two parts of the token. Then comes the crucial step: the server takes these encoded strings, concatenates them with a period, and feeds them along with a secret key into a cryptographic signing algorithm. The resulting signature becomes the third part of the token.

The complete token—header.payload.signature—gets sent to the client. The client doesn't need to understand any of this; it just stores the token and sends it back with future requests, typically in an Authorization header: `Bearer eyJhbGciOiJIUzI1NiIs...`.

When the server receives a JWT, the verification process runs in reverse. It splits the token at the periods, extracting the three parts. It decodes the header to determine which algorithm to use. Then—and this is the critical part—it takes the received header and payload, signs them again with its secret key, and compares this new signature with the signature that came with the token. If they match, the token is valid and unmodified.

The beauty lies in the mathematics: without the secret key, it's computationally infeasible to create a valid signature. Even changing a single character in the payload would produce a completely different signature. This is how JWTs achieve integrity without encryption.

But here's what catches developers off guard: the server doesn't store these tokens anywhere. There's no database table of valid tokens, no session store to query. The token's validity comes entirely from its signature. If the signature checks out and the token hasn't expired, it's valid—period. This statelessness is both JWT's greatest strength and its most significant limitation.

## The Difference Between Encoding, Encryption, and Signing

These three concepts get confused constantly, but understanding their differences is crucial for JWT security.

- **Encoding** is simply changing data's representation. It's like translating a book—the information remains the same, just in a different format. Base64URL encoding transforms JSON into URL-safe strings. There's no secret involved; anyone can encode or decode. It's about compatibility, not security. When you Base64URL-encode the JWT payload, you're not hiding anything—you're just making it transport-friendly.
- **Encryption** scrambles data so only authorized parties can read it. It's like putting your message in a locked safe—without the key, the contents are meaningless gibberish. Encryption requires secrets (keys) and protects confidentiality. Standard JWTs don't use encryption; the payload is merely encoded. Anyone who intercepts a JWT can read its contents. This is why you should never put passwords or sensitive data in JWT payloads.
- **Signing** creates a cryptographic fingerprint of data. It doesn't hide the data but proves it hasn't been tampered with and confirms who created it. Think of it like a seal on a letter—the letter is still readable, but the unbroken seal proves authenticity and integrity. This is what the JWT signature provides. The signature says: "This token was created by someone with the secret key, and nobody has modified it since."

The confusion often comes from expecting JWTs to provide all three. In reality, standard JWTs only provide encoding (for transport) and signing (for integrity). They don't provide encryption (for confidentiality). Your JWT payload is visible to anyone who intercepts it—the signature only prevents them from modifying it.

This is why HTTPS is non-negotiable when using JWTs. HTTPS provides the encryption layer, protecting tokens during transmission. The JWT signature ensures integrity, HTTPS ensures confidentiality, and together they provide complete security.

If you absolutely need encrypted payloads, JSON Web Encryption (JWE) exists, but it adds significant complexity. Most applications find that the combination of signed JWTs over HTTPS provides the right balance of security and simplicity.

## Symmetric vs. Asymmetric Signing

When it comes to signing JWTs, you have two main approaches: symmetric and asymmetric signing. Each has its own use cases, advantages, and trade-offs.

### Symmetric Signing

Symmetric signing uses a single secret key for both creating and verifying the JWT signature. The same key must be shared between the issuer (the server that creates the token) and the verifier (the server that checks the token).

In symmetric signing, algorithms like HMAC with SHA-256 (HS256) are commonly used. The process is straightforward:
1. The server generates a secret key and keeps it safe.
2. When creating a JWT, the server uses this secret key to sign the token.
3. When verifying the JWT, the server uses the same secret key to validate the signature.
4. If the signature matches, the token is valid.
5. If the signature doesn't match, the token has been tampered with or is invalid.

The main advantage of symmetric signing is its simplicity and speed. Since only one key is involved, it's easy to implement and efficient to compute. However, the downside is that both the issuer and verifier must securely share and manage the same secret key. If the key is compromised, anyone with access to it can create valid tokens.

### Asymmetric Signing

Asymmetric signing, on the other hand, uses a pair of keys: a private key for signing and a public key for verification. The private key is kept secret by the issuer, while the public key can be freely shared with anyone who needs to verify the token.

In asymmetric signing, algorithms like RSA (RS256) or ECDSA (ES256) are commonly used. The process works as follows:
1. The server generates a key pair: a private key and a public key.
2. When creating a JWT, the server uses the private key to sign the token.
3. When verifying the JWT, the server (or any other party) uses the public key to validate the signature.
4. If the signature matches, the token is valid.
5. If the signature doesn't match, the token has been tampered with or is invalid.

The main advantage of asymmetric signing is enhanced security and flexibility. Since the private key never leaves the issuer, it reduces the risk of compromise. Additionally, multiple verifiers can validate tokens using the public key without needing access to the private key. This is particularly useful in distributed systems or microservices architectures where different services need to verify tokens issued by a central authority.

However, asymmetric signing is more complex to implement and generally slower than symmetric signing due to the computational overhead of public-key cryptography.

### Choosing Between Symmetric and Asymmetric Signing

The choice between symmetric and asymmetric signing depends on your application's requirements:
- **Use Symmetric Signing (e.g., HS256)** when:
  - You have a simple architecture with a single service issuing and verifying tokens.
  - Performance is a critical concern, and you need fast token processing.
  - You can securely manage and share the secret key between the issuer and verifier.

- **Use Asymmetric Signing (e.g., RS256, ES256)** when:
  - You have a distributed system or microservices architecture where multiple services need to verify tokens.
  - You want to minimize the risk of key compromise by keeping the private key secure and only sharing the public key.
  - You require a higher level of security and flexibility in token verification.

## Implementation

### JSON Web Token Service

We need a service to issue and verify JWT tokens. Let's define an interface for such a service:

```scala
trait JwtTokenService {
  def issue(username: String): UIO[String]
  def verify(token: String): Task[String]
}
```

The `issue` method takes a `username` and generates a JWT token. It uses the standard `sub` claim to store the username. The `verify` method takes a JWT token and decodes it, returning the username if the token is valid, or failing if the token is invalid or expired.

### Implementing the JWT Service

To implement the `JwtTokenService`, we can use the `jwt-core` library:

```sbt
libraryDependencies += "com.github.jwt-scala" %% "jwt-core" % @JWT_CORE_VERSION@
```

It supports both symmetric and asymmetric signing. The following implementation uses a symmetric signing algorithm:

```scala
case class JwtAuthServiceLive(
  secretKey: Secret,
  tokenTTL: Duration,
  algorithm: JwtHmacAlgorithm,
) extends JwtTokenService {
  implicit val clock: Clock = Clock.systemUTC

  override def issue(username: String): UIO[String] =
    ZIO.succeed {
      Jwt.encode(
        JwtClaim(subject = Some(username)).issuedNow.expiresIn(tokenTTL.toSeconds),
        secretKey.stringValue,
        algorithm,
      )
    }

  override def verify(token: String): Task[String] =
    ZIO
      .fromTry(
        Jwt.decode(token, secretKey.stringValue, Seq(algorithm)),
      )
      .map(_.subject)
      .some
      .orElse(ZIO.fail(new Exception("Invalid token")))
}

object JwtTokenService {
  def live(
    secretKey: Secret,
    tokenTTL: Duration = 15.minutes,
    algorithm: JwtHmacAlgorithm = JwtAlgorithm.HS512,
  ): ULayer[JwtAuthServiceLive] =
    ZLayer.succeed(JwtAuthServiceLive(secretKey, tokenTTL, algorithm))
}
```

The `JwtAuthServiceLive` class implements the `JwtTokenService` interface. It uses a `secretKey` to sign and verify tokens, a `tokenTTL` to set the token expiration time, and an `algorithm` to specify the signing algorithm.

### Authentication Middleware

Now that we have a JWT service, we can create an authentication middleware that verifies the JWT token in the `Authorization` header of incoming requests:

```scala
import zio._
import zio.http._

object AuthMiddleware {
  def jwtAuthentication(realm: String): HandlerAspect[JwtTokenService & UserService, UserInfo] =
    HandlerAspect.interceptIncomingHandler {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            ZIO
              .serviceWithZIO[JwtTokenService](_.verify(token.value.asString))
              .flatMap { username =>
                ZIO.serviceWithZIO[UserService](_.getUser(username))
              }
              .map(u => UserInfo(u.username, u.email, u.roles))
              .map(userInfo => (request, userInfo))
              .orElseFail(
                Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))),
              )
          case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))))
        }
      }
    }
}
```

The `jwtAuth` method creates a middleware that intercepts incoming requests. It checks for the `Authorization` header and extracts the JWT token. It then uses the `JwtTokenService` to verify the token and retrieve the username. If the token is valid, it fetches the user details from the `UserService` and passes them along with the request to the next handler. If the token is invalid or missing, it responds with a `401 Unauthorized` status including the `WWW-Authenticate` header with the realm of the resource that requires authentication.

Please note that the `UserService` is the same as defined in the [digest authentication guide](digest-authentication.md).

Also please note that we've introduced a new data type called `UserInfo` that encapsulates all necessary user information to be passed to the next handlers in the request pipeline:

```scala
case class UserInfo(
  username: String,
  email: String,
  roles: Set[String],
)

object UserInfo {
  implicit val codec: JsonCodec[UserInfo] = DeriveJsonCodec.gen
}
```

### The Login Route

The login process involves the following steps:
1. The server receives the username and password from the client.
2. The server verifies the credentials against the user store.
3. If the credentials are valid, the server issues a JWT token using the `JwtTokenService`.
4. The server responds with the JWT token to the client.

The login route requires two services: `UserService` to verify the user credentials and `JwtTokenService` to issue the JWT token. Here's how we can implement the login route:

```scala
val login =
  Method.POST / "login" ->
    handler { (request: Request) =>
      def extractFormField(form: Form, fieldName: String): ZIO[Any, Response, String] =
        ZIO
          .fromOption(form.get(fieldName).flatMap(_.stringValue))
          .orElseFail(Response.badRequest(s"Missing $fieldName"))

      val unauthorizedResponse =
        Response
          .unauthorized("Invalid username or password.")
          .addHeaders(Headers(Header.WWWAuthenticate.Bearer("User Login")))

      for {
        form         <- request.body.asURLEncodedForm.orElseFail(Response.badRequest)
        username     <- extractFormField(form, "username")
        password     <- extractFormField(form, "password")
        tokenService <- ZIO.service[JwtTokenService]
        user         <- ZIO
          .serviceWithZIO[UserService](_.getUser(username))
          .orElseFail(unauthorizedResponse)
        response     <-
          if (user.password == Secret(password))
            tokenService.issue(username).map(Response.text(_))
          else
            ZIO.fail(unauthorizedResponse)
      } yield response
    }
```

The `login` route listens for `POST` requests at the `/login` endpoint. It extracts the `username` and `password` from the request body, verifies the credentials using the `UserService`, and if valid, issues a JWT token using the `JwtTokenService`. The token is then returned in the response body. If the credentials are invalid or missing, it responds with a `401 Unauthorized` status.

### Applying the Middleware

After user login, we are ready to protect our routes using the `jwtAuth` middleware. For example, we can protect the `/profile/me` route using the `@@` syntax:

```scala
val profile =
  Method.GET / "profile" / "me" -> handler { (_: Request) =>
    ZIO.serviceWith[UserInfo](info => Response.text(s"Welcome ${info.username}!"))
  } @@ jwtAuth(realm = "User Profile")
```

The `profile` route listens for `GET` requests at the `/profile/me` endpoint. It uses the `jwtAuth` middleware to ensure that only authenticated users can access this route. If the user is authenticated, it retrieves the user's details and responds with a welcome message including the user's profile information. If the user is not authenticated, it responds with a `401 Unauthorized` status.

## Restricting Access Based on User Roles

In many applications, we need to restrict access to certain routes based on user roles or permissions. For example, we can create an `/admin` route that only allows access to users with the `admin` role.

There are two main approaches to implement role-based access control with JWTs:
1. The traditional approach, where the JWT token only contains the username, and the API service retrieves the user's roles from a user service or database for each request.
2. An enhanced approach, where the JWT token includes all necessary user information (such as roles) in its claims, allowing the API service to make authorization decisions without additional lookups.

### Traditional Approach: Server-Side Role Verification

In this scenario, the `jwtAuth` middleware retrieves the authenticated username from the JWT token and then fetches the user details from the `UserService` and passes the user details (the `User` object) to the next handlers in the request pipeline. The admin route checks if the user has the `admin` role and grants or denies access accordingly:

```scala
val admin =
  Method.GET / "admin" / "users" -> handler { (_: Request) =>
    Handler.fromZIO(ZIO.service[UserService]).flatMap { userService =>
      Handler.fromZIO {
        ZIO.serviceWithZIO[UserInfo] { info =>
          if (info.roles.contains("admin")) userService.getUsers.map { users =>
            val userList = users.map(u => s"${u.username} (${u.email}) - Role: ${u.roles}").mkString("\n")
            Response.text(s"User List:\n$userList")
          }
          else
            ZIO.fail(Response.unauthorized(s"Access denied. User ${info.username} is not an admin."))
        }
      } @@ jwtAuth(realm = "Admin Area")
    }
  } 
```

In this authentication pattern, we only include the username as a JWT claim. Additional information, such as the user's role, is not included in the JWT token. Therefore, it is the responsibility of the middleware to fetch the complete user details from the `UserService` after verifying the token. 

While this approach works well for monolithic applications, it presents challenges in microservice architectures. Consider a scenario where administrative operations are handled by a separate, independent service. How can this microservice verify if the requesting user has admin privileges? Using the traditional approach, all protected routes using the `jwtAuth` middleware would require the `UserService` to verify user roles.

With the traditional approach, the microservice must make an API call to the `UserService` to verify the user's roles. Although functional, this creates an unnecessary dependency—the microservice becomes coupled to the `UserService` solely for role verification.

### Enhanced Approach: Encoding Roles in JWT Claims

A more efficient approach is to encode all required information directly in the JWT claims. When a user initially authenticates through the login API, the server includes the necessary fields (such as roles) in the token's claims. In subsequent requests to protected areas, all essential user information is already encoded within the token.

Let's revise the `jwtAuth` middleware to implement this approach. The original middleware had the type `HandlerAspect[JwtTokenService & UserService, UserInfo]`, which required calling the `UserService` after verifying the JWT token to retrieve user details.

In our new implementation, we eliminate the dependency on `UserService` since all required fields are contained within the JWT claims:

```scala
object AuthMiddleware {
  def jwtAuth(realm: String): HandlerAspect[JwtTokenServiceClaim, UserInfo] =
    HandlerAspect.interceptIncomingHandler {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            ZIO
              .serviceWithZIO[JwtTokenServiceClaim](_.verify(token.value.asString))
              .map(userInfo => (request, userInfo))
              .orElseFail(
                Response
                  .unauthorized("Invalid or expired token.")
                  .addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))),
              )
          case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))))
        }
      }
    }
}
```

The middleware now verifies the received token, extracts the user information, and passes it to subsequent handlers in the request pipeline. Note that the middleware type has changed to `HandlerAspect[JwtTokenService, UserInfo]`, eliminating the `UserService` dependency.

### Updating the JWT Service

We need to update the `JwtTokenService` interface to support the enhanced claims:

```scala
trait JwtTokenService {
  def issue(username: String, email: String, roles: Set[String]): UIO[String]
  def verify(token: String): Task[UserInfo]
}
```

The `issue` method now accepts additional parameters (email and roles), and the `verify` method returns a `UserInfo` object instead of just the username. 

The `JwtTokenService` implementation must be updated to include additional fields in the claims:

```scala
import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm
import zio.Config.Secret
import zio._
import zio.json._

import java.time.Clock

case class JwtAuthServiceLive(
  secretKey: Secret,
  tokenTTL: Duration,
  algorithm: JwtHmacAlgorithm,
) extends JwtTokenServiceClaim {
  implicit val clock: Clock = Clock.systemUTC

  override def issue(username: String, email: String, roles: Set[String]): UIO[String] =
    ZIO.succeed {
      Jwt.encode(
        claim = JwtClaim(subject = Some(username)).issuedNow
          .expiresIn(tokenTTL.toSeconds)
          .++(("roles", roles))
          .++(("email", email)),
        key = secretKey.stringValue,
        algorithm = algorithm,
      )
    }

  override def verify(token: String): ZIO[Any, Throwable, UserInfo] =
    ZIO
      .fromTry(
        Jwt.decode(token, secretKey.stringValue, Seq(algorithm)),
      )
      .filterOrFail(_.isValid)(new Exception("Token expired"))
      .map(_.toJson)
      .map(UserInfo.codec.decodeJson(_).toOption)
      .someOrFail(new Exception("Invalid token"))
}

object JwtTokenServiceClaim {
  def live(
    secretKey: Secret,
    tokenTTL: Duration = 15.minutes,
    algorithm: JwtHmacAlgorithm = JwtAlgorithm.HS512,
  ): ULayer[JwtAuthServiceClaimLive] =
    ZLayer.succeed(JwtAuthServiceClaimLive(secretKey, tokenTTL, algorithm))
}
```

### Updating Routes

The login route must be updated to pass additional fields to the `issue` method:

```scala
val login =
  Method.POST / "login" ->
    handler { (request: Request) =>
      for {
        form         <- request.body.asURLEncodedForm.orElseFail(Response.badRequest)
        username     <- extractFormField(form, "username")
        password     <- extractFormField(form, "password")
        tokenService <- ZIO.service[JwtTokenServiceClaim]
        user         <- ZIO
          .serviceWithZIO[UserService](_.getUser(username))
          .orElseFail(unauthorizedResponse)
        response     <-
          if (user.password == Secret(password))
            tokenService.issue(username, user.email, user.roles).map(Response.text(_))
          else
            ZIO.fail(unauthorizedResponse)
      } yield response
    }
```

The admin route now uses the `UserInfo` object provided by the middleware:

```scala
val users =
  Method.GET / "admin" / "users" ->
    Handler.fromZIO(ZIO.service[UserService]).flatMap { userService =>
      Handler.fromZIO {
        ZIO.serviceWithZIO[UserInfo] { info: UserInfo =>
          if (info.roles.contains("admin")) userService.getUsers.map { users =>
            val userList = users.map(u => s"${u.username} (${u.email}) - Role: ${u.roles}").mkString("\n")
            Response.text(s"User List:\n$userList")
          }
          else
            ZIO.fail(Response.unauthorized(s"Access denied. User ${info.username} is not an admin."))
        }
      } @@ jwtAuth(realm = "Admin Area")
    }
```

We verify authorization by checking if the `UserInfo` object's roles field contains the "admin" role. If it does, we grant access; otherwise, we respond with a `401 Unauthorized` status. The key advantage is that we no longer need to query the `UserService` for authorization decisions—all necessary information is contained within the JWT token.

## Writing a Client

In this section, we will demonstrate how to write a client application that interacts with the protected API using JWT authentication. The client will perform two main steps: first, it sends a login request to obtain a JWT token, and then it uses the token to access protected routes.

Let's start by implementing a ZIO HTTP client, followed by a JavaScript client.

### Writing a ZIO HTTP Client

To implement the first step, we will create a ZIO HTTP client that sends a `POST` request to the `/login` endpoint with the user's credentials. Upon successful authentication, the server will respond with a JWT token:

```scala
val url = "http://localhost:8080"
val loginUrl = URL.decode(s"$url/login").toOption.get

val loginRequest =
    ZClient
      .batched(
        Request
          .post(loginUrl,
            Body.fromURLEncodedForm(
              Form(
                FormField.simpleField("username", "john"),
                FormField.simpleField("password", "password123"),
              ),
            ),
          )
      )
      .flatMap(_.body.asString)
```

Making the login request will return the JWT token as a string.

Next, we will use the received JWT token to access a protected route, such as `/profile/me`. We will include the token in the `Authorization` header of the request:

```scala
val greetUrl = URL.decode(s"$url/profile/me").toOption.get

loginRequest.flatMap { token =>
  ZClient.batched(Request.get(greetUrl).addHeader(Header.Authorization.Bearer(token)))
}
```

The client sends a `GET` request to the `/profile/me` endpoint, including the JWT token in the `Authorization` header. If the token is valid, the server will respond with the user's profile information.

### Writing a JavaScript Client

It’s straightforward to create a JavaScript client—here are the steps:

1. Create a login form to collect user credentials.
2. Send a login request to obtain a JWT token.
3. Use the JWT token to access protected routes.

#### Login Form

To obtain a JWT token, we need to create a simple login form that takes the username and password from the user:

```html
<div id="loginForm">
  <input name="username" id="username" placeholder="username (try: john, jane, or admin)">
  <input name="password" id="password" type="password" placeholder="password (john: password123, jane: secret456, admin: admin123)">
  <button onclick="login()">Login & Get JWT</button>
</div>
```

The form includes two input fields for the username and password, and a button that triggers the `login` function when clicked. When the button is clicked, the `login` function sends a `POST` request to the `/login` endpoint with the provided credentials:

```javascript
let currentToken = null;

async function login() {
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    try {
        const res = await fetch('/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ username, password })
        });

        const token = res.ok ? await res.text() : null;
        setTokenState(token);
    } catch(e) {
        setTokenState(null);
    }
}
```

We store the received JWT token in the `currentToken` variable for later use.

### Sending Authenticated Requests

Now that we have the JWT token, we can use it to access protected routes. For example, we can create a button that fetches the user's profile information from the `/profile/me` endpoint:

```html
<div class="section">
  <h3>Endpoints:</h3>
  <button onclick="fetchPublic()">Public Endpoint (No Auth)</button>
  <button onclick="fetchProfile()">Get Profile (Requires JWT)</button>
  <button onclick="fetchAllUsers()">Admin Users (Requires Admin JWT)</button>
</div>

<pre id="result">Results will appear here...</pre>
```

The `fetchProfile` function sends a `GET` request to the `/profile/me` endpoint, including the JWT token in the `Authorization` header:

```javascript
async function fetchProfile() {
    if (!currentToken) {
        document.getElementById('result').textContent = 'Please login first to get a JWT token';
        return;
    }

    try {
        const res = await fetch('/profile/me', {
            headers: { 'Authorization': `Bearer ${currentToken}` }
        });
        const text = await res.text();
        document.getElementById('result').textContent = `${text}`;
    } catch(e) {
        document.getElementById('result').textContent = `Error: ${e.message}`;
    }
}
```

We can do the same for all protected routes, such as the admin users endpoint.

To improve usability, we can handle token expiration by implementing a refresh token mechanism on both the server and client.

## Conclusion

JSON Web Tokens have revolutionized API authentication by providing a stateless, scalable solution that aligns perfectly with modern architectural patterns. Throughout this guide, we've explored the journey from traditional session-based authentication to the token-based approach that dominates today's distributed systems. Here are some key takeaways:

**JWTs excel in specific scenarios**: They're ideal for RESTful APIs, microservices architectures, cross-domain authentication, and temporary access tokens. Their self-contained nature eliminates database lookups and enables true horizontal scaling. When you need stateless authentication that works seamlessly across multiple services and domains, JWTs are often the perfect choice.

**Security requires careful consideration**: Remember that JWTs are encoded, not encrypted. The payload is visible to anyone who intercepts the token, making HTTPS mandatory for production deployments. Never store sensitive information like passwords or credit card numbers in JWT payloads, and always validate tokens properly on the server side.

**Choose your signing strategy wisely**: Symmetric signing offers simplicity and performance for single-service architectures, while asymmetric signing provides better security and flexibility for distributed systems. Your choice should align with your architecture's complexity and security requirements.

**Token management matters**: The stateless nature of JWTs means they can't be revoked before expiration without additional infrastructure. Keep token lifetimes short, implement refresh token patterns for long-lived sessions, and consider maintaining a token blacklist for emergency revocations if your security requirements demand it.

**Implementation patterns affect scalability**: The decision to include user roles and permissions in JWT claims versus fetching them from a database on each request has significant implications. Including them in the token reduces database load and improves performance but makes permission changes slower to propagate. Choose the pattern that best fits your application's needs.
