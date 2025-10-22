---
id: basic-authentication
title: "Securing Your APIs: Basic Authentication"
sidebar_label: "Basic Authentication"
---

Basic Authentication is one of the simplest HTTP authentication schemes, widely used for securing web applications and APIs. In this guide, we'll explore how to implement Basic Authentication using ZIO HTTP, a powerful and type-safe HTTP library for Scala.

## Understanding Basic Authentication

Basic Authentication is an HTTP authentication scheme that transmits credentials as username/password pairs, encoded in Base64. When a client makes a request, it includes an `Authorization` header with the format:

```
Authorization: Basic <base64-encoded-credentials>
```

The credentials are encoded as `username:password` and then Base64 encoded. For example, if the username is `john` and the password is `secret123`, we need to encode the `john:secret123` string to Base64:

```bash
$ echo -n "john:secret123" | base64
am9objpzZWNyZXQxMjM=
```

So the header would be:

```
Authorization: Basic am9objpzZWNyZXQxMjM=
```

When the server receives a request with this header, it decodes the credentials and checks them against a user database or service. If the credentials are valid, the server allows access to the requested resource; otherwise, it responds with a `401 Unauthorized` status.

## Setting Up Dependencies

First, add the necessary dependencies to our `build.sbt`:

```scala 
libraryDependencies ++= Seq(
  "dev.zio" %% "zio"      % "@ZIO_VERSION@",
  "dev.zio" %% "zio-http" % "@VERSION@",
)
```

## Implementing Basic Authentication

### 1. Creating the Authentication Service

Assume we have a simple in-memory user store that holds usernames, passwords, and roles:

```scala mdoc:silent
import zio.Config.Secret

case class User(username: String, password: Secret, email: String, role: String)

// Sample user database
val users = Map(
  "john"  -> User("john", Secret("secret123"), "john@example.com", "user"),
  "jane"  -> User("jane", Secret("password456"), "jane@example.com", "user"),
  "admin" -> User("admin", Secret("admin123"), "admin@example.com", "admin"),
)
```

:::note
In a real application, we would typically use a database or an external service for user management. Also, in production environments, we should never store passwords in plain text, as we will discuss later in the security best practices section.
:::

### 2. Creating Basic Authentication Middleware

Next, we'll create middleware that handles Basic Authentication:

```scala mdoc:silent
import zio._
import zio.http._

val basicAuthWithUserContext: HandlerAspect[Any, User] =
  HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
    request.header(Header.Authorization) match {
      case Some(Header.Authorization.Basic(username, password)) =>
        users.get(username) match {
          case Some(user) if user.password == password =>
            ZIO.succeed((request, user))
          case _                                       =>
            ZIO.fail(
              Response
                .unauthorized("Invalid username or password")
                .addHeaders(Headers(Header.WWWAuthenticate.Basic(realm = Some("Protected API")))),
            )
        }
      case _                                                    =>
        ZIO.fail(
          Response
            .unauthorized("Authentication required")
            .addHeaders(Headers(Header.WWWAuthenticate.Basic(realm = Some("Protected API")))),
        )
    }
  })
```

This middleware checks for the `Authorization` header, decodes the credentials, and verifies them against our in-memory user store. If the credentials are valid, it allows the request to proceed by attaching the user data to the request context. If the credentials are invalid or missing, it responds with a `401 Unauthorized` status and a `WWW-Authenticate` header prompting for Basic Authentication.

### 3. Creating Protected Routes

Now that the authentication middleware is ready, we can apply it to any routes that we want to protect:

```scala mdoc:silent
import zio.http._

def routes: Routes[Any, Response] =
  Routes(
    // Public route - no authentication required
    Method.GET / "public" -> handler { (_: Request) =>
      Response.text("This is a public endpoint accessible to everyone")
    },

    // Route that uses the full User object
    Method.GET / "profile" / "me" -> handler { (_: Request) =>
      ZIO.serviceWith[User](user =>
        Response.text(s"Welcome ${user.username}!\nEmail: ${user.email}\nRole: ${user.role}"),
      )
    } @@ basicAuthWithUserContext,
  ) @@ Middleware.debug
```

In this example, we have a `/public` route that anyone can access, and a protected route `/profile/me` that requires authentication. The `basicAuthWithUserContext` middleware is applied to the protected route, allowing us to access the authenticated user information within the handler.

Note that the `basicAuthWithUserContext` middleware is applied to the route. This allows us to access the authenticated user information in the handler through the ZIO environment. We used `ZIO.serviceWith[User]` to access the user context, which was set by the middleware. This enables us to personalize responses based on the user's data.

### 4. Putting It All Together

Here's how we combine everything into a complete application:

```scala mdoc:silent
import zio.Config.Secret
import zio._
import zio.http._

object AuthenticationServer extends ZIOAppDefault {
  val basicAuthWithUserContext: HandlerAspect[Any, User] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(Header.Authorization.Basic(username, password)) =>
          users.get(username) match {
            case Some(user) if user.password == password =>
              ZIO.succeed((request, user))
            case _                                       =>
              ZIO.fail(
                Response
                  .unauthorized("Invalid username or password")
                  .addHeaders(Headers(Header.WWWAuthenticate.Basic(realm = Some("Protected API")))),
              )
          }
        case _                                                    =>
          ZIO.fail(
            Response
              .unauthorized("Authentication required")
              .addHeaders(Headers(Header.WWWAuthenticate.Basic(realm = Some("Protected API")))),
          )
      }
    })

  def routes: Routes[Any, Response] =
    Routes(
      // Public route - no authentication required
      Method.GET / "public" -> handler { (_: Request) =>
        Response.text("This is a public endpoint accessible to everyone")
      },

      // Route that uses the full User object
      Method.GET / "profile" / "me" -> handler { (_: Request) =>
        ZIO.serviceWith[User](user =>
          Response.text(s"Welcome ${user.username}!\nEmail: ${user.email}\nRole: ${user.role}"),
        )
      } @@ basicAuthWithUserContext,
    ) @@ Middleware.debug

  override val run = Server.serve(routes).provide(Server.default)

}
```

This will serve the application on the default port (8080) and provide both public and protected routes. Now it is time to test the Basic Authentication by accessing the `/profile/me` endpoint with valid credentials.

## Testing the Protected Route

To test the Basic Authentication, we can use tools like `curl`, Postman, or any HTTP client library. Here's how to do it with `curl`:

```bash
$ curl -v -H "Authorization: Basic am9objpzZWNyZXQxMjM=" -X GET http://127.0.0.1:8080/profile/me
> GET /profile/me HTTP/1.1
> Host: 127.0.0.1:8080
> User-Agent: curl/8.4.0
> Accept: */*
> Authorization: Basic am9objpzZWNyZXQxMjM=
>
< HTTP/1.1 200 Ok
< content-type: text/plain
< date: Thu, 17 Jul 2025 17:32:41 GMT
< content-length: 48
<
Welcome john!
Email: john@example.com
Role: user
```

If you try to access the protected route without providing valid credentials, you will receive a `401 Unauthorized` response:

```bash
$ curl -v -X GET http://127.0.0.1:8080/profile/me
> GET /profile/me HTTP/1.1
> Host: 127.0.0.1:8080
> User-Agent: curl/8.4.0
> Accept: */*
>
< HTTP/1.1 401 Unauthorized
< www-authenticate: Basic realm="Protected API", UTF-8="UTF-8"
< date: Thu, 17 Jul 2025 17:34:27 GMT
< content-length: 23
<
* Connection #0 to host 127.0.0.1 left intact
Authentication required⏎
```

The `www-authenticate: Basic realm="Protected API"` header indicates that the server expects Basic Authentication credentials.

## Writing a Client to Test Basic Authentication

### ZIO HTTP Client

We can create a simple client to test the Basic Authentication implementation:

```scala mdoc:silent
import zio._
import zio.http._

object AuthenticationClient extends ZIOAppDefault {

  val profileMeUrl  = URL.decode(s"http://localhost:8080/profile/me").toOption.get

  val program =
    for {
      _         <- Console.printLine(s"=== Accessing profile/me with john's credentials ===")
      response  <- ZClient.batched(Request.get(profileMeUrl).addHeader(Header.Authorization.Basic("john", "secret123")))
      body     <- response.body.asString
      _         <- Console.printLine(s"Status: ${response.status}")
      _         <- Console.printLine(s"Response: $body")
    } yield ()

  override val run = program.provide(Client.default)
}
```

By adding the `Header.Authorization.Basic("john", "secret123")` header, the ZIO HTTP Client will encode the credentials and send them in the request.

### Web Client

To create a simple web client that interacts with the Basic Authentication endpoint, we can use HTML and JavaScript. Below is an example of a basic HTML page that allows users to enter their username and password, and then fetch their profile using Basic Authentication:

```html
// src/main/resources/basic-auth-client.html
<!DOCTYPE html>
<html>
  <head><title>Basic Authentication</title></head>
  <body>
    <h1>Basic Authentication</h1>
    <input type="text"     id="user" placeholder="Username">
    <input type="password" id="pass" placeholder="Password">
    <button onclick="getMyProfile()">Get My Profile</button>

    <p id="result"></p>

    <script>
      async function getMyProfile() {
        const user = document.getElementById('user').value;
        const pass = document.getElementById('pass').value;

        const response = await fetch('/profile/me', {
          headers: {
              'Authorization': 'Basic ' + btoa(user + ':' + pass)
          }
        });

        document.getElementById('result').innerHTML = await response.text();
      }
    </script>
  </body>
</html>
```

We used `btoa()` to encode the username and password in Base64 format, which is required for Basic Authentication. When the user clicks the "Get My Profile" button, it sends a request to the `/profile/me` endpoint with the provided credentials.

Now, it's time to serve this HTML page using ZIO HTTP. We can create a simple server that serves this HTML file. First, make sure to place the HTML file in the resources directory of our project, for example, `src/main/resources/basic-auth-client.html`. Then, let's add the following route to the existing `routes`:

```scala mdoc:silent:nest
import zio._
import zio.http._
import zio.stream.{ZPipeline, ZStream}

val routes =
  Routes(
    Method.GET / Root -> handler { (_: Request) =>
      ZStream
        .fromResource("basic-auth-client.html")
        .via(ZPipeline.utf8Decode)
        .runCollect
        .map(_.mkString)
        .map { htmlContent =>
          Response(
            status = Status.Ok,
            headers = Headers(Header.ContentType(MediaType.text.html)),
            body = Body.fromString(htmlContent),
          )
        }
        .orElseFail(
          Response.internalServerError("Failed to load HTML file"),
        )
    }
    
    // other routes ...
  )
```

Now if we run the server and open localhost:8080 in a web browser, we can enter the username and password to fetch the profile information using Basic Authentication.

## Custom User Service

Our first implementation used an immutable in-memory map for user storage. In a real-world application, we would typically have a separate user service that interacts with a database or an external authentication provider. So, let's create a separate user service that can have different implementations, such as in-memory, database-backed, or even an external API.

```scala mdoc:silent
case class User(
  username: String,
  password: Secret,
  email: String,
  role: String
)

trait UserService {
  def authenticate(username: String, password: Secret): UIO[Option[User]]
}

case class InMemoryUserService(private val users: Ref[Map[String, User]]) 
  extends UserService {
  def authenticate(username: String, password: Secret): UIO[Option[User]] =
    users.get.map(_.get(username).find(user => user.password == password))
}

object InMemoryUserService {
  def make(users: Map[String, User]): UIO[UserService] =
    Ref.make(users).map(new InMemoryUserService(_))

  private val users = Map(
    "john"  -> User("john", Secret("secret123"), "john@example.com", "user"),
    "jane"  -> User("jane", Secret("password456"), "jane@example.com", "user"),
    "admin" -> User("admin", Secret("admin123"), "admin@example.com", "admin"),
  )
  
  val live: ZLayer[Any, Nothing, UserService] =
    ZLayer.fromZIO(make(users))
}
```

Now we can use this `UserService` in our authentication middleware:

```scala mdoc:silent
import zio.Config.Secret
import zio._
import zio.http._

import java.nio.charset.StandardCharsets
import scala.io.Source

object AuthenticationServer extends ZIOAppDefault {

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
    ) @@ Middleware.debug

  override val run = Server.serve(routes).provide(Server.default, InMemoryUserService.live)
  
}
```

In this example, we demonstrated writing middleware that uses a `UserService` to authenticate users and return the user as part of the request context. This allows for a more modular and testable approach to authentication, as we can easily swap out the `UserService` implementation without changing the middleware logic.

## Security Best Practices

While it is simple to implement, there are some security concerns with Basic Authentication, so we should follow several security best practices to ensure the safety of our application and its users. Here are some key recommendations:

### 1. Use HTTPS Only

The credentials are sent in an easily decodable format, which means they can be intercepted if not transmitted over a secure channel (HTTPS). Therefore, it is crucial to use Basic Authentication only over HTTPS to protect the credentials from being exposed during transmission.

### 2. Password Hashing

Another important aspect is that the server should not store passwords in plain text. If the database is compromised, attackers can easily access user credentials. So, we should use secure hashing algorithms to store hashed passwords, and compare the hashed version of the provided password with the stored hash.

To store users securely without the risk of compromising the user's passwords, the system first receives a username and password during registration. It then hashes the password using a hash function and stores the username along with the resulting hashed password in a user database.

For authentication, the system receives a username and password from the user trying to log in. It looks up the user by username in the database. If the user exists, the system hashes the entered password using the same hash function and compares it to the stored hashed password. If they match, the login is successful; otherwise, it fails. If the username is not found in the database, the login fails immediately.

This approach is simple and easy to implement, but insecure for real-world use. Without salting or a slow hashing algorithm like bcrypt or argon2, it is vulnerable to brute-force and rainbow table attacks. A rainbow table is a precomputed list of common passwords and their hash values. If we store hashes without a salt (a random value added to the password before hashing), then:

- Anyone with access to our database can match the hash to known passwords instantly.
- Two users with the same password will have identical hashes, revealing password reuse.

Also, not all hashing methods are suitable for storing passwords. Fast hashing algorithms like SHA-256 or MD5 are not suitable because they allow attackers to try many passwords quickly. Instead, use a slow hashing algorithm like **bcrypt**, **scrypt**, or **argon2**, which are designed for securely hashing passwords.

So, never store passwords in plain text. Always use proper password hashing with salt.

### 3. Implement Rate Limiting

To make our Basic Authentication more secure, it is recommended to implement rate limiting to prevent brute force attacks. Rate limiting restricts the number of requests a user can make in a given time period, which helps mitigate the risk of attackers trying to guess passwords through repeated attempts.

As the details of the implementation are out of scope for this article, we only provide a simple interface for such middleware:

```scala
def rateLimit(maxRequests: Int, windowSeconds: Int): Middleware[Any] = {
  // Implementation would track requests per IP and time window
}
```

We will discuss rate limiting in more detail in a separate article.

## Source Code

The complete source code for this Basic Authentication example is available in the ZIO HTTP examples repository. The example includes both server and client implementations, along with an interactive web-based demo.

To clone the example:

```scala
git clone --depth 1 --filter=blob:none --sparse https://github.com/zio/zio-http.git
cd zio-http
git sparse-checkout set zio-http-example-basic-auth
```


The example contains the following files:

- **AuthenticationServer.scala** - The server implementation with secure password hashing
- **AuthenticationClient.scala** - A simple ZIO HTTP-based client demonstrating API calls
- **basic-auth-client.html** - An interactive web interface for testing (in resources)
- **basic-auth-client-simple.html** - A simplified interactive web interface for testing (in resources)

### Running the Server

To run the authentication server:

```bash
cd zio-http
sbt "zio-http-example/runMain example.auth.basic.AuthenticationServer"
```

The server will start on `http://localhost:8080` with the following test users:

| Username | Password      | Role  | Description                            |
|----------|---------------|-------|----------------------------------------|
| `john`   | `secret123`   | user  | Standard user account                  |
| `jane`   | `password456` | user  | Standard user account                  |
| `admin`  | `admin123`    | admin | Admin account with elevated privileges |

### Running the Client (ZIO HTTP Client)

To run the command-line client (make sure the server is running first):

```bash
sbt "zio-http-example/runMain example.auth.basic.AuthenticationClient"
```

The client will demonstrate:
1. Accessing a public endpoint (no authentication)
2. Accessing a protected profile endpoint with valid credentials
3. Accessing an admin-only endpoint

Here is an example output from the client:

```
=== Basic Authentication Client Example ===

--- Accessing public endpoint ---
Status: Ok
Response: This is a public endpoint accessible to everyone

--- Accessing profile/me with john's credentials ---
Status: Ok
Response: Welcome john!
Email: john@example.com
Role: user

--- Accessing admin/users with admin credentials ---
Status: Ok
Response: User List (accessed by admin):
john (john@example.com) - Role: user
jane (jane@example.com) - Role: user
admin (admin@example.com) - Role: admin

=== All requests completed ===
```

### Testing with the Web Interface

After starting the server, open your web browser and navigate to:

```
http://localhost:8080
```

This will load an interactive web interface where you can:

- Select from pre-configured test users
- See real-time HTTP request/response messages
- Test different endpoints with various credentials
- Observe authentication success and failure scenarios

## Demo

We have deployed a live demo of the Basic Authentication example application. You can access it at: [https://basic-auth-demo.ziohttp.com/](https://basic-auth-demo.ziohttp.com/). By using this demo, you can explore the functionality of Basic Authentication implemented with ZIO HTTP. You can see the HTTP traffic in your browser's developer tools to understand how the authentication process works.

## Conclusion

Through this guide, we've explored how to implement authentication middleware, create protected routes, and integrate user services in a modular and testable way. 

While Basic Authentication is simple to implement and understand, it's crucial to follow security best practices in production environments. Always use HTTPS to protect credentials in transit, implement proper password hashing with salt using algorithms like bcrypt or argon2, and consider additional security measures such as rate limiting to prevent brute force attacks. For applications requiring more sophisticated authentication mechanisms, consider exploring other options like JWT tokens, OAuth 2.0, or session-based authentication.
