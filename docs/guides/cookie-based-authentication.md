---
id: cookie-based-authentication
title: "Securing Your APIs: Cookie-based Authentication"
sidebar_label: "Digest Authentication"
---

Session-based authentication using cookies is one of the most common authentication mechanisms for web applications. In this guide, we demonstrate how to implement a robust cookie-based authentication system in ZIO HTTP, covering both server-side implementation and client integration.

In this authentication model, when a user logs in successfully, the server creates a session and sends a session identifier to the client as a cookie. The client automatically includes this cookie in subsequent requests, allowing the server to identify and authenticate the user.


## Core Concepts

Session-based authentication involves several key components:

- **Session Store**: A server-side storage mechanism that maintains active sessions
- **Cookie**: A cookie containing the session identifier
  - **Set-Cookie Header**: Sent by the server to the client upon successful login
  - **Cookie Header**: Sent by the client in subsequent requests to authenticate
- **Authentication Middleware**: Intercepts requests to validate session cookies; if valid, allows access to protected resources


## Understanding Set-Cookie and Cookie Headers

The foundation of cookie-based authentication lies in two HTTP headers: `Set-Cookie` (server to client) and `Cookie` (client to server). Understanding these headers is crucial for implementing secure authentication.

### The Set-Cookie Header (Server → Client)

The `Set-Cookie` header is used by the server to send cookies to the client. When a user successfully authenticates, the server creates a session and sends the session identifier to the client using this header.

In ZIO HTTP, you can create a `Set-Cookie` header using the `Cookie.Response` data type:

```scala
val sessionCookie = Cookie.Response(
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

- **name**: The name of the cookie (e.g., `session_id`).
- **content**: The value of the cookie, typically a session identifier.
- **domain**: The domain for which the cookie is valid. It defines which hosts (subdomains) can receive the cookie. If no domain is specified, the cookie is valid for the host that set it. If a domain is specified, the cookie will be sent to that domain and its subdomains.
- **path**: The path for which the cookie is valid. The server can include this attribute to restrict where that cookie is sent back to the server. If no path is specified, the cookie is sent only to the same path as the resource that set it and its subdirectories. If a path is specified, the cookie will be sent to that path and its subdirectories.
- **isSecure**: If `true`, the cookie is only sent over HTTPS connections. This prevents the cookie from being transmitted over unencrypted HTTP, enhancing security.
- **isHttpOnly**: If `true`, the cookie is not accessible via JavaScript, mitigating the risk of cross-site scripting (XSS) attacks.
- **maxAge**: The maximum age of the cookie in seconds. After this time, the cookie will be deleted by the browser. If not specified, the cookie is a **session cookie** and will be deleted when the browser is closed.
- **sameSite**: Controls whether the cookie is sent with cross-site requests. The `SameSite` attribute can be set to `Strict`, `Lax`, or `None`. Setting it to `Strict` provides the highest level of CSRF protection, while `Lax` allows some cross-site requests (e.g., top-level navigations).

We can use this cookie in the login flow. When a user provides valid credentials, we create a session and send it back to the client as a `Set-Cookie` header:

```scala
if (user.password == Secret(password)) {
  Response
    .text(s"Login successful! Session created for $username")
    .addCookie(sessionCookie)
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

```scala
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

## Session Service

Here is a simple in-memory session service that manages user sessions. It allows creating, retrieving, and removing sessions:

```scala
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

```scala
object SessionService {
  def live: ZLayer[Any, Nothing, SessionService] =
    ZLayer.fromZIO {
      Ref.make(Map.empty[String, String]).map(new SessionService(_))
    }
}
```

## User Service

The user service manages user accounts, allowing retrieval and creation of users. It also handles errors related to user operations, such as user not found or user already exists:

```scala
import zio._
import zio.Config._
import example.auth.session.cookie.core.UserServiceError._

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

Now, we have all required services to write the login route and then implement cookie-based authentication middleware.

## Login Route

This is a `POST /login` route handler that implements user authentication. It first parses the request body as URL-encoded form data and extracts the username and password fields, failing with a 400 Bad Request if either is missing or invalid. It then uses the `SessionService` to create a new session ID for the user and constructs a cookie. The handler uses the `UserService` to fetch the user by username, returning 401 Unauthorized if the user doesn't exist. Finally, it validates the provided password against the stored password - if correct, it returns a success response with the cookie attached; if incorrect, it returns 401 Unauthorized:

```scala
val login = 
  Method.POST / "login"         ->
    handler { (request: Request) =>
      val form = request.body.asURLEncodedForm.orElseFail(Response.badRequest("Invalid form data"))
      for {
        username       <- form
          .map(_.get("username"))
          .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing username field!")))
          .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing username value!")))
        password       <- form
          .map(_.get("password"))
          .flatMap(ff => ZIO.fromOption(ff).orElseFail(Response.badRequest("Missing password field!")))
          .flatMap(ff => ZIO.fromOption(ff.stringValue).orElseFail(Response.badRequest("Missing password value!")))
        sessionService <- ZIO.service[SessionService]
        sessionId      <- sessionService.create(username)
        sessionCookie = Cookie.Response(
          name = SESSION_COOKIE_NAME,
          content = sessionId,
          maxAge = Some(SESSION_LIFETIME.seconds),
          isHttpOnly = false, // Set to true in production to prevent XSS attacks
          isSecure = false,   // Set to true in production with HTTPS
          sameSite = Some(Cookie.SameSite.Strict),
        )
        users <- ZIO.service[UserService]
        user <- users
          .getUser(username)
          .orElseFail(
            Response.unauthorized("Invalid username or password."),
          )
        res  <-
          if (user.password == Secret(password)) {
            ZIO.succeed(
              Response
                .text(s"Login successful! Session created for $username")
                .addCookie(sessionCookie),
            )
          } else
            ZIO.fail(Response.unauthorized("Invalid username or password."))
      } yield res
    }
```


## Authentication Middleware

The authentication middleware intercepts incoming requests and checks for a valid session cookie. We can write it as a `HandlerAspect` like this:

```scala
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

This middleware can be applied to any route that requires authentication. For example, to protect a user profile route, you can use it like this:

```scala
val profile =
  Method.GET / "profile" / "me" -> handler { (_: Request) =>
    ZIO.serviceWith[User](user =>
      Response.text(
        s"Welcome ${user.username}! " +
          s"This is your profile: \n Username: ${user.username} \n Email: ${user.email}",
      ),
    )
  } @@ cookieAuth(SESSION_COOKIE_NAME)
```

## Writing a Web Client

### Logging In

First, we have to write a login form to ask the user for their credentials. This form will submit the username and password to the server, which will then create a session and send back a cookie:

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

The `login` function sends a POST request to the `/login` endpoint with the username and password. If the response is successful, it toggles the login form to hide it. The `hide` is a CSS class that hides the element:

```css
.hide { display: none; }
```

Upon successful login, the browser stores the received cookie automatically. This received cookie will be used to authenticate subsequent requests to the server.

The flow is simple: Login → Server creates session → Server sends cookie → Browser stores cookie → Browser sends cookie with every request → Server validates cookie → Server responds with protected data.

Unlike token-based authentication where you manually store and attach tokens, cookies are handled automatically by the browser, making the client implementation much simpler.

### Making Authenticated Requests

By default, `fetch` doesn't send cookies with requests. The `credentials: 'include'` option tells the browser to include cookies in the request and accept cookies from the response. Without this, your authentication won't work.

```javascript
fetch('/api/endpoint', { 
    credentials: 'include'  // Critical: tells browser to send cookies
})
```

There are three options for credentials:
- `'omit'` - Never send cookies (default for CORS requests)
- `'same-origin'` - Only send cookies to same domain (default for same-origin requests)
- `'include'` - Always send cookies, even for CORS requests

For authentication to work consistently, always use `include`.

For example, to fetch the user profile after logging in, let's write a button that triggers an authenticated request to the server:

```html
<div id="loggedIn" class="hide">
  <button onclick="getProfile()">Get Profile</button>
</div>
<pre id="result">Results will appear here...</pre>
```

Also, we added a section for displaying the result of the request.

By default, the "Get Profile" button is hidden until the user logs in. When clicked, it calls the `getProfile` function:

```javascript
async function getProfile() {
    try {
        const res = await fetch('/profile/me', { credentials: 'include' });
        result.textContent = await res.text();
    } catch(e) {
        result.textContent = `error: ${e.message}`;
    }
}
```

As the `getProfile` function shows, we make a GET request to the `/profile/me` endpoint with `credentials: 'include'`. This ensures the session cookie is sent along with the request.

Now that we added a div called `loggedIn` which contains all the elements that need to be visible after logging in, let's update the `setLoginState` function:

```javascript
function setLoginState(isLoggedIn) {
    loginForm.classList.toggle('hide', isLoggedIn);
    loggedIn.classList.toggle('hide', !isLoggedIn);
}
```

This toggles both the login form and logged-in-related elements and is called after each call to login and logout endpoints.

### Checking Session Status on Page Load

Users expect to stay logged in when they refresh the page or return later. On page load, we can check if the user is logged in by making a request to a protected endpoint, e.g., `/profile/me`:

```javascript
window.onload = async () => {
    try {
        const res = await fetch('/profile/me', { credentials: 'include' });
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

This pattern prevents the login form from flashing before showing authenticated content. It's better UX to check authentication first, then render the appropriate UI. You may want to show a loading spinner during this check. Please note that for simplicity, we used the same `/profile/me` endpoint for checking session status, which also returns user details if authenticated, which are not intended for this scenario. In a real application, you might have a dedicated endpoint like `/api/me` that returns user details if authenticated or a 401 status if not.

### Logging Out

To log out, let's add a corresponding button:

```html
<div id="loggedIn" class="hide">
  <button onclick="getProfile()">Get Profile</button>
  <button onclick="logout()">Logout</button>
</div>
<pre id="result">Results will appear here...</pre>
```

Clearing the cookie on the client side is not enough, and it is good practice to call the server to invalidate the session. This ensures that the session is removed from the server-side store, preventing any further access to that session ID. The server creates a cookie with an expired `maxAge` and sends it back to the client, which will invalidate the stored cookie in the browser:

```javascript
async function logout() {
    try {
        const res = await fetch('/logout', { credentials: 'include' });
        result.textContent = await res.text();
        setLoginState(!res.ok);
    } catch(e) {
        result.textContent = `Error: ${e.message}`;
    }
}
```

After successful logout, the client calls the `setLoginState` to make the login form visible again and hide the logged-in section.

### Complete Minimal Example

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
          const res = await fetch('/logout', { credentials: 'include' });
          result.textContent = await res.text();
          setLoginState(!res.ok);
      } catch(e) {
          result.textContent = `Error: ${e.message}`;
      }
  }
  
  async function getProfile() {
      try {
          const res = await fetch('/profile/me', { credentials: 'include' });
          result.textContent = await res.text();
      } catch(e) {
          result.textContent = `error: ${e.message}`;
      }
  }
  
  window.onload = async () => {
      try {
          const res = await fetch('/profile/me', { credentials: 'include' });
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

After placing this HTML file in your resources directory and naming it `cookie-based-auth-client-simple.html`, you can serve it using the following ZIO HTTP route:

```scala
val route = 
  Method.GET / Root ->
    Handler
      .fromResource("cookie-based-auth-client-simple.html")
      .orElse(Handler.internalServerError("Failed to load HTML file"))
```

## Security Best Practices

When deploying this authentication system to production, implement the following security measures:

1. **Cookie Security:**
   - **HttpOnly Flag**: Always set `httpOnly = true` for session cookies to prevent client-side JavaScript access. Never store sensitive authentication data in localStorage or sessionStorage as these are vulnerable to XSS attacks.
   - **Secure Flag**: Enable `secure = true` to ensure cookies are only transmitted over HTTPS connections. Always use HTTPS in production environments to protect data in transit.
   - **SameSite Attribute**: Configure `sameSite` attribute (Strict or Lax) to provide CSRF protection by default. Combine with CSRF tokens for defense in depth.
   - **CSRF Protection**: Implement CSRF tokens for all state-changing operations (POST, PUT, DELETE requests) alongside SameSite cookies.

2. **Session Management:**
   - **Expiration Strategy**: Implement both idle timeout and absolute timeout for sessions to balance security and user experience.
   - **Session Renewal**: Provide secure session renewal mechanisms before expiration to maintain user sessions without re-authentication.
   - **Session Invalidation**: Ensure proper session destruction on logout, clearing both server-side session data and client-side cookies.
   - **Session Rotation**: Generate new session IDs after successful authentication to prevent session fixation attacks.

3. **Infrastructure Security:**
   - **Persistent Storage**: Use distributed session stores (Redis, database) instead of in-memory storage for scalability and reliability.
   - **Rate Limiting**: Implement rate limiting on authentication endpoints to prevent brute force and DoS attacks.
   - **Password Security**: Use secure password hashing algorithms (bcrypt, scrypt, or Argon2) instead of storing raw passwords.

## Conclusion

In this guide, we've implemented a complete cookie-based authentication system in ZIO HTTP, demonstrating how to build secure session management for web applications. We covered the essential components: creating a session service for managing active sessions, implementing a user service for account management, building a login endpoint that generates secure session cookies, and developing authentication middleware to protect routes.