---
id: cookie-based-authentication
title: "Securing Your APIs: Cookie-based Authentication"
sidebar_label: "Cookie-based Authentication"
---

Session-based authentication using cookies is one of the most common authentication mechanisms for web applications. In this guide, we demonstrate how to implement a robust cookie-based authentication system in ZIO HTTP, covering both server-side implementation and client integration.

In this authentication model, when a user logs in successfully, the server creates a session and sends a session identifier to the client as a cookie. The client automatically includes this cookie in subsequent requests, allowing the server to identify and authenticate the user.

## Understanding Set-Cookie and Cookie Headers

The foundation of cookie-based authentication lies in two HTTP headers: `Set-Cookie` (server to client) and `Cookie` (client to server). Understanding these headers is crucial for implementing secure authentication.

### The Set-Cookie Header (Server → Client)

The `Set-Cookie` header is used by the server to send cookies to the client. When a user successfully authenticates, the server creates a session and sends the session identifier to the client using this header.

In ZIO HTTP, we can create a `Set-Cookie` header using the `Cookie.Response` data type:

```scala mdoc:silent
import zio._
import zio.http._

val cookie = Cookie.Response(
  name = "session_id",                    // Cookie name
  content = "abc123def456",               // Cookie value (session ID)
  domain = Some("example.com"),           // Cookie scope by domain
  path = Some(Path.root),                 // Cookie scope by path
  isSecure = true,                        // HTTPS only
  isHttpOnly = true,                      // Not accessible via JavaScript
  maxAge = Some(3600.seconds),            // Lifetime in seconds
  sameSite = Some(Cookie.SameSite.Strict) // CSRF protection
)
```

Here is a concise breakdown of the attributes used in the `Cookie.Response`:

- **`name`**: The name of the cookie (e.g., `session_id`).
- **`content`**: The value of the cookie, typically a session identifier.
- **`domain`**: The domain for which the cookie is valid. It defines which hosts (subdomains) can receive the cookie. If no domain is specified, the cookie is valid for the host that set it. If a domain is specified, the cookie will be sent to that domain and its subdomains.
- **`path`**: The path for which the cookie is valid. The server can include this attribute to restrict the cookie to specific paths. If no path is specified, the cookie is sent only to the same path as the resource that set it and its subdirectories. If a path is specified, the cookie will be sent to that path and its subdirectories.
- **`isSecure`**: If `true`, the cookie is only sent over HTTPS connections. This prevents the cookie from being transmitted over unencrypted HTTP, enhancing security.
- **`isHttpOnly`**: If `true`, the cookie is not accessible via JavaScript, mitigating the risk of cross-site scripting (XSS) attacks.
- **`maxAge`**: The maximum age of the cookie in seconds. After this time, the cookie will be deleted by the browser. If not specified, the cookie is a **session cookie** and will be deleted when the browser is closed.
- **`sameSite`**: Controls whether the cookie is sent with cross-site requests. The `SameSite` attribute can be set to `Strict`, `Lax`, or `None`. Setting it to `Strict` provides the highest level of CSRF protection, while `Lax` allows some cross-site requests (e.g., top-level navigations).

We can use this cookie in the login flow. When a user provides valid credentials, we create a session and send it back to the client as a `Set-Cookie` header:

```scala mdoc:invisible
import zio.Config._
object user {
  val password: Secret = Secret("123")
}
val password = "123"
val username = "john"
```

```scala mdoc:silent
if (user.password == Secret(password)) {
  Response
    .text(s"Login successful! Session created for $username")
    .addCookie(cookie)
} else
  Response.unauthorized("Invalid username or password.")
```

### The Cookie Header (Client → Server)

After the server sends the `Set-Cookie` header, the client (usually the browser) stores the cookie and automatically includes it in subsequent requests to the same domain. The client sends this cookie using the `Cookie` header.

For example, if we have a cookie named `session_id`, the client will include it in requests like this:

```scala
GET /profile/me HTTP/1.1
Host: localhost:8080
Cookie: session_id=0f547819-2bde-4405-8ea5-986954bc9ee6
```

In this example, the client sends the `session_id` cookie to the server when accessing the `/profile/me` endpoint. The server can then validate this session ID to authenticate the user.

In ZIO HTTP, when writing client code, we don't need to manually create a `Cookie` header; instead, we can convert the received `Set-Cookie` header into a `Cookie` object and use it in subsequent requests:

```scala mdoc:invisible
val SERVER_URL = "http://localhost:8080"
val loginUrl   = URL.decode(s"$SERVER_URL/login").toOption.get
val profileUrl = URL.decode(s"$SERVER_URL/profile/me").toOption.get
```

```scala mdoc:compile-only
for {
  loginResponse <- ZClient.batched(
    Request
      .post(
        url = loginUrl,
        body = Body.fromURLEncodedForm(
          Form(
            FormField.simpleField("username", "john"),
            FormField.simpleField("password", "password123"),
          ),
        ),
      ),
  )
  cookie = loginResponse.headers(Header.SetCookie).head.value.toRequest
  _ <- Console.printLine("Accessing protected route...")
  greetResponse <- ZClient.batched(Request.get(profileUrl).addCookie(cookie))
} yield ()
```

## Implementation

In this section, we will implement a complete cookie-based authentication system using ZIO HTTP. Before we start, we need to define and implement some services that will help us manage user sessions and user accounts.

1. **Session Service**: This service will manage user sessions, allowing us to create, retrieve, and remove sessions.
2. **User Service**: This service will manage user accounts, allowing us to retrieve and create users. We need the user service to validate user credentials during login and also to retrieve user profile information.

Let's first implement these two services.

### 1. Session Service

Here is a simple in-memory session service that manages user sessions. It allows creating, retrieving, and removing sessions:

```scala mdoc:silent
class SessionService private(private val store: Ref[Map[String, String]]) {
  private def generateSessionId(): UIO[String] =
    ZIO.randomWith(_.nextUUID).map(_.toString)

  def create(username: String): UIO[String] =
    for {
      sessionId <- generateSessionId()
      _         <- store.update(_ + (sessionId -> username))
    } yield sessionId

  def get(sessionId: String): UIO[Option[String]] =
    store.get.map(_.get(sessionId))

  def remove(sessionId: String): UIO[Unit] =
    store.update(_ - sessionId)
}
```

Here is how to create a live layer for the `SessionService`:

```scala mdoc:silent
object SessionService {
  def live: ZLayer[Any, Nothing, SessionService] =
    ZLayer.fromZIO {
      Ref.make(Map.empty[String, String]).map(new SessionService(_))
    }
}
```

### 2. User Service

The user service manages user accounts, allowing retrieval and creation of users. It also handles errors related to user operations, such as user not found or user already exists:

```scala mdoc:silent
import zio._
import zio.Config._
import UserServiceError._

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

To create a live layer for the `UserService`, let's initialize it with some predefined users:

```scala mdoc:silent
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

Now we have all the required services to write the login route and then implement cookie-based authentication middleware.

The next step is to implement the login route that will authenticate users and create sessions.

### Login Route

The login route is responsible for receiving user credentials (username and password), validating them, and creating a session if the credentials are correct:

1. **Parse and validate** - Extracts username and password from URL-encoded form data, returning bad request errors if fields are missing
2. **Authenticate** - Retrieves user from `UserService` and verifies the password matches, returning unauthorized if user not found or password incorrect
3. **Create session** - Generates a session ID via `SessionService` and attaches a session cookie with security settings to the success response

```scala mdoc:silent
val login =
   Method.POST / "login" ->
     handler { (request: Request) =>
       val form = request.body.asURLEncodedForm.orElseFail(Response.badRequest("Invalid form data"))
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
         user     <- users
           .getUser(username)
           .orElseFail(
             Response.unauthorized("Invalid username or password."),
           )
         res      <-
           if (user.password == Secret(password)) {
             for {
               sessionService <- ZIO.service[SessionService]
               sessionId      <- sessionService.create(username)
               cookie = Cookie.Response(
                 name = "session_id",
                 content = sessionId,
                 maxAge = Some(300.seconds),
                 isHttpOnly = false, // Set to true in production to prevent XSS attacks
                 isSecure = false,   // Set to true in production with HTTPS
                 sameSite = Some(Cookie.SameSite.Strict),
               )
             } yield Response
               .text(s"Login successful! Session created for $username")
               .addCookie(cookie)
           } else
             ZIO.fail(Response.unauthorized("Invalid username or password."))
       } yield res
     }
```

:::note
In production environments, we should set `isHttpOnly = true` and `isSecure = true` for the session cookie to enhance security. This prevents client-side scripts from accessing the cookie and ensures it is only sent over secure HTTPS connections.
:::

### Authentication Middleware

After implementing the login route, we can now create middleware that will intercept incoming requests and check for a valid session cookie. If the cookie is present and valid, it will allow access to protected resources; otherwise, it will return an unauthorized response.

We can write it as a `HandlerAspect` like this:

```scala mdoc:silent
import zio._
import zio.http._

object AuthMiddleware {
  def cookieAuth(cookieName: String = "session_id"): HandlerAspect[SessionService & UserService, User] =
    HandlerAspect.interceptIncomingHandler {
      Handler.fromFunctionZIO[Request] { request =>
        ZIO.serviceWithZIO[SessionService] { sessionService =>
          request.cookie(cookieName) match {
            case Some(cookie) =>
              sessionService.get(cookie.content).flatMap {
                case Some(username) =>
                  ZIO
                    .serviceWithZIO[UserService](_.getUser(username))
                    .map(u => (request, u))
                    .orElseFail(
                      Response.unauthorized(s"User not found!"),
                    )
                case None           =>
                  ZIO.fail(Response.unauthorized("Invalid or expired session!"))
              }
            case None         =>
              ZIO.fail(Response.unauthorized("No session cookie found!"))
          }
        }
      }
    }
}
```

If the cookie is present and valid, it retrieves the associated user and allows access to protected resources by passing the original request along with the authenticated user to the downstream handlers. If not, it returns an unauthorized response.

## Applying Middleware

This middleware can be applied to any route that requires authentication. For example, to protect a user profile route, we can use it like this:

```scala mdoc:silent
val profile =
  Method.GET / "profile" / "me" -> handler { (_: Request) =>
    ZIO.serviceWith[User](user =>
      Response.text(
        s"Welcome ${user.username}! " +
          s"This is your profile: \n Username: ${user.username} \n Email: ${user.email}",
      ),
    )
  } @@ AuthMiddleware.cookieAuth("session_id")
```

## Writing a ZIO HTTP Client

While web browsers handle cookies automatically, when building a programmatic client using ZIO HTTP, we need to explicitly manage cookies in our requests. This section demonstrates how to build a ZIO HTTP client that can authenticate with our cookie-based server and access protected resources.

Unlike browser-based clients where cookies are automatically stored and sent, a ZIO HTTP client requires explicit cookie management:
1. **Sending credentials** to the login endpoint
2. **Extracting the cookie** from the `Set-Cookie` response header
3. **Including the cookie** in subsequent requests to protected endpoints

Let's build a client that authenticates with our server and accesses protected resources.

### Step 1: Making a Login Request

To authenticate, we send a POST request with URL-encoded form data containing the username and password:

```scala mdoc:silent:nest
val SERVER_URL = "http://localhost:8080"
val loginUrl   = URL.decode(s"$SERVER_URL/login").toOption.get

val loginRequest = Request
  .post(
    url = loginUrl,
    body = Body.fromURLEncodedForm(
      Form(
        FormField.simpleField("username", "john"),
        FormField.simpleField("password", "password123"),
      ),
    ),
  )

val loginResponse = ZClient.batched(loginRequest)
```

The `Body.fromURLEncodedForm` helper creates the appropriate body with the `application/x-www-form-urlencoded` content type, matching what our server expects.

### Step 2: Extracting the Session Cookie

After successful authentication, the server responds with a `Set-Cookie` header containing our session cookie. We need to extract this cookie and convert it for use in subsequent requests:

```scala mdoc:silent:nest
for {
  res <- loginResponse
  cookie = res.headers(Header.SetCookie).head.value.toRequest
} yield ()
```

The `toRequest` method converts a `Cookie.Response` (used in `Set-Cookie` headers) to a `Cookie.Request` (used in `Cookie` headers), handling all the necessary format conversions.

:::note
In the client we have written, we only handle a single cookie for session management. However, in real-world applications, we might need to manage multiple cookies, such as those for CSRF protection or other session-related data. In such scenarios, we should extract all cookies from the response and manage them accordingly.
:::

### Step 3: Using the Cookie for Protected Routes

With the session cookie in hand, we can now access protected endpoints by including the cookie in our requests:

```scala mdoc:silent
val cookie = Cookie.Request(name = "foo", content = "bar")
```

```scala mdoc:silent
val profileUrl = URL.decode(s"$SERVER_URL/profile/me").toOption.get

val protectedRequest = Request
  .get(profileUrl)
  .addCookie(cookie)

val profileResponse = ZClient.batched(protectedRequest)
```

The `addCookie` method adds the cookie to the request's `Cookie` header, authenticating our request with the server.

### Complete Client Implementation

Here's a complete example that demonstrates the full authentication lifecycle:

```scala mdoc:passthrough
utils.printSource("zio-http-example-cookie-auth/src/main/scala/example/auth/session/cookie/AuthenticationClient.scala")
```

## Writing a Web Client

In this section, we will implement a simple web client that interacts with our cookie-based authentication server. The client will allow users to log in, retrieve their profile, and log out using cookies for session management.

### Logging In

First, we need to write a login form to ask the user for their credentials. This form will submit the username and password to the server, which will then create a session and send back a cookie:

```html
<div id="loginForm">
  <input id="user">
  <input id="password" type="password">
  <button onclick="login()">Login</button>
</div>
```

To handle the login, we can write a simple JavaScript function that sends the credentials to the server and handles the response:

```javascript
function setLoginState(isLoggedIn) {
    loginForm.classList.toggle('hide', isLoggedIn);
}

async function login() {
    try {
        const res = await fetch('/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `username=${user.value}&password=${password.value}`
        });
        result.textContent = await res.text();
        setLoginState(res.ok);
    } catch(e) {
        result.textContent = `Error: ${e.message}`;
        setLoginState(false);
    }
}
```

The `login` function sends a POST request to the `/login` endpoint with the username and password. If the response is successful, it toggles the login form to hide it. The `hide` class is a CSS class that hides the element:

```css
.hide { display: none; }
```

Upon successful login, the browser stores the received cookie automatically. This received cookie will be used to authenticate subsequent requests to the server.

The flow is simple: Login → Server creates session → Server sends cookie → Browser stores cookie → Browser sends cookie with every request → Server validates cookie → Server responds with protected data.

The next step is to make authenticated requests to the server using the received session cookie.

### Making Authenticated Requests

Unlike token-based authentication where we manually store and attach tokens, cookies are handled automatically by the browser, making the client implementation much simpler.

To fetch the user profile after logging in, let's write a button that triggers an authenticated request to the server:

```html
<div id="loggedIn" class="hide">
  <button onclick="getProfile()">Get Profile</button>
</div>
<pre id="result">Results will appear here...</pre>
```

We also added a section for displaying the result of the request.

By default, the "Get Profile" button is hidden until the user logs in. When clicked, it calls the `getProfile` function:

```javascript
async function getProfile() {
    try {
        const res = await fetch('/profile/me');
        result.textContent = await res.text();
    } catch(e) {
        result.textContent = `error: ${e.message}`;
    }
}
```

As the `getProfile` function shows, we make a GET request to the `/profile/me` endpoint using the `fetch()` function. By default, the session cookie is sent along with the request, so no additional handling is needed to include the cookie in the request.

:::note
To have more control over cookie handling, we can specify the `credentials` option in the `fetch` call. For example, the following code snippet will ensure that cookies are sent with the request even for cross-origin requests:

```javascript
fetch('/api/endpoint', {
    credentials: 'include'
})
```

In JavaScript, the `credentials` option of the `fetch` method can accept three possible values:
- **`omit`** - Never send cookies, HTTP authentication, or client certificates with the request, even for same-origin requests. This is the most restrictive option.
- **`same-origin`** - Only send credentials (cookies, HTTP authentication, client certificates) when the request is to the same origin. This is the default value if we don't specify the `credentials` option.
- **`include`** - Always send credentials with the request, even for cross-origin requests. This is necessary when we need to send cookies or authentication headers to a different domain.
:::

Now that we've added a div called `loggedIn` which contains all the elements that need to be visible after logging in, let's update the `setLoginState` function:

```javascript
function setLoginState(isLoggedIn) {
    loginForm.classList.toggle('hide', isLoggedIn);
    loggedIn.classList.toggle('hide', !isLoggedIn);
}
```

This toggles both the login form and logged-in-related elements and is called after each call to the login and logout endpoints.

### Checking Session Status on Page Load

Users expect to stay logged in when they refresh the page or return later. On page load, we can check if the user is logged in by making a request to a protected endpoint, e.g., `/profile/me`:

```javascript
window.onload = async () => {
    try {
        const res = await fetch('/profile/me');
        const text = await res.text();
        if (res.ok) {
            result.textContent = 'Session is active!';
            setLoginState(true);
        } else {
            result.textContent = text;
            setLoginState(false);
        }
    } catch(e) {
        result.textContent = `Error: ${e.message}`;
        setLoginState(false);
    }
};
```

This pattern prevents the login form from flashing before showing authenticated content. It's better UX to check authentication first, then render the appropriate UI. We may want to show a loading spinner during this check.

Please note that for simplicity, we used the same `/profile/me` endpoint for checking session status, which also returns user details if authenticated. In real applications, we might want to have a dedicated endpoint for checking session status without returning user details.

### Logging Out

To log out, let's add a corresponding button:

```html
<div id="loggedIn" class="hide">
  <button onclick="getProfile()">Get Profile</button>
  <button onclick="logout()">Logout</button>
</div>
<pre id="result">Results will appear here...</pre>
```

Clearing the cookie on the client side is not enough, and it is good practice to call the server to invalidate the session. This ensures that the session is removed from the server-side store, preventing any further access with that session ID and also returning a cookie with an expired `maxAge` to the client, which will invalidate the stored cookie in the browser:

```javascript
async function logout() {
    try {
        const res = await fetch('/logout');
        result.textContent = await res.text();
        setLoginState(!res.ok);
    } catch(e) {
        result.textContent = `Error: ${e.message}`;
    }
}
```

After successful logout, the client calls `setLoginState` to make the login form visible again and hide the logged-in section.

### Complete Client Implementation

Here is the complete HTML code that includes the login form, profile retrieval, and logout functionality:

```html
<!DOCTYPE html>
<html>
<head>
  <title>Cookie-Based Authentication Demo</title>
  <style>
    body { font-family: monospace; max-width: 600px; margin: 40px auto; padding: 0 20px; }
    input, button { display: block; margin: 10px 0; }
    pre { background: #f0f0f0; padding: 10px; }
    .hide { display: none; }
    h1 { text-align: center; }
  </style>
</head>
<body>
<h1>Cookie-Based Authentication Demo</h1>
<div id="loginForm">
  <input id="user" placeholder="username (try: john)">
  <input id="password" type="password" placeholder="password (try: password123)">
  <button onclick="login()">Login</button>
</div>
<div id="loggedIn" class="hide">
  <button onclick="getProfile()">Get Profile</button>
  <button onclick="logout()">Logout</button>
</div>
<pre id="result">Results will appear here...</pre>
<script>
  function setLoginState(isLoggedIn) {
      loginForm.classList.toggle('hide', isLoggedIn);
      loggedIn.classList.toggle('hide', !isLoggedIn);
  }

  async function login() {
      try {
          const res = await fetch('/login', {
              method: 'POST',
              headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
              body: `username=${user.value}&password=${password.value}`
          });
          result.textContent = await res.text();
          setLoginState(res.ok);
      } catch(e) {
          result.textContent = `Error: ${e.message}`;
          setLoginState(false);
      }
  }
  
  async function logout() {
      try {
          const res = await fetch('/logout');
          result.textContent = await res.text();
          setLoginState(!res.ok);
      } catch(e) {
          result.textContent = `Error: ${e.message}`;
      }
  }
  
  async function getProfile() {
      try {
          const res = await fetch('/profile/me');
          result.textContent = await res.text();
      } catch(e) {
          result.textContent = `error: ${e.message}`;
      }
  }
  
  window.onload = async () => {
      try {
          const res = await fetch('/profile/me');
          const text = await res.text();
          if (res.ok) {
              result.textContent = 'Session is active!';
              setLoginState(true);
          } else {
              result.textContent = text;
              setLoginState(false);
          }
      } catch(e) {
          result.textContent = `Error: ${e.message}`;
          setLoginState(false);
      }
  };
</script>
</body>
</html>
```

After placing this HTML file in our resources directory and naming it `cookie-based-auth-client-simple.html`, we can serve it using the following ZIO HTTP route:

```scala mdoc:silent
val route = 
  Method.GET / Root ->
    Handler
      .fromResource("cookie-based-auth-client-simple.html")
      .orElse(Handler.internalServerError("Failed to load HTML file"))
```

## Security Best Practices

When implementing cookie-based authentication, it is crucial to follow security best practices to protect user sessions and sensitive data. Here are some key recommendations:

Cookie security forms the foundation of a robust authentication system. Always set the `isHttpOnly` flag to true for session cookies to prevent client-side JavaScript access, and never store sensitive authentication data in localStorage or sessionStorage as these are vulnerable to XSS attacks. The `isSecure` flag should be enabled to ensure cookies are only transmitted over HTTPS connections, and you should always use HTTPS in production environments to protect data in transit.

Additionally, configure the `SameSite` attribute with either `Strict` or `Lax` settings to provide CSRF protection by default, and combine this with CSRF tokens for defense in depth. Implement CSRF tokens for all state-changing operations, including POST, PUT, and DELETE requests, alongside `SameSite` cookies for comprehensive protection.

Session management requires careful attention to multiple security considerations. Implement both idle timeout and absolute timeout for sessions to balance security and user experience, and provide secure session renewal mechanisms before expiration to maintain user sessions without requiring re-authentication. Ensure proper session destruction on logout by clearing both server-side session data and client-side cookies.

Infrastructure security completes the authentication security triad. Implement rate limiting on authentication endpoints to prevent brute force and denial-of-service attacks. For password security, always use secure password hashing algorithms like bcrypt, scrypt, or Argon2 instead of storing raw passwords, which would be a critical vulnerability.

## Source Code

The complete source code for this Cookie-Based Authentication example is available in the ZIO HTTP examples repository.

To clone the example:

```bash
git clone --depth 1 --filter=blob:none --sparse https://github.com/zio/zio-http.git
cd zio-http
git sparse-checkout set zio-http-example-cookie-auth
```

The example contains the following files:

- **SessionService.scala** - Session management with UUID-based identifiers
- **UserService.scala** - User account management necessary for authentication
- **AuthMiddleware.scala** - Authentication middleware for protecting routes
- **AuthenticationServer.scala** - Main server with login, profile, and logout endpoints
- **AuthenticationClient.scala** - ZIO HTTP client demonstrating API calls
- **cookie-based-auth-client.html** - Advanced interactive web interface (in resources)
- **cookie-based-auth-client-simple.html** - Simplified web interface useful for learning the basics (in resources)

## Running the Server

To run the authentication server:

```bash
cd zio-http/zio-http-example-cookie-auth
sbt "runMain example.auth.session.cookie.AuthenticationServer"
```

The server starts on `http://localhost:8080` with these test users:

| Username | Password      | Email                |
|----------|---------------|----------------------|
| `john`   | `password123` | john@example.com     |
| `jane`   | `secret456`   | jane@example.com     |
| `admin`  | `admin123`    | admin@company.com    |

Available endpoints:

- **GET /** - Serves the web client interface
- **POST /login** - Authenticates users and creates sessions (accepts form data: `username`, `password`)
- **GET /profile/me** - Protected endpoint returning user profile (requires cookie)
- **GET /logout** - Destroys session and clears cookie

## Running the Client

### ZIO HTTP Client

Run the command-line client (ensure server is running):

```bash
cd zio-http/zio-http-example-cookie-auth
sbt "runMain example.auth.session.cookie.AuthenticationClient"
```

Example output:

```
Making login request...
Login response: Login successful! Session created for john
Accessing protected route...
Protected route response: Welcome john! This is your profile: 
 Username: john 
 Email: john@example.com
Logging out...
Logout response: Logged out successfully!
Trying to access protected route after logout...
Final response: Invalid or expired session!
Final response status: Unauthorized
```

### Web-Based Client

Navigate to `http://localhost:8080` to access the interactive web interface.

The advanced client (default) includes:

- User selection cards with pre-configured credentials
- Real-time session status indicator
- HTTP transaction viewer showing requests/responses
- Cookie information display

To use the simple version, modify `AuthenticationServer.scala`:

```scala
Method.GET / Root ->
  Handler
    .fromResource("cookie-based-auth-client-simple.html")
    .orElse(Handler.internalServerError("Failed to load HTML file"))
```

## Demo

We have deployed a live demo at: [https://cookie-auth-demo.ziohttp.com/](https://cookie-auth-demo.ziohttp.com/)


**Try these scenarios:**

1. **Normal Login Flow** - Log in and observe Set-Cookie header, then access profile to see Cookie header automatically sent
2. **Session Persistence** - Log in, refresh the page, notice you remain logged in
3. **Session Expiration** - Log in, wait 5 minutes, try accessing profile (fails with "Invalid or expired session")
4. **Proper Logout** - Log in, click Logout, observe cookie clearing, then try accessing profile (fails)

Use your browser's developer tools (Network tab) to examine cookie behavior and attributes in detail.

## Conclusion

In this guide, we've implemented a complete cookie-based authentication system in ZIO HTTP, demonstrating how to build secure session management for web applications. We covered the essential components: creating a session service for managing active sessions, implementing a user service for account management, building a login endpoint that generates secure session cookies, and developing authentication middleware to protect routes.