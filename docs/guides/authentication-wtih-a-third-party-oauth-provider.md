---
id: authentication-with-a-third-party-oauth-provider
title: "Securing Your APIs: Authentication with a Third-party OAuth Provider"
sidebar_label: "Authentication with a Third-party OAuth Provider"
---

In this guide, we walk through implementing OAuth 2.0 authentication using GitHub as an identity provider, based on the provided Scala and ZIO HTTP implementation. We'll cover the complete authorization code flow, from initial setup to handling refresh tokens.

## Understanding OAuth 2.0

OAuth 2.0 is an authorization framework that enables applications to obtain limited access to user accounts on an HTTP service. Instead of sharing passwords, users can authorize applications to access their information stored with another service.

Traditional authentication requires users to share their passwords with third-party applications, which poses several security risks. OAuth solves this by:
- Never exposing user credentials to the client application
- Allowing users to revoke access at any time
- Enabling granular permissions (scopes)
- Supporting multiple client types (web, mobile, desktop)

While OAuth is an authorization framework, we can use it for authentication by leveraging the identity information provided by the OAuth provider. In this article, we focus on using OAuth for authentication.

## The Authorization Code Flow

The authorization code flow is one of the most secure OAuth flows for server-side applications. In this guide, we use GitHub as the OAuth provider.

Here's how the authorization flow works:

```
┌─────────────┐                                   ┌─────────────┐
│     User    │                                   │             │
│             │                                   │   GitHub    │
│  (Browser)  │                                   │             │
└──────┬──────┘                                   └──────┬──────┘
       │                                                 │
       │  1. Click "Login with GitHub"                   │
       │────────────────────────────────────────────────>│
       │                                                 │
       │  2. Redirect to GitHub OAuth                    │
       │<────────────────────────────────────────────────│
       │                                                 │
       │  3. User authorizes app                         │
       │────────────────────────────────────────────────>│
       │                                                 │
       │  4. Redirect with authorization code            │
       │<────────────────────────────────────────────────│
       │                                                 │
┌──────▼──────┐                                          │
│             │  5. Exchange code for tokens             │
│  Your App   │─────────────────────────────────────────>│
│   Server    │                                          │
│             │  6. Return access & refresh tokens       │
│             │<─────────────────────────────────────────│
└─────────────┘                                          
```

As you can see, the flow involves several steps:
1. The user initiates the login process by clicking a button.
2. The browser redirects the user to [GitHub's authorization endpoint](https://github.com/login/oauth/authorize) and passes the `client_id`, `redirect_uri`, `scope`, and a random `state` parameter for CSRF protection. 
3. The user logs in to GitHub, and GitHub displays an authorization prompt with the requested scopes. For example, if the app requests the `user:email` scope, GitHub will show that the app wants access to the user's email addresses.
4. Upon user approval, GitHub redirects back to the application's `redirect_uri` with an authorization `code` and the original `state`.
5. The application server verifies the `state` to prevent CSRF attacks, then exchanges the authorization `code` for an access token and a refresh token by making a POST request to [GitHub's token endpoint](https://github.com/login/oauth/access_token).
6. The server receives the tokens and can now use the access token to make authenticated requests to GitHub's API on behalf of the user. For example, we can fetch the [user's profile](https://api.github.com/user) information and email addresses, which can be used to create a session in our application. In this guide, we issue our own JWT tokens for session management.

## Creating a GitHub OAuth App

To get started, we need to create a GitHub OAuth App to obtain the `Client ID` and `Client Secret`. Follow these steps:

1. Navigate to GitHub Developer Settings at https://github.com/settings/developers
2. In the developer settings page, locate and click the "New OAuth App" button.
3. Complete the application registration form with these details:
   - Application name: Choose a descriptive name for your application
   - Set the Homepage URL to `http://localhost:8080` (as we are running the server locally)
   - Configure the Authorization callback URL as `http://localhost:8080/auth/github/callback`
4. After registration, securely store the provided Client ID and Client Secret credentials. 

## Configuring Environment Variables

After creating the OAuth app, we need to configure our application with the obtained credentials. Add the following environment variables to your system:

```bash
export GH_CLIENT_ID="your_client_id_here"
export GH_CLIENT_SECRET="your_client_secret_here"
export BASE_URL="http://localhost:8080"
```

## Server Implementation

In this section, we first implement the OAuth authentication service that handles the OAuth flow, and then we implement authentication middleware to protect specific routes.

### OAuth Authentication Service

The `GithubAuthService` orchestrates the entire OAuth flow:

```scala
class GithubAuthService private (
  private val redirectUris: Ref[Map[String, URI]], // state -> redirectUri
  private val users: Ref[Map[String, GitHubUser]], // userId -> GitHubUser
  private val clientID: String,
  private val clientSecret: Secret,
  private val baseUrl: String,
) {
  // Key methods for handling OAuth flow  
}
```

It maintains two in-memory states:
1. **Redirect URIs**: Maps OAuth state parameters to redirect URIs.
2. **User Information**: Stores GitHub user information indexed by user ID.

You may ask: what is the purpose of maintaining state parameters and their respective URIs? The `state` parameter provides CSRF protection. 

When using OAuth 2.0, a client app redirects the user to the authorization server for login/consent. After login, the authorization server redirects the user back to the client with an authorization code.

Without the state parameter, an attacker could trick the user into visiting a malicious link that points back to your app's redirect endpoint but with an authorization code that belongs to the attacker's account. If your app accepts that code blindly (without checking the state parameter), it might link the attacker's account to the victim's session. As a result, the victim is logged into the attacker's account. From that point on, the attacker can log into their account and see anything the victim did while "logged in as the attacker."

To prevent this, the client app generates a cryptographically strong random `state` value when initiating the OAuth flow and stores it (e.g., in memory or a database) along with the intended redirect URI. The client passes this `state` to the authorization server. After the user logs in and authorizes, the authorization server redirects back to the client with both the authorization code and the original `state`. When handling the callback, the app verifies that the returned `state` matches the stored value. If they don't match, the request is rejected. The attacker cannot guess the valid `state` bound to the victim's session, so their forged callback is rejected. Therefore, only OAuth responses that your app actually initiated can complete successfully, neutralizing login CSRF.

To keep the implementation simple, we've added another functionality to this service - storing user information. After exchanging the authorization code for an access token, we fetch the user's profile from GitHub and store it in memory. This allows us to associate our own JWT tokens with GitHub user data.

Let's start by implementing these two core functionalities:

```scala
class GithubAuthService private (
  private val redirectUris: Ref[Map[String, URI]], // state -> redirectUri
  private val users: Ref[Map[String, GitHubUser]], // userId -> GitHubUser
  private val clientID: String,
  private val clientSecret: Secret,
  private val baseUrl: String,
) {
  private val REDIRECT_URI         = s"$baseUrl/auth/github/callback"
  private val GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
  
  val authorize =
    Method.GET / "auth" / "github" -> handler { (request: Request) =>
      for {
        state         <- generateRandomString()
        redirectUri <- ZIO.fromOption(request.url.queryParams.queryParams("redirect_uri").headOption).orElseFail(
          Response.badRequest("Missing redirect_uri parameter")
        )
        _             <- redirectUris.update(_.updated(state, URI.create(redirectUri)))
        githubAuthUrl <- ZIO
          .fromEither(
            URL.decode(
              s"$GITHUB_AUTHORIZE_URL" +
                s"?client_id=$clientID" +
                s"&redirect_uri=$REDIRECT_URI" +
                s"&scope=user:email" +
                s"&state=$state",
            ),
          )
      } yield Response.status(Status.Found).addHeader(Header.Location(githubAuthUrl))
    }
}
```

The `GET /auth/github` route redirects the user to GitHub's authorization URL, passing the `client_id`, `redirect_uri`, `scope`, and finally the `state` parameter. We generate the `state` using the following cryptographically secure function:

```scala
def generateRandomString(): ZIO[Any, Nothing, String] = 
  ZIO.succeed {
    val bytes = new Array[Byte](32) // 256 bits
    java.security.SecureRandom.getInstanceStrong.nextBytes(bytes)
    bytes.map("%02x".format(_)).mkString
  }
```

The `redirect_uri` is another route that handles GitHub's callback. After the user logs in and authorizes, GitHub's authorization server redirects the user to the following callback URL, passing the authentication `code`, `state`, and `error` parameters:

```scala
class GithubAuthService private (
  private val redirectUris: Ref[Map[String, URI]], // state -> redirectUri
  private val users: Ref[Map[String, GitHubUser]], // userId -> GitHubUser
  private val clientID: String,
  private val clientSecret: Secret,
  private val baseUrl: String,
) {
  private val REDIRECT_URI         = s"$baseUrl/auth/github/callback"
  private val GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
  private val GITHUB_USER_API      = "https://api.github.com/user"
  
  val callback =
    Method.GET / "auth" / "github" / "callback" -> handler { (request: Request) =>
      val queryParams = request.url.queryParams
      // Authorization code returned by GitHub after successful user authentication
      // This temporary code is exchanged for an access token
      val code        = queryParams.queryParams("code").headOption
      // The same random string your server generated and sent to GitHub initially
      // prevents malicious sites from initiating fake OAuth flows
      // The server must verify this matches the state it originally sent
      val state       = queryParams.queryParams("state").headOption
      // Error code indicating why the OAuth flow failed
      val error       = queryParams.queryParams("error").headOption
  
      error match {
        case Some(err) =>
          ZIO.succeed(Response.unauthorized(s"OAuth error: $err"))
        case None      =>
          (code, state) match {
            case (Some(authCode), Some(stateParam)) =>
              for {
                uri         <- redirectUris.get.map(_.get(stateParam))
                redirectUri <- ZIO.fromOption(uri)
  
                // Exchange code for access token
                githubTokens <- exchangeCodeForToken(authCode)
  
                // Fetch user info from GitHub
                githubUser <- fetchGitHubUser(githubTokens.access_token)
  
                // Generate our own JWT tokens
                token <- ZIO.serviceWithZIO[JwtTokenService](
                  _.issueTokens(githubUser.id.toString, githubUser.email.getOrElse(""), Set("user")),
                )
  
                // Clean up state, store user info, and store refresh token
                _ <- redirectUris.update(_.removed(stateParam))
                _ <- users.update(_.updated(githubUser.id.toString, githubUser))
  
                // Redirect back to the client with tokens
                redirectUrl <-
                  ZIO
                    .fromEither(
                      URL.decode(
                        s"$redirectUri?access_token=${token.accessToken}" +
                          s"&refresh_token=${token.refreshToken}" +
                          s"&token_type=${"Bearer"}" +
                          s"&expires_in=${token.expiresIn}",
                      ),
                    )
              } yield Response.status(Status.Found).addHeader(Header.Location(redirectUrl))
  
            case _ =>
              ZIO.succeed(Response.badRequest("Missing code or state parameter"))
          }
      }
    }
}
```

After receiving the authorization code, we use it to get the access token from GitHub's access token endpoint using the `exchangeCodeForToken` function:

```scala
private val GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token"

def exchangeCodeForToken(code: String): ZIO[Client, Throwable, GitHubToken] =
  for {
    response      <- ZClient.batched(
      Request
        .post(
          path = GITHUB_TOKEN_URL,
          body = Body.from(
            Map(
              "client_id"     -> clientID,
              "client_secret" -> clientSecret.stringValue,
              "code"          -> code,
              "redirect_uri"  -> REDIRECT_URI,
            ),
          ),
        )
        .addHeader(Header.ContentType(MediaType.application.json))
        .addHeader(Header.Accept(MediaType.application.json)),
    )
    _             <- ZIO
      .fail(new Exception(s"GitHub token exchange failed: ${response.status}"))
      .when(!response.status.isSuccess)
    body          <- response.body.asString
    token <- ZIO
      .fromEither(body.fromJson[GitHubToken])
      .mapError(error => new Exception(s"Failed to parse GitHub token response: $error"))
  } yield token
```

Now, using this access token, we can access GitHub's protected user API to fetch the user's profile information:

```scala
val GITHUB_USER_API = "https://api.github.com/user"

def fetchGitHubUser(accessToken: String): ZIO[Client, Throwable, GitHubUser] =
  for {
    response <- ZClient.batched(
      Request
        .get(GITHUB_USER_API)
        .addHeader(Header.Authorization.Bearer(accessToken))
        .addHeader(Header.Accept(MediaType.application.json)),
    )
    _        <- ZIO
      .fail(new Exception(s"GitHub user API failed: ${response.status}"))
      .when(!response.status.isSuccess)
    body     <- response.body.asString
    user     <- ZIO
      .fromEither(body.fromJson[GitHubUser])
      .mapError(error => new Exception(s"Failed to parse GitHub user response: $error"))
  } yield user
```

With the user's profile in hand, we can issue our own JWT and refresh tokens and return them to the user. We use the same `JwtTokenService` that we implemented in the previous [guide](authentication-with-jwt-bearer-and-refresh-tokens.md).

One step remains. We need to pass the generated tokens (JWT token and refresh token) back to the client. Since this is a server-side application, we cannot return the tokens in the response body. Instead, we redirect the user back to the original `redirect_uri` with the tokens as query parameters:

```scala
val response = 
  ZIO.fromEither(
    URL.decode(
      s"$redirectUri?access_token=${token.accessToken}" +
        s"&refresh_token=${token.refreshToken}" +
        s"&token_type=${"Bearer"}" +
        s"&expires_in=${token.expiresIn}",
    ),
  )
  .map{ redirectUrl =>
    Response.status(Status.Found).addHeader(Header.Location(redirectUrl))
  }
```

In the next step, the client extracts the tokens from the URL and stores them securely; then the client can use them to make authenticated requests to the server. We will discuss the client-side implementation later in this guide.

Let's move on to the middleware implementation, which enables us to protect certain routes.

### Authentication Middleware

The implementation of authentication middleware is the same as we discussed in the previous guide:

```scala
import zio._
import zio.http._

object AuthMiddleware {
  def jwtAuth(realm: String): HandlerAspect[JwtTokenService, UserInfo] =
    HandlerAspect.interceptIncomingHandler {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            ZIO
              .serviceWithZIO[JwtTokenService](_.verify(token.value.asString))
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

It extracts the Bearer token from the `Authorization` header, verifies it using the `JwtTokenService`, and injects the user information into the request context if the token is valid. If the token is missing or invalid, it responds with a 401 Unauthorized status.

### Protected Routes

Using the authentication middleware, we can protect certain routes. For example, let's protect the `/profile/me` endpoint, which returns the user's profile:

```scala
val profile = 
  Method.GET / "profile" / "me" -> handler { (_: Request) =>
    ZIO.service[UserInfo].flatMap { userInfo =>
      // Fetch user profile from in-memory store
      users.get.map { 
        _.get(userInfo.username) match {
          case Some(user) =>
            Response(body = Body.from(user))
          case None       =>
            Response(
              status = Status.NotFound,
              body = Body.from(
                Map(
                  "userId"  -> userInfo.username,
                  "message" -> "User profile not found",
                ),
              ),
            )
        }
      }
    }
  } @@ AuthMiddleware.jwtAuth(realm = "User Profile")
```

In this example, the `/profile/me` endpoint is protected by the `jwtAuth` middleware. Only requests with a valid JWT token will be able to access this endpoint. After successful authentication, the user info is injected into the handler, allowing us to fetch the user's profile from the in-memory store.

## Client Implementation

In this section, we implement the client-side logic to interact with the OAuth-protected server. We will demonstrate both a web client using JavaScript and a Scala client using ZIO HTTP.

### Web Client

The web client is a simple HTML page with a "Sign in with GitHub" button. When the user clicks the button, they are redirected to the server's GitHub authorization endpoint. After successful authentication, the user is redirected back to the client with tokens in the URL.

The authentication flow is initiated by a button click:

```html
<div id="loginSection">
   <button id="loginBtn" class="btn btn-primary">
      <span>🔐</span>
      Sign in with GitHub
   </button>
</div>
```

When the user clicks the "Sign in with GitHub" button, the following JavaScript code redirects the user to the server's GitHub authorization endpoint:

```javascript
const loginBtn = document.getElementById('loginBtn');
loginBtn.addEventListener('click', startAuth);

function startAuth() {
  const redirectUri = encodeURIComponent(window.location.origin);
  const authUrl = `/auth/github?redirect_uri=${redirectUri}`;

  showLoading(true);
  showStatus('Redirecting to GitHub...', 'loading');

  // Redirect to GitHub OAuth
  window.location.href = authUrl;
}
```

After the user authorizes the app on GitHub, they are redirected back to the client with tokens in the URL.

To extract the tokens, we check the URL parameters on each page load by adding an event listener for `DOMContentLoaded`:

```javascript
let currentTokens = null;

// Check for tokens in URL on page load
window.addEventListener('DOMContentLoaded', () => {
  const urlParams = new URLSearchParams(window.location.search);
  const accessToken  = urlParams.get('access_token');
  const refreshToken = urlParams.get('refresh_token');

  if (accessToken && refreshToken) {
    currentTokens = {
      accessToken: accessToken,
      refreshToken: refreshToken,
      tokenType: urlParams.get('token_type'),
      expiresIn: parseInt(urlParams.get('expires_in'))
    };

    // Clean URL
    window.history.replaceState({}, document.title, window.location.pathname);

    fetchUserProfile();
  }
});
```

After extracting the tokens from the URL, we must clean up the URL to remove sensitive data. Having sensitive tokens in the URL is a security risk because URLs can be logged in browser history or can be shared or bookmarked accidentally.

We use the `window.history.replaceState()` method to modify the current history entry, replacing the URL with a clean version. This is a security best practice to prevent token exposure while maintaining the user's current session in memory.

Now, we are ready to call the server to fetch the user's profile with the retrieved access token:

```javascript
async function fetchUserProfile() {
  if (!currentTokens) return;

  try {
    const response = await fetch('/profile/me', {
      headers: {
          'Authorization': `${currentTokens.tokenType} ${currentTokens.accessToken}`
      }
    });

    if (response.ok) {
      const user = await response.json();
      displayUserInfo(user);
    } else {
      throw new Error(`Failed to fetch profile: ${response.status}`);
    }
  } catch (error) {
    showStatus(`Error fetching profile: ${error.message}`, 'error');
  } finally {
    showLoading(false);
  }
}
```

### ZIO HTTP Client

To write a client application in Scala that interacts with the OAuth-protected server, we can use ZIO HTTP's client capabilities. Below is a simple interface for an OAuth client that handles login, logout, and making authenticated requests:

```scala
trait OAuthClient {
  def makeAuthenticatedRequest(request: Request): IO[Throwable, Response]
  def login: IO[Throwable, Unit]
  def logout: IO[Throwable, Unit]
}
```

Let's start by writing the `login` method:

```scala
case class GithubOAuthClient(
  client: Client,
  tokenStore: Ref[Option[Token]],
) extends OAuthClient {

  private val serverUrl    = "http://localhost:8080"
  private val callbackPort = 3000
  private val callbackUrl  = s"http://localhost:$callbackPort"

  private val refreshUrl = URL.decode(s"$serverUrl/refresh").toOption.get
  private val logoutUrl  = URL.decode(s"$serverUrl/logout").toOption.get
  
  override def login: IO[Throwable, Unit] =
    for {
      tokenPromise <- Promise.make[Throwable, Token]

      // Start callback server
      serverFiber <- startCallbackServer(tokenPromise).fork

      // Build OAuth URL
      oauthUrl = s"$serverUrl/auth/github?redirect_uri=$callbackUrl"

      // Open browser for OAuth flow
      _ <- Console.printLine("Starting OAuth flow...")
      _ <- Console.printLine(s"Opening browser to: $oauthUrl")
      _ <- openBrowser(oauthUrl)

      // Wait for callback
      _      <- Console.printLine("Waiting for OAuth callback...")
      tokens <- tokenPromise.await.timeoutFail(new Exception("OAuth flow timed out"))(5.minutes)
      _      <- tokenStore.set(Some(tokens))

      // Stop callback server
      _ <- serverFiber.interrupt

    } yield ()


  override def makeAuthenticatedRequest(request: Request): IO[Throwable, Response] = ???
  override def logout: IO[Throwable, Unit] = ???

}
```

The `startCallbackServer` function starts a temporary HTTP server to listen for the OAuth callback:

```scala
private def startCallbackServer(tokenPromise: Promise[Throwable, Token]): ZIO[Any, Throwable, Server] = {
  val callbackRoutes = Routes(
    Method.GET / Root -> handler { (request: Request) =>
      val queryParams  = request.url.queryParams
      val accessToken  = queryParams.queryParams("access_token").headOption
      val refreshToken = queryParams.queryParams("refresh_token").headOption
      val tokenType    = queryParams.queryParams("token_type").headOption
      val expiresIn    = queryParams.queryParams("expires_in").headOption.flatMap(_.toLongOption)

      (accessToken, refreshToken, tokenType, expiresIn) match {
        case (Some(at), Some(rt), Some(tt), Some(exp)) =>
          val tokens = Token(at, rt, tt, exp)
          tokenPromise
            .succeed(tokens)
            .as(
              Response.html(
                Html.raw(
                  """
                    |<!DOCTYPE html>
                    |<html>
                    |<head><title>Authentication Successful</title></head>
                    |<body>
                    |  <h1>Authentication Successful!</h1>
                    |  <p>You have successfully authenticated with GitHub.</p>
                    |  <p>You can now close this window and return to the application.</p>
                    |  <script>setTimeout(() => window.close(), 5000);</script>
                    |</body>
                    |</html>
              """.stripMargin,
                ),
              ),
            )

        case _ =>
          val error            = queryParams.queryParams("error").headOption.getOrElse("Unknown error")
          val errorDescription = queryParams.queryParams("error_description").headOption.getOrElse("")

          tokenPromise
            .fail(new Exception(s"OAuth error: $error - $errorDescription"))
            .as(
              Response.html(
                Html.raw(
                  s"""
                     |<!DOCTYPE html>
                     |<html>
                     |<head><title>Authentication Failed</title></head>
                     |<body>
                     |  <h1>Authentication Failed</h1>
                     |  <p>Error: $error</p>
                     |  <p>$errorDescription</p>
                     |  <p>Please try again.</p>
                     |</body>
                     |</html>
              """.stripMargin,
                ),
              ),
            )
      }
    },
  )

  Server.serve(callbackRoutes).provide(Server.defaultWithPort(callbackPort))
}
```

It takes a `Promise` parameter that will be completed with the OAuth tokens when they arrive. It creates an HTTP route that listens for GET requests containing either successful authentication tokens (access token, refresh token, token type, and expiration) or error information in the query parameters. On successful authentication, it fulfills the promise with the token information and returns an HTML success page that automatically closes after 5 seconds. On failure, it fails the promise with the error details and returns an HTML error page.

After the client receives the tokens using the `performOAuthFlow`, it can store them (`tokenStore.set(Some(tokens))`) and use them to perform authenticated requests.

Now, we are ready to implement the `makeAuthenticatedRequest`:

```scala
case class GithubOAuthClient(
  client: Client,
  tokenStore: Ref[Option[Token]],
) extends OAuthClient {

  override def login: IO[Throwable, Unit] = ???
  override def logout: IO[Throwable, Unit] = ???
     
  override def makeAuthenticatedRequest(request: Request): IO[Throwable, Response] = {
    def attemptRequest(accessToken: String): ZIO[Any, Throwable, Response] =
      client.batched(request.addHeader(Header.Authorization.Bearer(accessToken)))

    def refreshAndRetry(currentTokenStore: Token): ZIO[Any, Throwable, Response] =
      for {
        newTokens <- refreshTokens(currentTokenStore.refreshToken)
        _         <- tokenStore.set(Some(newTokens))
        response  <- attemptRequest(newTokens.accessToken)
      } yield response

    for {
      tokenStoreValue <- tokenStore.get
      response        <- tokenStoreValue match {
        case Some(tokens) =>
          attemptRequest(tokens.accessToken).flatMap { response =>
            if (response.status == Status.Unauthorized) refreshAndRetry(tokens) else ZIO.succeed(response)
          }
        case None         =>
          login *> makeAuthenticatedRequest(request)
      }
    } yield response
  }
}
```

When making a request, the `makeAuthenticatedRequest` first checks for stored tokens and attaches the access token as a Bearer token in the Authorization header. If the request returns an Unauthorized status (401), indicating an expired access token, it automatically attempts to refresh the token using the stored refresh token and retries the original request with the new access token. If no tokens are available, it initiates the login flow before retrying the request. This implementation provides a seamless authentication experience by handling token expiration and renewal transparently to the calling code.

The final step is to implement the `logout` method. The implementation is straightforward - we call the `/logout` endpoint and remove the stored tokens:

```scala
case class GithubOAuthClient(
  client: Client,
  tokenStore: Ref[Option[Token]],
) extends OAuthClient {
  private val serverUrl  = "http://localhost:8080"
  private val logoutUrl  = URL.decode(s"$serverUrl/logout").toOption.get
  
  override def makeAuthenticatedRequest(request: Request): IO[Throwable, Response] = ???
  override def login: IO[Throwable, Unit] = ???

  override def logout: IO[Throwable, Unit] =
    tokenStore.get.map(_.map(_.refreshToken)).flatMap {
      case Some(refreshToken) =>
        val formData = Form(FormField.simpleField("refreshToken", refreshToken))
        for {
          response <- client
            .batched(Request.post(logoutUrl, Body.fromURLEncodedForm(formData)))
            .orDie
          _        <- ZIO.when(!response.status.isSuccess) {
            ZIO.fail(new Exception(s"Logout failed: ${response.status}"))
          }
          _        <- tokenStore.set(None)
        } yield ()
      case None =>
        ZIO.unit
    }
}
```

## Running the Server and Client

To run the server, first set the following environment variables after creating a GitHub OAuth App:

```bash
# Set environment variables
export GH_CLIENT_ID="your_client_id"
export GH_CLIENT_SECRET="your_client_secret"
```

Now, we can run the server using the following sbt command:

```bash
# Run the server
sbt "zioHttpExample/runMain example.auth.bearer.oauth.AuthenticationServer"
```

Running this command also serves the web client at http://localhost:8080.

Open http://localhost:8080 in your browser and click the "Sign in with GitHub" button. You'll be redirected to GitHub where you can authorize the application. After authorization, you'll be redirected back to confirm successful authentication. Finally, test the protected endpoints to verify everything is working correctly.

To test the Scala client, first run the server, and in another terminal run the following sbt command:

```bash
# Run the client
sbt "zioHttpExample/runMain example.auth.bearer.oauth.AuthenticationClient"
```

The client application will initiate the OAuth flow by starting a local callback server and opening your default browser for GitHub authentication. Once you authorize the application, the client captures the callback containing the tokens and can proceed to make authenticated requests to the protected endpoints.

## Conclusion

In this guide, we demonstrated an implementation of OAuth 2.0 authentication with GitHub using ZIO HTTP. The solution provides a secure and robust authentication system by implementing state management for CSRF protection, proper token exchange and storage mechanisms, and automatic token refresh capabilities.

The combination of OAuth 2.0 for third-party authentication and JWT for session management offers several advantages. OAuth enables secure delegation of user authentication to GitHub, eliminating the need for password management, while JWT provides a stateless session management solution that scales well. Together, they create a secure foundation for building modern web applications that require user authentication.
