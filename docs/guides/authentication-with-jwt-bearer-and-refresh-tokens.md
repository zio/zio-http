---
id: authentication-with-jwt-bearer-and-refresh-tokens
title: "Securing Your APIs: Authentication with JWT Bearer and Refresh Tokens"
sidebar_label: "Authentication with JWT Bearer and Refresh Tokens"
---

In the [previous guide](./authentication-with-jwt-bearer-tokens.md), we explored JWT bearer tokens and their role in modern API authentication. We examined their architectural elegance: stateless, self-contained tokens that eliminate database lookups. However, we also identified their fundamental limitation—once issued, a JWT remains valid until expiration. Revocation is impossible, modification is infeasible, and waiting for natural expiration is the only option.

This creates a fundamental architectural dilemma. Short-lived tokens (5-15 minutes) provide strong security guarantees but degrade user experience through frequent re-authentication. Long-lived tokens (hours or days) improve usability but significantly increase security exposure. Consider a compromised token scenario or the need for immediate access revocation—neither case has an elegant solution with pure JWT implementations.

Enter refresh tokens: the elegant solution that gives us both security and usability.

## The Token Expiration Dilemma

Picture this scenario: You're building a mobile banking app. Security demands short token lifespans—maybe 5 minutes. But forcing users to enter their credentials every 5 minutes would be absurd. They'd abandon your app faster than you can say "authentication failed."

Or consider the opposite: You issue tokens that last 30 days for a smooth user experience. Then an employee leaves the company. Their token remains valid for weeks, accessing sensitive data long after their departure. Your only option? Change the signing key and invalidate everyone's tokens. Mass logout. Support tickets flooding in. Not ideal.

The JWT specification itself doesn't solve this problem. It gives us self-contained tokens but no mechanism for refreshing them. We need something more sophisticated—a two-token system that balances security with usability.

## Understanding Refresh Tokens

Refresh tokens are long-lived credentials used solely to obtain new access tokens. Here's the beautiful simplicity: Your access token (the JWT) remains short-lived and stateless. When it expires, instead of forcing re-authentication, the client presents its refresh token to get a new access token. The refresh token itself never touches your API endpoints—it's only used at the token refresh endpoint.

This separation of concerns is powerful:
- **Access tokens** stay lightweight, short-lived, and stateless
- **Refresh tokens** can be revocable, trackable, and managed server-side
- **API endpoints** only deal with simple JWT validation
- **Token refresh** happens transparently without user intervention

## How Refresh Tokens Work

The refresh token flow adds sophistication to our authentication process without adding complexity to our API endpoints. Here's the complete lifecycle:

### 1. Initial Authentication

When a user logs in successfully, the server generates two tokens:

1. **Access Token**: A short-lived JWT (5-15 minutes) containing user claims
2. **Refresh Token**: A long-lived token (days or weeks) stored server-side

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 300
}
```

### 2. Using Access Tokens

The client includes the access token in API requests exactly as before:

```
Authorization: Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...
```

API endpoints validate the JWT normally—they don't know or care about refresh tokens. This is the beauty of the pattern: your existing JWT infrastructure remains unchanged.

### 3. Token Refresh Process

When the access token expires, the client sends the refresh token to a dedicated refresh endpoint:

```
POST /refresh
Content-Type: application/x-www-form-urlencoded

refreshToken=550e8400-e29b-41d4-a716-446655440000
```

The server:
1. Validates the refresh token against its store
2. Checks if it's expired or revoked
3. Issues a new access token (and optionally a new refresh token)
4. Returns the new tokens to the client

### 4. Token Revocation

When you need to revoke access, you simply delete the refresh token from server storage. Here's what happens:

1. User's refresh token is deleted from the database
2. Their current access token still works (but only for a few more minutes)
3. When the access token expires, they try to get a new one using their refresh token
4. The server can't find their refresh token in storage → Access denied
5. User must log in again

The short-lived access token ensures revocation takes effect quickly—within minutes, not days. For example, if your access tokens lasted 30 days, revocation would be meaningless—the user could continue using their existing access token for weeks. But with 5-minute access tokens, even if someone has a valid token, when you revoke access, they'll be locked out within a 5-minute maximum.

## Security Considerations

Refresh tokens introduce new security considerations. They're powerful, long-lived credentials that need careful handling.

### 1. Storage Security

**Never store refresh tokens in localStorage or sessionStorage.** These are accessible to any JavaScript code, including XSS attacks. For web applications, use `httpOnly` cookies with `Secure` and `SameSite` flags. For mobile apps, use platform-specific secure storage (iOS Keychain, Android Keystore).

### 2. Refresh Token Rotation

Each time a refresh token is used, issue a new one and invalidate the old one. This limits the window of opportunity for stolen tokens. If an attacker steals a refresh token but the legitimate user uses it first, the attacker's token becomes invalid:

```scala
override def refreshTokens(refreshToken: String): Task[TokenResponse] =
  for {
    tokenData <- validateRefreshToken(refreshToken)
    _         <- revokeRefreshToken(refreshToken)  // Invalidate old token
    newTokens <- issueTokens(tokenData.username, tokenData.email, tokenData.roles)
  } yield newTokens
```

### 3. Detecting Token Theft

Track refresh token usage patterns. If a refresh token is used twice (indicating both legitimate user and attacker have it), revoke all tokens for that user immediately. Force re-authentication to establish a new secure session.

## Implementation

Let's build a complete refresh token system using ZIO HTTP. We'll extend our JWT service to manage both access and refresh tokens.

### Token Response Model

First, define the structure for our token response:

```scala
import zio.json._

case class TokenResponse(
  accessToken: String,
  refreshToken: String,
  tokenType: String = "Bearer",
  expiresIn: Int = 300
)

object TokenResponse {
  implicit val codec: JsonCodec[TokenResponse] = DeriveJsonCodec.gen
}
```

The response includes both tokens, the token type (always "Bearer" for our implementation), and the access token's lifetime in seconds.

### Enhanced JWT Service

Our JWT service now manages both token types:

```scala
trait JwtTokenService {
  def issueTokens(username: String, email: String, roles: Set[String]): UIO[TokenResponse]
  def verifyAccessToken(token: String): Task[UserInfo]
  def refreshTokens(refreshToken: String): Task[TokenResponse]
  def revokeRefreshToken(refreshToken: String): UIO[Unit]
}
```

Notice the separation of concerns:
- `issueTokens` creates both tokens during login
- `verifyAccessToken` validates JWTs (unchanged from before)
- `refreshTokens` exchanges refresh tokens for new access tokens
- `revokeRefreshToken` handles logout and security revocations

### Refresh Token Storage

Unlike stateless JWTs, refresh tokens need server-side storage. We'll use an in-memory store for simplicity, but production systems should use Redis, a database, or another persistent store:

```scala
import java.security.SecureRandom
import java.time.Clock

import zio.Config.Secret
import zio._

import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm

case class RefreshTokenData(
  username: String, 
  email: String, 
  roles: Set[String], 
  expiresAt: Long
)

case class JwtTokenServiceLive(
  secretKey: Secret,
  accessTokenTTL: Duration,
  refreshTokenTTL: Duration,
  algorithm: JwtHmacAlgorithm,
  refreshTokenStore: Ref[Map[String, RefreshTokenData]]
) extends JwtTokenService {
  private def generateRefreshToken(
    username: String, 
    email: String, 
    roles: Set[String]
  ): UIO[String] =
    for {
      tokenId   <- generateSecureToken
      expiresAt = System.currentTimeMillis() + refreshTokenTTL.toMillis
      _         <- refreshTokenStore.update(
        _.updated(tokenId, RefreshTokenData(username, email, roles, expiresAt))
      )
    } yield tokenId

  private def generateSecureToken: UIO[String] =
    ZIO.succeed {
      val random = new SecureRandom()
      val bytes  = new Array[Byte](32)
      random.nextBytes(bytes)
      java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
    }
}
```

In this implementation, refresh tokens are cryptographically secure random strings with 256 bits of entropy, Base64URL-encoded for safe transport. This provides strong unpredictability and resistance to brute force attacks.

### The Refresh Endpoint

The refresh endpoint validates the refresh token and issues new tokens:

```scala
Method.POST / "refresh" ->
  handler { (request: Request) =>
    for {
      form         <- request.body.asURLEncodedForm
      refreshToken <- extractFormField(form, "refreshToken")
      tokenService <- ZIO.service[JwtTokenService]
      newTokens    <- tokenService
        .refreshTokens(refreshToken)
        .orElseFail(Response.unauthorized("Invalid or expired refresh token"))
      response = Response.json(newTokens.toJson)
    } yield response
  }
```

The implementation checks the refresh token store, validates the refresh token, and issues fresh tokens:

```scala
override def refreshTokens(refreshToken: String): Task[TokenResponse] =
  for {
    store     <- refreshTokenStore.get
    tokenData <- ZIO
      .fromOption(store.get(refreshToken))
      .orElseFail(new Exception("Invalid refresh token"))
    _         <- ZIO.when(tokenData.expiresAt < System.currentTimeMillis()) {
      ZIO.fail(new Exception("Refresh token expired"))
    }
    // Revoke old refresh token and issue new tokens
    _         <- refreshTokenStore.update(_ - refreshToken)
    newTokens <- issueTokens(tokenData.username, tokenData.email, tokenData.roles)
  } yield newTokens
```

This implements refresh token rotation—each use generates a new refresh token and invalidates the old one.

### Logout Implementation

Logout becomes trivial with refresh tokens—just remove them from the store:

```scala
Method.POST / "logout" ->
  handler { (request: Request) =>
    for {
      form         <- request.body.asURLEncodedForm
      refreshToken <- extractFormField(form, "refreshToken")
      tokenService <- ZIO.service[JwtTokenService]
      _            <- tokenService.revokeRefreshToken(refreshToken)
    } yield Response.text("Logged out successfully")
  }
```

The user's access token might remain valid for a few more minutes, but without a refresh token, they can't get new ones. The session effectively ends.

## Client-Side Token Management

Clients need sophisticated token management to handle refresh tokens properly. The key is making token refresh transparent—API calls should "just work" even when tokens expire.

### ZIO HTTP Client

A simple client interface might look like this, which includes four operations:

```scala
trait AuthenticationService {
  def login(username: String, password: String): IO[Throwable, TokenResponse]
  def refreshTokens(refreshToken: String): IO[Throwable, TokenResponse]
  def makeAuthenticatedRequest(request: Request): IO[Throwable, Response]
  def logout(refreshToken: String): IO[Throwable, Unit]
}
```

1. `login` obtains both issued tokens
2. `refreshTokens` exchanges refresh tokens for new access tokens
3. `makeAuthenticatedRequest` handles HTTP requests by transparently refreshing tokens as needed
4. `logout` revokes the refresh token

The `login`, `logout`, and `refreshTokens` functions are straightforward. The key function is `makeAuthenticatedRequest`, which takes a request and automatically handles token expiration and refresh:

```scala
case class AuthenticationServiceLive(
  client: Client,
  tokenStore: Ref[Option[TokenStore]],
) extends AuthenticationService {
  def login(username: String, password: String): IO[Throwable, TokenResponse] = ???
  def refreshTokens(refreshToken: String): IO[Throwable, TokenResponse] = ???
  
  def makeAuthenticatedRequest(request: Request): IO[Throwable, Response] = {
    def attemptRequest(accessToken: String): IO[Throwable, Response] =
      client.batched(request.addHeader(Header.Authorization.Bearer(accessToken)))
  
    def refreshAndRetry(currentTokenStore: TokenStore): IO[Throwable, Response] =
      for {
        _         <- Console.printLine("Access token expired, refreshing...")
        newTokens <- refreshTokens(currentTokenStore.refreshToken)
        response  <- attemptRequest(newTokens.accessToken)
      } yield response
  
    tokenStore.get.flatMap {
      case Some(tokens) =>
        attemptRequest(tokens.accessToken).flatMap { response =>
          if (response.status == Status.Unauthorized) {
            refreshAndRetry(tokens)
          } else {
            ZIO.succeed(response)
          }
        }
      case None =>
        ZIO.fail(new Exception("No authentication tokens available"))
    }
  }

  def logout(refreshToken: String): IO[Throwable, Unit] = ???
}
```

The client:
1. Attempts the request with the current access token
2. If it gets 401 Unauthorized, refreshes the token
3. Retries with the new access token
4. Updates the token store for future requests

Using this approach, token refresh is seamless. The user experience remains smooth, and the client handles token expiration gracefully.

### JavaScript Client Implementation

#### Login Form

The login form is the same as in the previous guide:

```html
<div id="login-form">
    <h3>Login Form</h3>
    <input id="username" placeholder="Username">
    <input id="password" type="password" placeholder="Password">
</div>
<div id="actions">
    <h3>Actions</h3>
    <button onclick="login()">Login (Get JWT)</button>
</div>
```

It takes username and password, and calls the `/login` endpoint to get both access and refresh tokens. Here is the login function:

```javascript
const SERVER_URL = 'http://localhost:8080';
let accessToken = null;
let refreshToken = null;
let tokenExpiryTime = null;
    
async function login() {
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    
    try {
        const response = await fetch(SERVER_URL + '/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({ username, password })
        });

        if (response.ok) {
            const tokens = await response.json();
            accessToken = tokens.accessToken;
            refreshToken = tokens.refreshToken;
            tokenExpiryTime = new Date(Date.now() + (tokens.expiresIn * 1000));
        } else {
            clearTokens();
        }
    } catch (error) {
        log(`ERROR: ${error.message}`);
    }
}
```

The `login` function stores both tokens and the access token's expiry time.

#### Making Authenticated Requests

Making authenticated requests is similar to the previous article. The key difference is handling 401 responses by refreshing the token. When a request fails due to an expired access token, the client calls the refresh endpoint with the stored refresh token, updates its tokens, and retries the original request:

```javascript
async function userProfile() {
    try {
        const url = '/profile/me';
        const headers = {
            'Authorization': `Bearer ${accessToken}`
        };

        const response = await fetch(url, {
            method: 'GET',
            headers: headers
        });

        const text = await response.text();

        if (response.ok) {
            displayResponse('protectedResponse', text);
        } else if (response.status === 401) {
            await refreshTokens();
            if (accessToken) {
                setTimeout(() => userProfile(), 1000);
            }
        } else {
            console.log(`Error: ${text}`)
        }
    } catch (error) {
        console.log(`Network error: ${error.message}`);
    }
}
```

The refresh logic itself is straightforward:

```javascript
async function refreshTokens() {
    try {
        const formData = new URLSearchParams();
        formData.append('refreshToken', refreshToken);

        const response = await fetch(SERVER_URL + '/refresh', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: formData
        });

        if (response.ok) {
            const tokens = await response.json();
            accessToken = tokens.accessToken;
            refreshToken = tokens.refreshToken;
            tokenExpiryTime = new Date(Date.now() + (tokens.expiresIn * 1000));
        } else {
            const error = await response.text();
            console.log(`FAILED: ${error}`);
            clearTokens();
        }
    } catch (error) {
        console.log(`ERROR: ${error.message}`);
    }
}

function clearTokens() {
    accessToken = null;
    refreshToken = null;
    tokenExpiryTime = null;
}
```

This implementation ensures that the client always has valid tokens when making requests. If the access token expires, it uses the refresh token to get a new one without user intervention. However, the problem is we have to implement the same refresh logic for all protected endpoints.

Instead of doing that, we can create a generic function to make authenticated requests that handles refreshing the token automatically:

```javascript
const SERVER_URL = 'http://localhost:8080';
let accessToken = null;
let refreshToken = null;
let tokenExpiryTime = null;
    
async function makeAuthenticatedRequest(url, options = {}) {
    const attemptRequest = async (token) => {
        return await fetch(url, {
            ...options,
            headers: {
                ...options.headers,
                'Authorization': `Bearer ${token}`
            }
        });
    };

    const refreshAndRetry = async () => {
        const formData = new URLSearchParams();
        formData.append('refreshToken', refreshToken);

        const response = await fetch(SERVER_URL + '/refresh', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: formData
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(`Token refresh failed: ${error}`);
        }

        const tokens = await response.json();
        accessToken = tokens.accessToken;
        refreshToken = tokens.refreshToken;
        tokenExpiryTime = new Date(Date.now() + (tokens.expiresIn * 1000));
        return await attemptRequest(accessToken);
    };

    // Check if we have tokens
    if (!accessToken || !refreshToken) {
        throw new Error('No authentication tokens available. Please login first.');
    }

    // First attempt with current access token
    const response = await attemptRequest(accessToken);

    // If unauthorized, refresh and retry
    if (response.status === 401) {
        return await refreshAndRetry();
    }

    return response;
}
```

This makes the client code cleaner and easier to maintain. All authenticated requests go through this function, which handles token expiration and refresh seamlessly. For example, to fetch the user profile, we simply call:

```javascript
const response = await makeAuthenticatedRequest(SERVER_URL + '/profile/me');
```

Please note that the approach we used for token refresh is **reactive**—the client only attempts to refresh when it receives a 401 Unauthorized response. This keeps the implementation simple and avoids unnecessary refresh calls.

We can also **proactively** monitor token expiration and refresh before it happens. This is more complex but can improve user experience by avoiding failed requests.

## Best Practices

### Token Lifetimes

Finding the right token lifetimes requires balancing security and usability:

- **Access tokens**: 5-15 minutes for high-security applications, up to 1 hour for lower-risk scenarios
- **Refresh tokens**: 7-30 days for typical applications, hours for high-security environments

Consider your threat model. Banking apps might use 5-minute access tokens and 1-hour refresh tokens, requiring frequent re-authentication. Social media apps might use 1-hour access tokens and 30-day refresh tokens for convenience.

### Refresh Token Families

Track refresh token "families"—chains of tokens descended from the same login. If you detect reuse of an old family member, revoke the entire family. This catches token theft even with rotation:

```scala
case class RefreshTokenData(
  username: String,
  email: String,
  roles: Set[String],
  expiresAt: Long,
  familyId: String,  // Track token families
  sequence: Int      // Position in family chain
)
```

### Device-Specific Tokens

In multi-device scenarios, issue separate refresh tokens per device:

```scala
case class RefreshTokenData(
  username: String,
  email: String,
  roles: Set[String],
  expiresAt: Long,
  deviceId: String,
  deviceName: String
)
```

Users can revoke access to specific devices without affecting others. Lost phone? Revoke its tokens without logging out everywhere.

## Source Code

The complete source code for this JWT Bearer and Refresh Token Authentication example is available in the ZIO HTTP repository.

To clone the example:

```bash
git clone --depth 1 --filter=blob:none --sparse https://github.com/zio/zio-http.git
cd zio-http
git sparse-checkout set zio-http-example-jwt-bearer-refresh-token-auth
```

### Running the Server

To run the authentication server:

```bash
cd zio-http/zio-http-example-jwt-bearer-refresh-token-auth
sbt "runMain example.auth.bearer.jwt.refresh.AuthenticationServer"
```

The server starts on `http://localhost:8080` with these test users:

| Username | Password      | Email                | Roles        |
|----------|---------------|----------------------|--------------|
| `john`   | `password123` | john@example.com     | user         |
| `jane`   | `secret456`   | jane@example.com     | user         |
| `admin`  | `admin123`    | admin@company.com    | admin, user  |

### ZIO HTTP Client

Run the command-line client (ensure server is running):

```bash
cd zio-http/zio-http-example-jwt-bearer-refresh-token-auth
sbt "runMain example.auth.bearer.jwt.refresh.AuthenticationClient"
```

### Web-Based Client

To demonstrate the refresh token authentication flow in a web client, we've created a simple HTML page where users can log in, view their profile, refresh tokens, and log out.

First, start the `AuthenticationServer`, which provides the authentication API and serves the HTML client (`jwt-client-with-refresh-token.html`) located in the resource folder:

```bash
sbt "runMain example.auth.bearer.jwtrefresh.AuthenticationServer"
```

Then open [http://localhost:8080](http://localhost:8080) in your browser to interact with the system using predefined credentials. You can log in, view your profile, manually refresh tokens, and log out, showcasing the full JWT bearer and refresh token authentication flow with automatic token refresh on expired access tokens.

The HTML file's source code can be found in the example project's resource folder.

## Demo

We have deployed a live demo of the server and the web client at: [http://jwt-bearer-refresh-token-auth-demo.ziohttp.com/](http://jwt-bearer-refresh-token-auth-demo.ziohttp.com/)

The demo allows you to experience the refresh token authentication flow firsthand. You can log in using the predefined users, access their profiles, observe automatic token refresh when access tokens expire, and log out to see how token revocation works in practice. HTTP transactions can be inspected at the bottom of the page, so you can see the requests and responses in detail, including the token refresh mechanism in action.

## Conclusion

Refresh tokens elegantly solve JWT's fundamental limitation. They give us stateless, scalable authentication while maintaining control over session lifecycle. The pattern's beauty lies in its simplicity: short-lived access tokens for security, long-lived refresh tokens for usability, and clean separation between them.

Key takeaways from implementing refresh tokens:

**The two-token system works**: Access tokens remain simple, stateless JWTs. Refresh tokens add revocability without complicating your API. Your endpoints still just validate JWTs—they don't know refresh tokens exist.

**Security improves dramatically**: Five-minute access tokens become practical when users don't have to re-authenticate constantly. Immediate revocation becomes possible. Token theft has limited impact. You get fine-grained session control without sacrificing user experience.

**Server-side storage is required**: Refresh tokens need server-side storage, but the data is small and operations are simple. Redis, a database table, or even a distributed cache works fine. The storage overhead is negligible compared to the security benefits.

Refresh tokens aren't perfect—they add complexity, require storage, and need careful implementation. But for applications that need both security and usability, they're the gold standard. They turn JWT's greatest weakness into a manageable tradeoff, giving us the best of both worlds: stateless scalability with stateful control.
