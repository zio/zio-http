---
id: digest-authentication
title: "Securing Your APIs: Digest Authentication"
---
# Digest Authentication

Digest Authentication is a more secure HTTP authentication method than Basic Authentication, designed to address the security vulnerabilities of transmitting credentials in easily decodable formats. In this guide, we'll explore how to implement Digest Authentication using ZIO HTTP, providing both server-side middleware and client-side integration.

## Understanding Digest Authentication

Digest Authentication, defined in RFC 2617 and extended in RFC 7616, is an HTTP authentication scheme that uses a **challenge-response mechanism with cryptographic hashing**. Unlike Basic Authentication, which transmits credentials in Base64 encoding, Digest Authentication never sends the actual raw password over the network. Instead, it uses hash functions to create a digest (typically MD5 or SHA-256) that proves the client knows the password without revealing it.

### The Challenge-Response Flow

The authentication process follows this sequence:

1. **Initial Request**: The client makes a request to a protected resource
2. **Challenge**: The server responds with `401 Unauthorized` and a `WWW-Authenticate` header containing:
    - `realm`: A string indicating the protection space
    - `nonce`: A server-generated unique value (prevents replay attacks)
    - `qop`: Quality of Protection (usually "auth")
    - `algorithm`: Hash algorithm to use (MD5, SHA-256, etc.)
3. **Response**: The client computes a digest using the provided parameters and its credentials, then retries the request with an `Authorization` header
4. **Verification**: The server validates the digest and grants or denies access

Here are examples of the initial request, the challenge response from the server, and the subsequent authenticated request:

1. First, the client tries to access a protected resource at `/profile/me` without authentication: 

```http
GET /profile/me HTTP/1.1
Host: localhost:8080
```

2. The server responds with "401 Unauthorized" and a challenge:

```http
HTTP/1.1 401 Unauthorized
content-length: 0
date: Thu, 24 Jul 2025 07:27:40 GMT
www-authenticate: Digest realm="User Profile", nonce="MTc1MzM0MjA2MDI0NDpmbXNGK2dTblF4WEVwN1gwWktMVllRPT0=", opaque="uSla+F7cMBsB/t3K9OCLzg==", stale=false, algorithm=MD5, qop="auth", charset=UTF-8, userhash=false
```

The `WWW-Authenticate` header is sent by the server in the HTTP 401 Unauthorized response to present the digest authentication challenge parameters. This challenge indicates that the requested resource requires authentication within the specified realm and requires the client to compute a cryptographic digest response using the provided parameters.

3. The client computes a digest response using the specified cryptographic hash algorithm (typically MD5 or SHA-256) applied to a structured combination of challenge parameters and user credentials. This process generates a hash value that cryptographically proves credential possession without transmitting the plaintext password.

The digest response is calculated using the following formula:

```http
HA1 = H(username:realm:password)
if (qop == "auth-int") 
    HA2 = H(method:uri:body)
else 
    HA2 = H(method:uri) 
response = H(HA1:nonce:nc:cnonce:qop:HA2)
```

Let's discuss the parameters involved in this calculation:

- **`username`**: The user identifier for authentication
- **`realm`**: A server-defined protection space identifier that logically groups protected resources.
- **`password`**: The user's secret credential (never transmitted in plaintext).
- **`method`** and **`uri`**: The HTTP request method (GET, POST, etc.) and the requested URI path. Including these values binds the digest to the specific request, preventing attackers from reusing valid digests for different requests (request tampering protection).
- **`nonce`**: A server-generated, time-limited unique value that changes for each authentication challenge. This server-controlled parameter ensures temporal uniqueness and prevents replay attacks by invalidating previously captured authentication attempts.
- **`nc` (nonce count)**: A hexadecimal counter (starting at 00000001) that increments with each request using the same nonce value. This enables the server to detect duplicate or out-of-sequence requests, providing protection against replay attacks even when nonces are reused during their validity period.
- **`cnonce` (client nonce)**: A client-generated random value that adds entropy from the client side. This parameter ensures that identical server challenges produce different digest responses, preventing certain cryptographic attacks and enabling client request correlation.
- **`qop`** (Quality of Protection): Defines the protection level - "auth" for authentication-only or "auth-int" for authentication with message integrity protection

The digest authentication mechanism provides several cryptographic security improvements over HTTP Basic Authentication:

1. **Credential Protection**: User passwords remain on the client side and are never transmitted across the network, even in hashed form.
2. **Replay Attack Resistance**: The combination of server nonces and nonce counts creates unique authentication tokens for each request, making captured authentication data unusable for subsequent unauthorized requests.
3. **Request Integrity**: By incorporating the HTTP method and URI into the digest calculation, the protocol prevents attackers from redirecting valid authentication tokens to different endpoints or changing request methods.
4. **Mutual Verification**: The client can verify server authenticity through the server's ability to validate client-generated digests, while the server confirms client identity through successful digest verification.
5. **Enhanced Integrity Protection**: When using `qop="auth-int"`, the protocol includes the request body hash in the authentication calculation, ensuring both authentication and message integrity.

Even with these security improvements, HTTPS remains necessary since Digest Authentication doesn't encrypt the entire communication channel. HTTPS deployment is essential for production systems to provide complete security through transport-layer encryption.

Let's calculate the digest response using the formula above:

```
HA1 = H(username:realm:password)
= MD5(john:User Profile:password123)
= e858d07c1afb2c75ea1f1ee29c1d7702

HA2 = H(method:uri)
= MD5(GET:/profile/me)
= 509ae9f341ffefdd68447afcdae1e7bf

nonce  = MTc1MzM0MjA2MDI0NDpmbXNGK2dTblF4WEVwN1gwWktMVllRPT0= // server-generated nonce
nc     = 00000001 // nonce count (hexadecimal)
qop    = auth
cnonce = 71n315lg67i4kr9473e5hw // client-generated nonce

response = H(HA1:nonce:nc:cnonce:qop:HA2)
= MD5(e858d07c1afb2c75ea1f1ee29c1d7702:MTc1MzM0MjA2MDI0NDpmbXNGK2dTblF4WEVwN1gwWktMVllRPT0:00000001:71n315lg67i4kr9473e5hw:auth:509ae9f341ffefdd68447afcdae1e7bf)
= f7e07fe43aa7a7e3a296edf8f3b3772a
```

After calculating the digest response, the client constructs the `Authorization` header with the computed values:

```http
GET /profile/me HTTP/1.1
Host: localhost:8080
Authorization: Digest username="john", realm="User Profile", nonce="MTc1MzM0MjA2MDI0NDpmbXNGK2dTblF4WEVwN1gwWktMVllRPT0=", uri="/profile/me", algorithm="MD5", qop="auth", nc="00000001", cnonce="71n315lg67i4kr9473e5hw", response="f7e07fe43aa7a7e3a296edf8f3b3772a", userhash=false, opaque="uSla+F7cMBsB/t3K9OCLzg=="
```

4. The server verifies the digest by recalculating it using the same parameters and its stored credentials for the user "john". If the digest matches, the server grants access to the requested resource. To do this, the server extracts the "username" parameter from the authorization header, then looks up the user's password in its database. The server can now compute the digest using the same algorithm and parameters as the client. If the digest matches the one sent by the client, the server responds with the requested resource:

```http
HTTP/1.1 200 Ok
content-length: 76
content-type: text/plain
date: Wed, 23 Jul 2025 12:59:32 GMT

Hello john! This is your profile: 
 Username: john 
 Email: john@example.com
```

If the digest does not match, the server responds with `401 Unauthorized` again, indicating that authentication failed, with a new `WWW-Authenticate` header containing a new challenge.

## Implementing Digest Authentication

To implement Digest Authentication in ZIO HTTP, we are going to create middleware that intercepts incoming requests, checks for the presence of the `Authorization` header, and validates the digest against stored user credentials. We'll also create a simple HTML client to demonstrate how to use Digest Authentication in practice.

ZIO HTTP currently does not have built-in support for Digest Authentication, but it has an excellent foundation for implementing it as custom middleware.

### Overview

The middleware we are going to implement should handle two main scenarios, based on whether the request contains an `Authorization` header:
- If it is present and is of type `Header.Authorization.Digest`, it means the client is trying to authenticate by responding to a challenge from the server. We need to validate the digest against the stored user credentials. If the digest is valid, we allow the request to proceed. If the digest is invalid, we respond with a `401 Unauthorized` status and a new challenge in the `WWW-Authenticate` header.
- If the header is not present, this means that the client is trying to access a protected resource without authentication, so we need to respond with a `401 Unauthorized` status and a `WWW-Authenticate` header containing the challenge parameters (`realm`, `nonce`, `algorithm`, etc.).

```scala
val digestAuthHandler: HandlerAspect[Any, Unit] =
   HandlerAspect.interceptIncomingHandler[Any, Unit] {
      Handler.fromFunctionZIO[Request](request =>
         request.header(Header.Authorization) match {
            // If the request contains a Digest Authorization header
            case Some(authHeader: Header.Authorization.Digest) =>
               // 1. find the user's credentials in the database using the username from header
               // 2. validate the digest against stored user credentials
               // 3. if valid, allow the request to proceed
               // 4. if invalid, respond with 401 Unauthorized with a new challenge
   
            // No auth header or not digest, send a challenge
            case _ =>
             // 1. respond with 401 Unauthorized with a new challenge
         },
      )
   }
```

### Digest Authentication Service Interface

There are two main operations we need to implement:

- **Creating a challenge**: When the client tries to access a protected resource without authentication, we need to generate a challenge that includes the `realm`, `nonce`, `algorithm`, and quality of protection (`qop`). The server generates the challenge and sends it back to the client in the `WWW-Authenticate` header of the response.
- **Validating the response**: When the client responds with a digest in the `Authorization` header, the server needs to validate it against the stored user credentials. This involves computing the expected digest and comparing it with the one sent by the client.

It is good practice to put these functionalities into a separate service called `DigestAuthService`:

```scala
trait DigestAuthService {
   def generateChallenge(
     realm: String,
     qop: List[QualityOfProtection],
     algorithm: HashAlgorithm
   ): UIO[DigestChallenge]

   def validateResponse(
     digest: DigestResponse,
     password: Secret,
     method: Method,
     body: Option[String] = None,
   ): ZIO[Any, DigestAuthError, Boolean]
}
```

The `DigestChallenge` is a data type that encapsulates the parameters needed to generate a challenge, such as `realm`, `nonce`, `opaque`, `algorithm`, and `qop`:

```scala
case class DigestChallenge(
  realm: String,
  nonce: String,
  opaque: Option[String] = None,
  algorithm: DigestAlgorithm = MD5,
  qop: List[QualityOfProtection] = List(Auth),
  stale: Boolean = false,
  domain: Option[List[String]] = None,
  charset: Option[String] = Some("UTF-8"),
  userhash: Boolean = false,
) {
  def toHeader: Header.WWWAuthenticate.Digest = ???
}

object DigestChallenge {
  def fromHeader(header: Header.WWWAuthenticate.Digest): ZIO[Any, Nothing, DigestChallenge] = ???
}
```

The `DigestChallenge#toHeader` method converts the `DigestChallenge` into a `Header.WWWAuthenticate.Digest`, which can be used in the HTTP response to challenge the client.

The `DigestResponse` is a data type that represents the response sent by the client in the `Authorization` header:

```scala
case class DigestResponse(
  response: String,
  username: String,
  realm: String,
  uri: URI,
  opaque: String,
  algorithm: DigestAlgorithm,
  qop: QualityOfProtection,
  cnonce: String,
  nonce: String,
  nc: NC,
  userhash: Boolean,
)

object DigestResponse {
  def fromHeader(digest: Header.Authorization.Digest): DigestResponse = ???
}
```

The `DigestResponse.fromHeader` constructor translates the `Header.Authorization.Digest` to `DigestResponse`, which is a more type-safe representation of the digest response.

The `DigestAuthError` is a sealed trait that represents the possible errors that can occur during the digest authentication process. It includes:

```scala
sealed trait DigestAuthError extends Throwable

object DigestAuthError {
   case class NonceExpired(nonce: String)                       extends DigestAuthError
   case class InvalidNonce(nonce: String)                       extends DigestAuthError
   case class ReplayAttack(nonce: String, nc: NC)               extends DigestAuthError
   case class InvalidResponse(expected: String, actual: String) extends DigestAuthError
   case class UnsupportedQop(qop: String)                       extends DigestAuthError
   case class MissingRequiredField(field: String)               extends DigestAuthError
   case class UnsupportedAuthHeader(message: String)            extends DigestAuthError
}
```

The `DigestAlgorithm` is an enum that represents the hashing algorithms used in Digest Authentication. It includes common algorithms like MD5, SHA-256, and SHA-512, along with their session variants:

```scala
sealed abstract class DigestAlgorithm(val name: String, val digestSize: Int) {
  override def toString: String = name
}

object DigestAlgorithm {
  case object MD5         extends DigestAlgorithm("MD5", 128)
  case object MD5_SESS    extends DigestAlgorithm("MD5-sess", 128)
  case object SHA256      extends DigestAlgorithm("SHA-256", 256)
  case object SHA256_SESS extends DigestAlgorithm("SHA-256-sess", 256)
  case object SHA512      extends DigestAlgorithm("SHA-512", 512)
  case object SHA512_SESS extends DigestAlgorithm("SHA-512-sess", 512)

  val values: List[DigestAlgorithm] =
    List(MD5, MD5_SESS, SHA256, SHA256_SESS, SHA512, SHA512_SESS)

  def fromString(s: String): Option[DigestAlgorithm] =
    values.find(_.name.equalsIgnoreCase(s.trim))
}
```

The `QualityOfProtection` is a sealed trait that represents the quality of protection used in Digest Authentication. It includes two common values: `auth` and `auth-int`. The `auth` value indicates that the request is authenticated, while `auth-int` indicates that the request is authenticated and the integrity of the request body is protected:

```scala
sealed abstract class QualityOfProtection(val name: String) {
  override def toString: String = name
}

object QualityOfProtection {
  case object Auth    extends QualityOfProtection("auth")
  case object AuthInt extends QualityOfProtection("auth-int")

  private val values: Set[QualityOfProtection] = Set(Auth, AuthInt)

  def fromString(s: String): Option[QualityOfProtection] =
    values.find(_.name.equalsIgnoreCase(s.trim))

  def fromChallenge(s: String): Set[QualityOfProtection] =
    s.split(",")
      .map(_.trim)
      .flatMap(QualityOfProtection.fromString)
      .toSet

  def fromChallenge(s: Option[String]): Set[QualityOfProtection] =
    s.fold(Set.empty[QualityOfProtection])(fromChallenge)
}
```

Now that we have defined the service interface and the supporting types, we are ready to dive into the implementation details of the `DigestAuthService`. But before we do that, we need to implement two core components: a nonce management service and a digest computation service.

#### Nonce Management Service

An important aspect of challenge generation is `nonce` generation. The `nonce` is a unique value generated by the server for each challenge, which prevents replay attacks and ensures freshness in the authentication process. It should be a random value that changes for each request/session, so that reuse (replay) of old requests is not possible.

There is no mandated algorithm for nonce generation, but here are some common approaches:

- **Random Nonce**: The nonce can be a random value generated by the server. This is a simple approach but requires the server to keep track of used nonces to prevent replay attacks. After receiving the response to a challenge, if the digest is valid, the server should mark the nonce as used so it cannot be reused in subsequent requests. With this approach, the server does not use the nonce count (`nc`), as the nonce itself is unique for each API session and cannot be reused:

```scala
val nonce = 
  Random.nextBytes(16)
    .map(_.toArray)
    .map(Base64.getEncoder.encodeToString) 
// e.g. nonce: pY0+z+EeTgrXwq/Y3L8lGA==
```

- **Timestamp-based Nonce**: The nonce can be a combination of the current timestamp and a random value or hash of the timestamp. This allows the server to check if a nonce is too old and reject it, reducing the replay attack window for the session. After receiving the response to a challenge, if the digest is valid, the server should first check if the nonce is still valid (not expired) and then mark the combination of `nonce` and `nc` as used, so it cannot be reused in subsequent requests. The next request with the same nonce should increment the `nc` value to indicate that it is a new request. Using this approach, we can use the same nonce for multiple requests until the session expires.

In this guide, we will use the timestamp-based nonce generation approach, which has better control over nonce expiration policies. To implement this approach, we are going to create a `NonceService` that will handle nonce generation, validation, and tracking of used nonces:

```scala
trait NonceService {
  def generateNonce: UIO[String]
  def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean]
  def isNonceUsed(nonce: String, nc: NC): UIO[Boolean]
  def markNonceUsed(nonce: String, nc: NC): UIO[Unit]
}
```

The `NC` class represents the nonce count (`nc`) as an 8-digit hexadecimal string, zero-padded on the left. The `NC` class is defined as follows:

```scala
case class NC(value: Int) extends AnyVal {
  override def toString: String = toHexString

  private def toHexString: String = f"$value%08x"
}

object NC {
  implicit val ordering: Ordering[NC] = Ordering.by(_.value)
}
```

Let's start by implementing the `generateNonce` method:

```scala
final case class NonceServiceLive(
  secretKey: Secret
) extends NonceService {
  private val HASH_ALGORITHM = "HmacSHA256"
  private val HASH_LENGTH    = 16

  def generateNonce: UIO[String] = 
    Clock.currentTime(TimeUnit.MILLISECONDS).map { timestamp =>
      val hash    = Base64.getEncoder.encodeToString(createHash(timestamp))
      val content = s"$timestamp:$hash"
      Base64.getEncoder.encodeToString(content.getBytes("UTF-8"))
    }
  
  def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean] = ???
  def isNonceUsed(nonce: String, nc: NC): UIO[Boolean] = ???
  def markNonceUsed(nonce: String, nc: NC): UIO[Unit] = ???

  private def createHash(timestamp: Long): Array[Byte] = {
    val mac = Mac.getInstance(HASH_ALGORITHM)
    mac.init(new SecretKeySpec(secretKey.stringValue.getBytes("UTF-8"), HASH_ALGORITHM))
    mac.doFinal(timestamp.toString.getBytes("UTF-8")).take(HASH_LENGTH)
  }
}
```

Each nonce generated by this method has two parts: 

```
nonce = Base64(timestamp:Base64(KD(timestamp)))
```

The `timestamp` is the current time in milliseconds, and `KD(timestamp)` is the HMAC hash of the timestamp using a secret key known only to the server. The nonce is then Base64-encoded to ensure it can be safely transmitted in HTTP headers.

When receiving a nonce value from the client, the server should decode it, extract the timestamp and hash, and check if the nonce is valid:
- First, check if the timestamp is within an acceptable range (e.g., not older than a certain duration, like 5 minutes). This prevents old requests from being used for replay attacks.
- Then, verify that the hash matches the computed hash for the given timestamp using the same secret key. This allows the server to ensure that the nonce was generated by itself and has not been tampered with.

If the nonce passes both checks, it is considered valid. Let's implement the `validateNonce` method to check if the nonce is valid:

```scala
final case class NonceServiceLive(secretKey: Secret) extends NonceService {
  private def computeHash(timestamp: Long, secretKey: Secret): Array[Byte] = ???
    
  def generateNonce: UIO[String] = ???

  def validateNonce(nonce: String, maxAge: Duration): ZIO[Any, NonceError, Unit] =
    ZIO.fromEither {
      try {
        val decoded = new String(Base64.getDecoder.decode(nonce), "UTF-8")
        val parts   = decoded.split(":", 2)

        if (parts.length != 2) {
          Left(NonceError.InvalidNonce(nonce))
        } else {
          val timestamp         = parts(0).toLong
          val providedHash      = Base64.getDecoder.decode(parts(1))
          val isWithinTimeLimit = java.lang.System.currentTimeMillis() - timestamp <= maxAge.toMillis

          if (!isWithinTimeLimit) {
            Left(NonceError.NonceExpired(nonce))
          } else if (!constantTimeEquals(createHash(timestamp), providedHash)) {
            Left(NonceError.InvalidNonce(nonce))
          } else {
            Right(())
          }
        }
      } catch {
        case _: Exception => Left(NonceError.InvalidNonce(nonce))
      }
    }
  
  def isNonceUsed(nonce: String, nc: NC): UIO[Boolean] = ???
  def markNonceUsed(nonce: String, nc: NC): UIO[Unit] = ???
    
  private def constantTimeEquals(a: Array[Byte], b: Array[Byte]): Boolean =
    a.length == b.length && a.zip(b).map { case (x, y) => x ^ y }.fold(0)(_ | _) == 0
}
```

After validating the nonce, we should also check if the `nonce` and `nc` have already been used in previous requests. This can be done by maintaining a map of used nonces and their associated counts (`nc`). If the nonce is found in the map, it means it has already been used, and we should reject the request. This helps us prevent current-session replay attacks, where an attacker might try to reuse a valid nonce from a previous request before it expires.

To implement this functionality, called `isNonceUsed`, we can use a `Ref` to store the used nonces in memory, which maintains a map of nonces to sets of counts (`nc`) that have been used:

```scala
final case class NonceServiceLive(
    usedNonce: Ref[Map[String, NC]],
    secretKey: SecretKey
  ) extends NonceService {
  def generateNonce: UIO[String] = ???
  def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean] = ???

  def isNonceUsed(nonce: String, nc: NC): ZIO[Any, NonceError, Unit] =
    for {
      usedNoncesMap <- usedNonces.get
      _             <- usedNoncesMap.get(nonce) match {
        case Some(lastUsedNc) if nc <= lastUsedNc =>
          ZIO.fail(NonceAlreadyUsed(nonce, nc))
        case _                                    =>
          ZIO.unit
      }
    } yield ()
    
  def markNonceUsed(nonce: String, nc: NC): UIO[Unit] = ???
}
```

Similarly, we need to implement the `markNonceUsed` method to mark a `nonce` and `nc` as used after a successful authentication:

```scala
final case class NonceServiceLive(
    usedNonce: Ref[Map[String, NC]],
    secretKey: SecretKey
  ) extends NonceService {
  def generateNonce: UIO[String] = ???
  def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean] = ???
  def isNonceUsed(nonce: String, nc: NC): ZIO[Any, NonceError, Unit] = ???

  def markNonceUsed(nonce: String, nc: NC): ZIO[Any, NonceError, Unit] =
    usedNonces.update { nonces =>
      val currentMax = nonces.getOrElse(nonce, NC(0))
      nonces.updated(nonce, currentMax max nc)
    }
}
```

The nonce generation and validation logic is now encapsulated in the `NonceService`, which can be injected into the `DigestAuthService` to handle nonce management during the digest authentication process. 

It is time to think about computing the digest response, which is the next step in implementing the `DigestAuthService`.

#### Digest Response Service

Let's create another service that is responsible for calculating the digest response based on the provided parameters:

```scala
trait DigestService {
  def calculateResponse(
    username: String,
    realm: String,
    password: Secret,
    nonce: String,
    nc: NC,
    cnonce: String,
    algorithm: DigestAlgorithm,
    qop: QualityOfProtection,
    uri: URI,
    method: Method,
    body: Option[String] = None,
  ): UIO[String]
}
```

As we discussed earlier, the digest response is calculated using the following formula:

```
HA1 = H(username:realm:password)
HA2 = H(method:uri)
response = H(HA1:nonce:nc:cnonce:qop:HA2)
```

But this is the simple version of the digest calculation when the algorithm is a regular algorithm, e.g., `MD5`, `SHA-256`. If the algorithm is a session algorithm (ending with "-sess", e.g., `MD5-sess`, `SHA-256-sess`), the `HA1` is calculated differently:

```
If algorithm ends in "-sess":
    HA1 = H(H(username:realm:password):nonce:cnonce)
otherwise:
    HA1 = H(username:realm:password)
HA2 = H(method:uri)
response = H(HA1:nonce:nc:cnonce:qop:HA2)
```

In regular algorithms, the `HA1` is a constant value for a given `username`, `realm`, and `password`. This means that if an attacker captures the `HA1` value, they can use it to generate valid digests for any request made by that user.

Session algorithms are variants of digest algorithms that enhance security by making the hash depend not only on the `username`, `realm`, and `password` but also on values that are unique to each session, such as the server nonce (`nonce`) and the client nonce (`cnonce`).

If someone steals `H(username:realm:password)` from the client, e.g., via sniffing or memory dump, they still can't generate a valid digest without knowing the specific `nonce` and `cnonce` values.

So, as a security best practice, always prefer `-sess` algorithms if both the client and server support them, especially over unsecured networks.

Now, let's implement the `computeResponse` method in the `DigestService`:

```scala
def computeResponse(
  username: String,
  realm: String,
  password: Secret,
  nonce: String,
  nc: NC,
  cnonce: String,
  algorithm: DigestAlgorithm,
  qop: QualityOfProtection,
  uri: URI,
  method: Method,
  body: Option[String] = None,
): UIO[String] =
  for {
    a1       <- computeA1(username, realm, password, nonce, cnonce, algorithm)
    ha1      <- hash(a1, algorithm)
    a2       <- computeA2(method, uri, algorithm, qop, body)
    ha2      <- hash(a2, algorithm)
    response <- computeFinalResponse(ha1, ha2, nonce, nc, cnonce, qop, algorithm)
  } yield response
```

The calculation of `a1` is straightforward. Based on the type of algorithm, we can either use the simple formula or the session formula:

```scala
private def computeA1(
  username: String,
  realm: String,
  password: Secret,
  nonce: String,
  cnonce: String,
  algorithm: DigestAlgorithm,
): UIO[String] = {
  val baseA1 = s"$username:$realm:${password.stringValue}"

  algorithm match {
    case DigestAlgorithm.MD5_SESS | DigestAlgorithm.SHA256_SESS | DigestAlgorithm.SHA512_SESS =>
      hash(baseA1, algorithm)
        .map(ha1 => s"$ha1:$nonce:$cnonce")
    case _                                                                                    =>
      ZIO.succeed(baseA1)
  }
}
```

Creating `a2` is also straightforward; it is just a hash of the HTTP method and URI. In the case of `auth-int` quality of protection, we also need to include the request body in the calculation:

```scala
private def computeA2(
  method: Method,
  uri: URI,
  algorithm: DigestAlgorithm,
  qop: QualityOfProtection,
  entityBody: Option[String],
): UIO[String] = {
  qop match {
    case QualityOfProtection.AuthInt =>
      entityBody match {
        case Some(body) =>
          hash(body, algorithm)
            .map(hbody => s"${method.name}:${uri.getPath}:$hbody")
        case None       =>
          ZIO.succeed(s"${method.name}:${uri.getPath}:")
      }
    case _                           =>
      ZIO.succeed(s"${method.name}:${uri.getPath}")
  }
}
```

And here is the final response generation:

```scala
private def computeFinalResponse(
  ha1: String,
  ha2: String,
  nonce: String,
  nc: NC,
  cnonce: String,
  qop: QualityOfProtection,
  algorithm: DigestAlgorithm,
): UIO[String] = 
  hash(s"$ha1:$nonce:$nc:$cnonce:${qop.name}:$ha2", algorithm)
```

The `hash` function is a utility function that computes the hash of a given string using the specified algorithm. It can be implemented using Java's `MessageDigest` or any other hashing library:

```scala
private def hash(data: String, algorithm: DigestAlgorithm): UIO[String] =
  ZIO.succeed {
    val md = algorithm match {
      case MD5 | MD5_SESS       =>
        MessageDigest.getInstance("MD5")
      case SHA256 | SHA256_SESS =>
        MessageDigest.getInstance("SHA-256")
      case SHA512 | SHA512_SESS =>
        MessageDigest.getInstance("SHA-512")
    }
    md.digest(data.getBytes("UTF-8"))
      .map(b => String.format("%02x", b & 0xff))
      .mkString
  }
```

### Digest Authentication Service Implementation

#### Generating a Challenge

Let's implement the `generateChallenge` method in the `DigestAuthService`:

```scala
case class DigestAuthServiceLive(
  nonceService: NonceService,
) extends DigestAuthService {
  def generateChallenge(
    realm: String,
    qop: List[QualityOfProtection],
    algorithm: HashAlgorithm,
  ): UIO[DigestChallenge] =
    for {
      nonce     <- nonceService.generateNonce
      opaque    <- generateOpaque
    } yield DigestChallenge(
      realm = realm,
      nonce = nonce,
      opaque = Some(opaque),
      algorithm = algorithm,
      qop = qop,
    )

  private def generateOpaque: UIO[String] =
    Random
      .nextBytes(OPAQUE_BYTES_LENGTH)
      .map(_.toArray)
      .map(Base64.getEncoder.encodeToString)
}
```

The `opaque` is an optional value chosen by the server, and the client should copy it back unchanged in the `Authorization` header when responding to the challenge.

[//]: # (Please note that `DigestChallenge` and `DigestResponse` are custom data types that encapsulate the digest challenge parameters and the digest response, respectively. They have the same structure as `Header.WWWAuthenticate.Digest` and `Header.Authorization.Digest`, but with better type safety guarantees.)


[//]: # (To validate the digest header, we also need to pass the password and method of the request, and optionally the body if we want to validate the integrity of the request body as well. Integrity of the request body is only required if the `qop` is set to `auth-int`, which means that the request body has been included in the digest calculation.)

#### Validating the Challenge Response

Another method we need to implement is `validateResponse`, which checks whether the provided digest response is valid. This method will compute the expected digest using the provided parameters and compare it with the one sent by the client.


Let's implement the `validateResponse` method in the `DigestAuthService`:

```scala
case class DigestAuthServiceLive(
  nonceService: NonceService,
  digestService: DigestService,
) extends DigestAuthService {

  def generateChallenge(
    realm: String,
    qop: List[QualityOfProtection],
    algorithm: HashAlgorithm,
  ): UIO[DigestChallenge] = ???

  def validateResponse(
    response: DigestResponse,
    password: Secret,
    method: Method,
    body: Option[String] = None,
  ): ZIO[Any, DigestAuthError, Unit] = {
    val r = response
    for {
      _        <- nonceService.validateNonce(r.nonce, Duration.fromSeconds(NONCE_MAX_AGE)).mapError(errorMapper)
      _        <- nonceService.isNonceUsed(r.nonce, r.nc).mapError(errorMapper)
      expected <- digestService.computeResponse(r.username, r.realm, password, r.nonce, r.nc, r.cnonce, r.algorithm, r.qop, r.uri, method, body)
      _        <- isEqual(expected, r.response)
      _        <- nonceService.markNonceUsed(r.nonce, r.nc)
    } yield ()
  }
}
```

The validation process involves several steps:
1. **Nonce Validation**: Check if the nonce is valid and not expired using the `NonceService`.
2. **Replay Attack Check**: Ensure that the nonce has not been used before with the same `nc` (nonce count) using the `NonceService`.
3. **Calculate the Expected Response**: Compute the expected response digest using the provided parameters and the user's password.
4. **Compare Responses**: Compare the expected response with the one provided by the client.
5. **Mark Nonce as Used**: If the response is valid, mark the nonce as used to prevent replay attacks in future requests.

Among these steps, the remaining part is the comparison of the expected response with the one provided by the client. This is done using a constant-time comparison to prevent timing attacks:

```scala
private def isEqual(expected: String, actual: String): ZIO[Any, InvalidResponse, Unit] = {
  val exp = expected.getBytes("UTF-8")
  val act = actual.getBytes("UTF-8")
  if (MessageDigest.isEqual(exp, act))
    ZIO.unit
  else
    ZIO.fail(InvalidResponse(expected, actual))
}
```

## User Service

Besides the `DigestAuthService`, we also need a `UserService` to store and retrieve user credentials. This service will provide methods to authenticate users and retrieve their passwords:

```scala
import zio._
import zio.Config._

case class User(username: String, password: Secret, email: String)

sealed trait UserServiceError
object UserServiceError {
  case class UserNotFound(username: String)      extends UserServiceError
  case class UserAlreadyExists(username: String) extends UserServiceError
}

trait UserService {
  def getUser(username: String): IO[UserServiceError, User]
  def addUser(user: User): IO[UserServiceError, Unit]
  def updateEmail(username: String, newEmail: String): IO[UserServiceError, Unit]
}
```

For the sake of simplicity, we can use an in-memory store for users, but in a real application, you would typically use a database or another persistent storage solution:

```
case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {
  def getUser(username: String): IO[UserServiceError, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.get(username))
        .orElseFail(UserNotFound(username))
    }

  def addUser(user: User): IO[UserServiceError, Unit] =
    users.get.flatMap { userMap =>
      ZIO.when(userMap.contains(user.username)) {
        ZIO.fail(UserAlreadyExists(user.username))
      } *> users.update(_.updated(user.username, user))
    }

  def updateEmail(username: String, newEmail: String): IO[UserServiceError, Unit] = for {
    currentUsers <- users.get
    user         <- ZIO.fromOption(currentUsers.get(username)).orElseFail(UserNotFound(username))
    _            <- users.update(_.updated(username, user.copy(email = newEmail)))
  } yield ()
}

object UserService {
   private val initialUsers = Map(
      "john"  -> User("john", Secret("password123"), "john@example.com"),
      "jane"  -> User("jane", Secret("secret456"), "jane@example.com"),
      "admin" -> User("admin", Secret("admin123"), "admin@company.com"),
   )

   val live: ZLayer[Any, Nothing, UserService] =
      ZLayer.fromZIO(Ref.make(initialUsers).map(UserServiceLive(_)))
}
```

## Implementing the Middleware

The middleware uses the `UserService` to retrieve user credentials in order to validate the digest. We can make our middleware more flexible by passing the user details to the outgoing request context in case of successful validation, so that downstream handlers can access the authenticated user information:

```scala
import zio._
import zio.http._

object DigestAuthHandlerAspect {

  def apply(
    realm: String,
    qop: List[QualityOfProtection] = List(Auth),
    supportedAlgorithms: Set[DigestAlgorithm] = Set(MD5, MD5_SESS, SHA256, SHA256_SESS, SHA512, SHA512_SESS),
  ): HandlerAspect[DigestAuthService & UserService, User] = {

    def unauthorizedResponse(message: String): ZIO[DigestAuthService, Response, Nothing] =
      ZIO
        .collectAll(
          supportedAlgorithms
            .map(algorithm => ZIO.serviceWithZIO[DigestAuthService](_.generateChallenge(realm, qop, algorithm))),
        )
        .flatMap(challenges =>
          ZIO.fail(
            Response
              .unauthorized(message)
              .addHeaders(Headers(challenges.map(_.toHeader))),
          ),
        )

    HandlerAspect.interceptIncomingHandler[DigestAuthService & UserService, User] {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(digest: Header.Authorization.Digest) =>
            {
              for {
                user        <- ZIO.serviceWithZIO[UserService](_.getUser(digest.username))
                body        <- request.body.asString.option
                authService <- ZIO.service[DigestAuthService]
                _    <- authService.validateResponse(DigestResponse.fromHeader(digest), user.password, request.method, body)
              } yield (request, user)
            }.catchAll(_ => unauthorizedResponse("Authentication failed!"))

          case _ =>
            unauthorizedResponse(s"Missing Authorization header for realm: $realm")
        }
      }

    }
  }

}
```

This middleware takes three parameters:

- `realm`: The realm for the digest authentication, which is a string that identifies the protected area we are going to protect.
- `qop`: A list of `QualityOfProtection` values that specify the quality of protection to use, such as `auth`, `auth-int`, or both. The challenge generated by the middleware will include these values in the `qop` parameter. For example, if the `qop` is set to `List(Auth, AuthInt)`, the challenge will include both `auth` and `auth-int` in the `qop` parameter, allowing the client to choose which one to use.
- `supportedAlgorithms`: A set of `HashAlgorithm` values that specify the supported hashing algorithms for digest authentication. The middleware will generate challenges for each of the supported algorithms, allowing the client to choose which one to use. For example, if the `supportedAlgorithms` is set to `Set(MD5, SHA256)`, the challenge will include both `MD5` and `SHA-256` headers, allowing the client to pick one of them.

To decode and extract the digest parameters from an incoming request, we do not require any extra effort, as ZIO HTTP extracts the `Authorization` header and makes it accessible via `request.header(Header.Authorization)`. If the header is present, we can check if it is of type `Header.Authorization.Digest`, which contains all the necessary parameters for digest authentication.


## Applying the Middleware

Now we can finally use this middleware in our ZIO HTTP application to protect our `/profile/me` route:

```scala
val profileRoute: Route[DigestAuthService & UserService, Nothing] =
  Method.GET / "profile" / "me" -> handler { (_: Request) =>
    for {
      user <- ZIO.service[User]
    } yield Response.text(
      s"Hello ${user.username}! This is your profile: \n Username: ${user.username} \n Email: ${user.email}",
    )
  
  } @@ DigestAuthHandlerAspect(realm = "User Profile")
```

Applying this middleware to our route requires two services in the environment: `DigestAuthService` and `UserService`. The `DigestAuthService` is responsible for generating challenges and validating responses, while the `UserService` provides user credentials for authentication.

The beautiful thing about this middleware is that it extracts the authenticated user from the request and makes it available to downstream handlers. This allows us to access the user information in the handler using the ZIO environment (`ZIO.service[User]`) without having to pass it explicitly.

Let's write another route for updating the user's email, which should also be protected by the digest authentication middleware. The difference is that this route will require the user to provide a new email address in the request body to update their profile:

```
PUT /profile/me/email HTTP/1.1
Host: localhost:8080
Authorization: Digest ...
Content-Type: application/json
Content-Length: 42

{
  "email": "my-new-email@example.com"
}
```

Since this route has a request body, we need to ensure that the `qop` is set to `auth-int`, which means that the request body will be included in the digest calculation. This way, the server can verify the integrity of the request body and ensure that it has not been tampered with:

```scala
val updateEmailRoute: Route[DigestAuthService & UserService, Nothing] =
  Method.PUT / "profile" / "email" ->
    Handler.fromZIO(ZIO.service[UserService]).flatMap { userService =>
      handler { (req: Request) =>
        for {
          user <- ZIO.service[User]
          updateRequest <- req.body
            .to[UpdateEmailRequest]
            .mapError(error => Response.badRequest(s"Invalid JSON (UpdateEmailRequest): $error"))
          _ <- userService
            .updateEmail(user.username, updateRequest.email)
            .logError(s"Failed to update email for user ${user.username}")
            .mapError(_ => Response.internalServerError(s"Failed to update email!"))
        } yield Response.text(
          s"Email updated successfully for user ${user.username}! New email: ${updateRequest.email}",
        )
      } @@ DigestAuthHandlerAspect(realm = "User Profile", qop = List(AuthInt))
    }
```

Here are some points to consider when writing this route:
- First, we used `List(AuthInt)` as the Quality of Protection (`qop`) parameter. This enforces the client to include the request body in the digest calculation, ensuring that the integrity of the request body is verified by the server. Please note that we can also include the `Auth` value in the `qop` list, which allows the client to optionally pick either `auth` or `auth-int` for the authentication request.
- Second, in the implementation of the route, we extracted the user details from the ZIO environment using the `ZIO.service[User]` method, which is made available by the `DigestAuthHandlerAspect`. Besides this, we also need to obtain the `UserService` from the ZIO environment to update the user's email. If we do both of these inside one handler, the type of our handler becomes `Handler[User & UserService, Response, Request, Response]`. On the other hand, the type of the `DigestAuthHandlerAspect` is `HandlerAspect[DigestAuthService & UserService, User]`. The type of the handler aspect tells us that it can only be applied to a handler whose environment is a subset of `User`. So it can't be applied to a handler that requires both `User` and `UserService` in its environment. To solve this, we chained two handlers using `flatMap`, where the first handler extracts the `UserService` from the environment, and the second handler uses it to update the user's email. The inner handler has the type `Handler[User, Response, Request, Response]`, which is compatible with the `DigestAuthHandlerAspect` that requires only `User` in its environment. So now we can apply the `DigestAuthHandlerAspect` to the inner handler and chain it with the outer handler that extracts the `UserService` from the environment.

## Writing a Client to Test the Digest Authentication

As we saw, the digest authentication process involves at least two requests: the first request to get the challenge and the second request to send the response (response to the challenge). However, this is not always the case. If the client already has a valid `nonce`, it can use it for subsequent requests without needing to get a new challenge until the nonce expires.

To create a client that reuses `nonce` between calls, we need a client that manages state. First, let's define the interface of the client:

```scala
trait DigestAuthClient {
   def makeRequest(request: Request): ZIO[Any, DigestAuthError, Response]
}
```

We need `Client`, `DigestService` and `NonceService` to implement the `DigestAuthClient`. We also need to maintain two states: 
- one for the digest challenge parameters (`Ref[Option[DigestChallenge]]`). We made it optional because the first time the client makes a request, it won't have any cached digest challenge parameters.
- another for maintaining the nonce count (`Ref[NC]`).

To simplify the client, let's pass the username and password when creating the client. The client will use these credentials to compute the digest response when making requests:

```scala
final case class DigestAuthClientImpl(
  challengeRef: Ref[Option[DigestChallenge]],
  ncRef: Ref[NC],
  client: Client,
  digestService: DigestService,
  nonceService: NonceService,
  username: String,
  password: Secret,
) extends DigestAuthClient {
  override def makeRequest(request: Request): ZIO[Any, DigestAuthError, Response] =
    for {
      authenticatedRequest <- authenticate(request)
      response             <- client.batched(authenticatedRequest).orDie
  
      finalResponse <-
        if (response.status == Status.Unauthorized) {
          for {
            _             <- ZIO.debug("Unauthorized response received!")
            _             <- handleUnauthorized(response)
            retryRequest  <- authenticate(request)
            retryResponse <- client.batched(retryRequest).orDie
            _             <- ZIO.debug("Retrying request with updated authentication headers")
          } yield retryResponse
        } else ZIO.succeed(response)
    } yield finalResponse
}
```

The only method we need to implement is `makeRequest`, which takes a `Request`, performs an authenticated request, and finally returns the `Response`. This method implements the core digest authentication flow by handling both initial authentication challenges and request retries. 

The method first attempts to authenticate the incoming request using any cached digest challenge parameters, then sends it to the server. If the response returns successfully (non-401 status), it's returned immediately. However, if the server responds with **401 Unauthorized**, the method enters the standard digest authentication challenge-response flow: it parses the WWW-Authenticate header to extract and cache the new digest challenge parameters, then computes the response and re-authenticates the original request with a response digest.

Here is the implementation of the `authenticate` helper method. It takes a `Request`, uses the cached digest to compute the response, and finally adds the proper digest header to the given request:

```scala
def authenticate(request: Request): ZIO[Any, Nothing, Request] =
  challengeRef.get.flatMap {
    case None =>
      ZIO.debug(s"No cached digest!") *>
        ZIO.debug("Sending request without auth header to get a fresh challenge") *>
        ZIO.succeed(request)

    case Some(challenge) =>
      for {
        _      <- ZIO.debug(s"Cached digest challenge found, using it to compute the digest response!")
        cnonce <- nonceService.generateNonce
        nc     <- ncRef.updateAndGet(nc => NC(nc.value + 1))
        selectedQop = selectQop(request, challenge.qop)
        _ <- ZIO.debug(s"Selected QOP: $selectedQop")
        uri = URI.create(request.url.path.toString)
        body <- request.body.asString.map(Some(_)).orDie

        response <- digestService.computeResponse(
          username = username,
          realm = challenge.realm,
          uri = uri,
          algorithm = challenge.algorithm,
          qop = selectedQop,
          cnonce = cnonce,
          nonce = challenge.nonce,
          nc = nc,
          password = password,
          method = request.method,
          body = body,
        )

        authHeader = Header.Authorization.Digest(
          response = response,
          username = username,
          realm = challenge.realm,
          nonce = challenge.nonce,
          uri = uri,
          algorithm = challenge.algorithm.toString,
          qop = selectedQop.toString,
          nc = nc.value,
          cnonce = cnonce,
          userhash = false,
          opaque = challenge.opaque.getOrElse(""),
        )
        _ <- ZIO.debug(s"nonce: ${challenge.nonce}")
        _ <- ZIO.debug(s"nc: $nc")
        _ <- ZIO.debug(s"response: $response")
      } yield request.addHeader(authHeader)
  }
```

We also need another helper method that chooses between two security levels based on request characteristics and server capabilities. It takes an HTTP request and a set of supported QoP values as parameters, then returns `QualityOfProtection.AuthInt` (authentication with integrity protection) if the request has a non-empty body and the server supports `AuthInt`, otherwise it defaults to `Auth` (authentication only):

```scala
def selectQop(request: Request, supportedQop: Set[QualityOfProtection]): QualityOfProtection =
  if (!request.body.isEmpty && supportedQop.contains(QualityOfProtection.AuthInt))
    QualityOfProtection.AuthInt
  else
    QualityOfProtection.Auth
```

For extracting and storing the digest challenge from the response of unauthorized calls, here is another helper method called `handleUnauthorized`:

```scala
def handleUnauthorized(response: Response): ZIO[Any, DigestAuthError, Unit] =
  response.header(Header.WWWAuthenticate) match {
    case Some(header: Header.WWWAuthenticate.Digest) =>
      for {
        _            <- ZIO.debug(s"Received a new WWW-Authenticate Digest challenge")
        newChallenge <- DigestChallenge.fromHeader(header)
        _            <- ZIO.debug(s"Caching digest challenge")
        _            <- challengeRef.set(Some(newChallenge))
        _            <- ncRef.set(NC(0)) // Reset nonce count
      } yield ()
    case _                                           =>
      ZIO.fail(UnsupportedAuthHeader("Expected WWW-Authenticate header"))
  }
```

Storing the digest challenge parameters enables reusing the same challenge for subsequent requests until the nonce expires or the server sends a new challenge. When receiving a new challenge, we reset the nonce count (`nc`) to zero so the next request starts with `nc = 1`. This prevents sending out-of-order nonce counts that would cause the server to reject the request since the server tracks nonce count usage for each nonce it issues.

Now, we are ready to write some API calls and see how digest authentication works in practice:

```scala
val program: ZIO[Client with DigestAuthClient, Throwable, Unit] =
  for {
    authClient <- ZIO.service[DigestAuthClient]

    profileEndpoint <- ZIO.fromEither(URL.decode(s"$url/profile/me"))
    emailEndpoint   <- ZIO.fromEither(URL.decode(s"$url/profile/email"))

    _         <- ZIO.debug("\nFirst call: GET /profile/me")
    response1 <- authClient.makeRequest(Request(method = Method.GET, url = profileEndpoint))
    body1     <- response1.body.asString
    _         <- ZIO.debug(s"Received response: $body1")

    _         <- ZIO.debug("\nSecond call: GET /profile/me")
    response2 <- authClient.makeRequest(Request(method = Method.GET, url = profileEndpoint))
    body2     <- response2.body.asString
    _         <- ZIO.debug(s"Received response: $body2")

    _ <- ZIO.debug("\nThird call: PUT /profile/email")
    email        = UpdateEmailRequest("my-new-email@example.com")
    emailRequest = Request(method = Method.PUT, url = emailEndpoint, body = Body.from(email))
    response3 <- authClient.makeRequest(emailRequest)
    body3     <- response3.body.asString
    _         <- ZIO.debug(s"Received response: $body3")

    _         <- ZIO.debug("\nFourth call: GET /profile/me")
    response4 <- authClient.makeRequest(Request(method = Method.GET, url = profileEndpoint))
    body4     <- response4.body.asString
    _         <- ZIO.debug(s"Received response: $body4")
  } yield ()
```

To run this program, you need to have the `DigestAuthClient` and `DigestAuthService` in your ZIO environment:

```scala
program.provide(
   Client.default,
   NonceService.live,
   DigestService.live,
   DigestAuthClient.live(USERNAME, PASSWORD),
)
```

This ZIO program executes four sequential HTTP calls:

1. `GET /profile/me` (initial request)
2. `GET /profile/me` (second request using cached authentication)
3. `PUT /profile/email` (update email address)
4. `GET /profile/me` (verify the email update)

Let's discuss each call in detail:

**First Call:** Since no digest challenge is cached, the client sends an unauthenticated request. The server responds with `401 Unauthorized` and includes a `WWW-Authenticate` header containing the digest challenge parameters (realm, nonce, algorithm, etc.). The client parses and caches this challenge information, computes the digest response using the user's credentials, and retries the request with the computed authentication header. This results in a successful response containing the user's profile.

**Second Call:** The client reuses the cached digest challenge to compute the authentication response without requiring a server round-trip for challenge negotiation. It increments the nonce count (`nc`) to `00000002`, ensuring the server recognizes this as a distinct request and preventing replay attacks. The authentication succeeds immediately without an initial `401` response, demonstrating efficient authentication state reuse.

**Third Call:** The `PUT /profile/email` request attempts to update the user's email address. The client computes the digest response using the cached challenge and increments the nonce count to `00000003`. However, since this endpoint requires `auth-int` (authentication with integrity protection) quality of protection to verify the request body hasn't been tampered with, the server rejects this request with `401 Unauthorized` because the request's response header is computed using the wrong `qop` parameter, i.e., `auth`. The client receives a new digest challenge specifying `auth-int` QOP, caches this updated challenge, and retries the request. For the new challenge, the nonce count starts from `00000001`, and the client successfully authenticates using `auth-int`, which includes the hash of the request body in the digest calculation.

**Fourth Call:** The final `GET /profile/me` request retrieves the updated profile information. The client uses the most recently cached challenge (from the PUT request), incrementing the nonce count to `00000002`. Since this is a GET request with an empty body, the client uses standard `auth` quality of protection rather than `auth-int`. The server responds with the updated profile, confirming the email address change was successful.

This design provides transparent handling of digest authentication, allowing clients to make requests normally without manually managing the challenge-response handshake. The method efficiently addresses both cold-start scenarios, where the first request triggers a challenge, and challenge-refresh situations, where server nonces expire, while maintaining authentication state between requests for optimal performance.

## Writing a Web Client to Test the Digest Authentication

Similar to the client we wrote in the previous section, we can write a web client. The overall structure of the web client is similar, but since the implementation details go beyond the scope of this guide, we will not cover them here. However, by running the `DigestAuthenticationServer`, it will serve the `digest-auth-client.html` at `http://localhost:8080`, which is a simple web client that allows you to demo and test the digest authentication flow in your browser. You can use it to send requests to the server and see how digest authentication works in practice.