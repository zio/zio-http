---
id: digest-authentication
title: "Securing Your APIs: Digest Authentication"
---

Digest Authentication provides enhanced security over Basic Authentication by addressing fundamental vulnerabilities in credential transmission. This implementation guide demonstrates how to build Digest Authentication using ZIO HTTP, covering both server-side middleware and client-side integration patterns.

## Understanding Digest Authentication

Digest Authentication, standardized in RFC 2617 and extended in RFC 7616, implements an HTTP authentication scheme utilizing a **challenge-response mechanism with cryptographic hashing**. Unlike Basic Authentication, which transmits credentials in Base64 encoding, Digest Authentication employs hash functions to generate cryptographic digests that verify credential knowledge without exposing plaintext passwords over the network.

### The Challenge-Response Protocol Flow

The authentication protocol follows this standardized sequence:

1. **Initial Request**: The client initiates a request to a protected resource.
2. **Authentication Challenge**: The server responds with `401 Unauthorized` status and a `WWW-Authenticate` header containing:
    - `realm`: A string identifying the protection space
    - `nonce`: A server-generated cryptographic nonce. Nonces are unique values that change for each authentication challenge to ensure that each request is authenticated only once, preventing replay attacks
    - `qop`: Quality of Protection specification (typically "auth" or "auth-int", or supporting both). This parameter defines the protection level for the request, indicating whether only authentication is required or if message integrity protection is also needed
    - `algorithm`: Cryptographic hash algorithm specification (MD5, SHA-256, etc.)
3. **Challenge Response**: The client computes a cryptographic digest using the challenge parameters and credentials, then resubmits the request with an `Authorization` header containing the response to the challenge.
4. **Authentication Verification**: The server validates the digest and either grants access or denies authorization.

The following examples illustrate the complete authentication handshake:

1. **Initial Resource Access Attempt**: The client requests a protected resource at `/profile/me` without authentication credentials:

```http
GET /profile/me HTTP/1.1
Host: localhost:8080
```

2. **Server Authentication Challenge**: The server responds with "401 Unauthorized" and presents an authentication challenge:

```http
HTTP/1.1 401 Unauthorized
content-length: 0
date: Thu, 24 Jul 2025 07:27:40 GMT
www-authenticate: Digest realm="User Profile", nonce="MTc1MzM0MjA2MDI0NDpmbXNGK2dTblF4WEVwN1gwWktMVllRPT0=", opaque="uSla+F7cMBsB/t3K9OCLzg==", stale=false, algorithm=MD5, qop="auth", charset=UTF-8, userhash=false
```

The `WWW-Authenticate` header delivers the authentication challenge within an HTTP 401 Unauthorized response, indicating that the requested resource requires authentication within the specified protection realm and mandating cryptographic digest computation using the provided parameters.

3. **Cryptographic Digest Computation**: The client computes a cryptographic digest response using the specified hash algorithm applied to a structured combination of challenge parameters and user credentials. This process generates a hash value that cryptographically proves credential possession without transmitting plaintext passwords.

The digest response calculation follows this standardized algorithm:

```http
HA1 = H(username:realm:password)
if (qop == "auth-int") 
    HA2 = H(method:uri:body)
else 
    HA2 = H(method:uri) 
response = H(HA1:nonce:nc:cnonce:qop:HA2)
```

**Parameter Definitions:**

- **`username`**: The user identifier for authentication
- **`realm`**: A server-defined protection space identifier that logically partitions protected resources
- **`password`**: The user's authentication secret
- **`method`** and **`uri`**: The HTTP request method (GET, POST, etc.) and the requested URI path. Including these values cryptographically binds the digest to the specific request, preventing attackers from reusing valid digests for different requests (mitigates request tampering)
- **`nonce`**: A server-generated, temporally-limited unique value that varies for each authentication challenge. This server-controlled parameter ensures temporal uniqueness and prevents replay attacks by invalidating previously captured authentication attempts
- **`nc` (nonce count)**: A hexadecimal counter (initialized at 00000001) that increments with each request utilizing the same nonce value. This enables the server to detect duplicate or out-of-sequence requests, providing protection against replay attacks even when nonces are reused during their validity period
- **`cnonce` (client nonce)**: A client-generated cryptographic nonce that contributes entropy from the client side. This parameter ensures that identical server challenges produce different digest responses, preventing certain cryptographic attacks and enabling client request correlation
- **`qop`** (Quality of Protection): Defines the protection level - "auth" for authentication-only or "auth-int" for authentication with message integrity protection

The digest authentication mechanism provides several cryptographic security enhancements over HTTP Basic Authentication:

1. **Credential Protection**: User passwords remain client-side and are never transmitted across the network, even in hashed form.
2. **Replay Attack Resistance**: The combination of server nonces and nonce counts creates unique authentication tokens for each request, rendering captured authentication data unusable for subsequent unauthorized requests.
3. **Request Integrity**: By incorporating the HTTP method and URI into the digest calculation, the protocol prevents attackers from redirecting valid authentication tokens to different endpoints or modifying request methods.
4. **Mutual Authentication**: The client can verify server authenticity through the server's ability to validate client-generated digests, while the server confirms client identity through successful digest verification.
5. **Message Integrity Protection**: When utilizing `qop="auth-int"`, the protocol includes the request body hash in the authentication calculation, ensuring both authentication and message integrity.

Despite these security enhancements, HTTPS deployment remains essential since Digest Authentication does not encrypt the communication channel. HTTPS is mandatory for production systems to provide comprehensive security through transport-layer encryption.

Now that we have received the digest challenge, let's compute the digest response using the provided parameters:

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

After calculating the digest response, the client constructs the `Authorization` header with the computed authentication response:

```http
GET /profile/me HTTP/1.1
Host: localhost:8080
Authorization: Digest username="john", realm="User Profile", nonce="MTc1MzM0MjA2MDI0NDpmbXNGK2dTblF4WEVwN1gwWktMVllRPT0=", uri="/profile/me", algorithm="MD5", qop="auth", nc="00000001", cnonce="71n315lg67i4kr9473e5hw", response="f7e07fe43aa7a7e3a296edf8f3b3772a", userhash=false, opaque="uSla+F7cMBsB/t3K9OCLzg=="
```

4. **Server Authentication Verification**: The server validates the digest by recalculating it using identical parameters and stored credentials for user "john". Upon successful digest verification, the server grants access to the requested resource. The server extracts the "username" parameter from the authorization header, retrieves the user's password from its credential store, and computes the expected digest using the same algorithm and parameters as the client. When the computed digest matches the client-provided digest, the server responds with the requested resource:

```http
HTTP/1.1 200 OK
content-length: 76
content-type: text/plain
date: Wed, 23 Jul 2025 12:59:32 GMT

Hello john! This is your profile: 
 Username: john 
 Email: john@example.com
```

If digest verification fails, the server responds with `401 Unauthorized`, indicating authentication failure and presenting a new challenge in the `WWW-Authenticate` header.

This was the simple digest authentication flow. In practice, the implementation may involve additional complexities such as nonce expiration, nonce reuse prevention, and handling of quality of protection levels. We will discuss these in the implementation section.

## Digest Authentication Implementation

To implement Digest Authentication in ZIO HTTP, we develop middleware that intercepts incoming requests, validates `Authorization` headers, and authenticates digest credentials against stored user data. 

ZIO HTTP does not provide built-in Digest Authentication support but offers an excellent foundation for implementing it as custom middleware.

### Implementation Overview

The middleware implementation handles two primary authentication scenarios based on `Authorization` header presence:

- **Digest Authentication Present**: When the request contains a `Header.Authorization.Digest`, the client is responding to a server challenge. The middleware validates the digest against stored user credentials. Valid digests permit request continuation; invalid digests trigger a `401 Unauthorized` response with a new challenge in the `WWW-Authenticate` header.
- **Missing Authentication**: When no authorization header exists or an unsupported authentication header is received, the middleware responds with `401 Unauthorized` status and a `WWW-Authenticate` header containing a new challenge.

So, the general structure of the middleware is as follows:

```scala
val digestAuthHandler: HandlerAspect[Any, Unit] =
   HandlerAspect.interceptIncomingHandler[Any, Unit] {
      Handler.fromFunctionZIO[Request](request =>
         request.header(Header.Authorization) match {
            // Process Digest Authorization header
            case Some(authHeader: Header.Authorization.Digest) =>
               // 1. Retrieve user credentials from credential store using header username
               // 2. Validate digest against stored user credentials
               // 3. On success, allow request continuation
               // 4. On failure, respond with 401 Unauthorized and new challenge
   
            // No authentication header present or unsupported authentication header, issue challenge
            case _ =>
             // Respond with 401 Unauthorized and authentication challenge
         },
      )
   }
```

### Digest Authentication Service Interface

The implementation requires two core operations:

- **Challenge Generation**: When clients attempt to access protected resources without authentication, the system generates challenges containing `realm`, `nonce`, `algorithm`, and quality of protection (`qop`) parameters. The server generates and transmits the challenge via the `WWW-Authenticate` header in the response.
- **Response Validation**: When clients provide digest responses in `Authorization` headers, the server validates them against stored user credentials by computing expected digests and comparing them with client-provided values.

These functionalities are encapsulated within a dedicated `DigestAuthService`:

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

The `DigestChallenge` data structure encapsulates challenge generation parameters including `realm`, `nonce`, `opaque`, `algorithm`, and `qop`:

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

The `DigestChallenge#toHeader` method transforms the `DigestChallenge` into a `Header.WWWAuthenticate.Digest` for HTTP response integration. The `DigestChallenge.fromHeader` method provides a type-safe conversion from `Header.WWWAuthenticate.Digest` header to `DigestChallenge`.

The `DigestResponse` data structure represents client responses within `Authorization` headers:

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

The `DigestResponse.fromHeader` constructor provides type-safe conversion from `Header.Authorization.Digest` to `DigestResponse`.

The `DigestAuthError` sealed trait represents authentication process errors:

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

The `DigestAlgorithm` enumeration represents cryptographic hash algorithms used in Digest Authentication, including standard algorithms like MD5, SHA-256, and SHA-512, along with their session variants:

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

The `QualityOfProtection` sealed trait represents protection levels in Digest Authentication, encompassing `auth` for authentication-only and `auth-int` for authentication with message integrity protection:

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

Before diving into the implementation details of the `DigestAuthService`, we need to implement two supporting services: nonce management and digest computation. These services will handle nonce generation, validation, and digest response computation based on the challenge parameters.

### Nonce Management Service

Challenge generation requires robust `nonce` generation. The `nonce` serves as a unique server-generated value for each challenge, preventing replay attacks and ensuring authentication freshness. It must be a cryptographically secure random value that changes for each request/session, rendering replay of previous requests impossible.

While no specific algorithm is mandated for nonce generation, several industry-standard approaches exist:

- **Cryptographically Random Nonce**: Generate nonces using cryptographically secure random number generators. This approach requires the server to maintain used nonce tracking to prevent replay attacks. After successful digest validation, the server marks the nonce as consumed, preventing reuse in subsequent requests. This approach typically does not utilize nonce count (`nc`), as each nonce is unique per session and cannot be reused:

```scala
val nonce = 
  Random.nextBytes(16)
    .map(_.toArray)
    .map(Base64.getEncoder.encodeToString) 
// Example nonce: pY0+z+EeTgrXwq/Y3L8lGA==
```

- **Temporal Nonce**: Generate nonces combining current timestamps with random values or timestamp hashes. This enables servers to validate nonce freshness and reject expired nonces, reducing replay attack windows. After successful digest validation, the server verifies nonce validity (non-expired status) and marks the `nonce` and `nc` combination as used, preventing reuse in subsequent requests. Subsequent requests with identical nonces must increment the `nc` value to indicate new requests. This approach enables nonce reuse for multiple requests until session expiration.

This implementation utilizes the temporal nonce generation approach, providing superior nonce expiration policy control. We implement a `NonceService` to handle nonce generation, validation, and usage tracking:

```scala
trait NonceService {
  def generateNonce: UIO[String]
  def validateNonce(nonce: String, maxAge: Duration): ZIO[Any, NonceError, Unit]
  def isNonceUsed(nonce: String, nc: NC): ZIO[Any, NonceError, Unit]
  def markNonceUsed(nonce: String, nc: NC): UIO[Unit]
}
```

The `NC` class represents the nonce count (`nc`) as an 8-digit hexadecimal string with zero-padding:

```scala
case class NC(value: Int) extends AnyVal {
  override def toString: String = toHexString

  private def toHexString: String = f"$value%08x"
}

object NC {
  implicit val ordering: Ordering[NC] = Ordering.by(_.value)
}
```

Implementation of the `generateNonce` method:

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

This nonce generation mechanism in digest authentication is designed to create cryptographically secure, self-verifying, time-bounded tokens. It uses HMAC (Hash-based Message Authentication Code) rather than plain SHA256. This is crucial because only the server with the `secretKey` can generate valid hashes, and no other party can forge valid nonces without access to this key.

Another interesting aspect of this nonce generation is that it combines a timestamp with a hash of the timestamp, ensuring that each nonce is unique and time-sensitive:

```
nonce = Base64(timestamp:Base64(HMAC(timestamp)))
```

Each nonce is tied to a specific timestamp, which is the current time in milliseconds. This enables the server to extract the timestamp of received nonces to check the age of the nonce and determine if it is still valid.

So, upon receiving client nonces, servers must perform two checks:
- **Temporal Validation**: Verify timestamps fall within acceptable ranges (e.g., not exceeding 5 minutes age) to prevent replay attack utilization of expired requests.
- **Cryptographic Verification**: Confirm hash values match computed hashes for given timestamps using identical secret keys. This ensures the server generated the nonce and prevents tampering.

If the nonce passes both checks, it is considered valid. Let's implement the `validateNonce` method:

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

After nonce validation, the system must verify that the `nonce` and `nc` combination has not been previously used. This is accomplished by maintaining a registry of used nonces with their associated counts (`nc`). Discovery of nonces within this registry indicates prior usage, warranting request rejection. This mechanism prevents intra-session replay attacks where attackers might attempt to reuse valid nonces from previous requests before expiration.

To implement this method, called `isNonceUsed`, we can naively use a `Ref` to store the used nonces in memory, which maintains a map of nonces to sets of counts (`nc`) that have been used. But a better approach is to use a `Ref` to store a map of nonces to their last used `nc` values. This allows us to check if the current `nc` is greater than or equal to the last used `nc` for that nonce, which indicates that the nonce has already been used in a previous request or is out of sequence:

```scala
final case class NonceServiceLive(
    usedNonces: Ref[Map[String, NC]],
    secretKey: SecretKey
  ) extends NonceService {
  def generateNonce: UIO[String] = ???
  def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean] = ???

  def isNonceUsed(nonce: String, nc: NC): ZIO[Any, NonceError, Unit] =
    for {
      usedNoncesMap <- usedNonces.get
      _             <- usedNoncesMap.get(nonce) match {
        case Some(lastUsedNc) if nc <= lastUsedNc                 =>
          ZIO.fail(NonceAlreadyUsed(nonce, nc))
        case Some(lastUsedNc) if nc.value != lastUsedNc.value + 1 =>
          ZIO.fail(NonceOutOfSequence(nonce, nc))
        case _                                                    =>
          ZIO.unit
      }
    } yield ()
    
  def markNonceUsed(nonce: String, nc: NC): UIO[Unit] = ???
}
```

Similarly, we need to implement the `markNonceUsed` method to mark a `nonce` and `nc` as used after a successful authentication:

```scala
final case class NonceServiceLive(
    usedNonces: Ref[Map[String, NC]],
    secretKey: SecretKey
  ) extends NonceService {
  def generateNonce: UIO[String] = ???
  def validateNonce(nonce: String, maxAge: Duration): UIO[Boolean] = ???
  def isNonceUsed(nonce: String, nc: NC): ZIO[Any, NonceError, Unit] = ???

  def markNonceUsed(nonce: String, nc: NC): UIO[Unit] =
    usedNonces.update { nonces =>
      val currentMax = nonces.getOrElse(nonce, NC(0))
      nonces.updated(nonce, currentMax max nc)
    }
}
```

The nonce generation and validation logic is now encapsulated within the `NonceService`, which can be injected into the `DigestAuthService` for nonce management during digest authentication processes.

The next implementation step involves digest response computation.

### Computation of Digest Response

To compute the digest response, we need to implement a service that calculates the digest response based on the provided parameters such as `username`, `realm`, `password`, `nonce`, `nc`, `cnonce`, `algorithm`, `qop`, `uri`, and `method`. Here is a simple interface for the digest computation service:

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

As discussed earlier, the digest response is calculated using the following formula:

```
HA1 = H(username:realm:password)
if (qop == "auth-int") 
    HA2 = H(method:uri:body)
else 
    HA2 = H(method:uri) 
response = H(HA1:nonce:nc:cnonce:qop:HA2)
```

However, this is the simple version of the digest calculation when the algorithm is a regular algorithm, such as `MD5` or `SHA-256`. If the algorithm is a session algorithm (ending with "-sess", such as `MD5-sess` or `SHA-256-sess`), the `HA1` is calculated differently:

```
If algorithm ends in "-sess":
    HA1 = H(H(username:realm:password):nonce:cnonce)
otherwise:
    HA1 = H(username:realm:password)
    
if (qop == "auth-int") 
    HA2 = H(method:uri:body)
else 
    HA2 = H(method:uri) 
response = H(HA1:nonce:nc:cnonce:qop:HA2)
```

In regular algorithms, the `HA1` is a constant value for a given `username`, `realm`, and `password`. This means that if an attacker captures the `HA1` value, they can use it to generate valid digests for any request made by that user.

Session algorithms are variants of digest algorithms that enhance security by making the hash depend not only on the `username`, `realm`, and `password` but also on values that are unique to each session, such as the server nonce (`nonce`) and the client nonce (`cnonce`).

Even if an attacker obtains `H(username:realm:password)` through network monitoring, sniffing, or memory dumps, they cannot generate valid digests without knowing the specific `nonce` and `cnonce` values.

As a security best practice, always prefer `-sess` algorithms when both client and server support them, particularly for unsecured network communications.

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

The calculation of `a1` is straightforward. Based on the type of algorithm, we can use either the simple formula or the session formula:

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
    case MD5_SESS | SHA256_SESS | SHA512_SESS =>
      hash(baseA1, algorithm)
        .map(ha1 => s"$ha1:$nonce:$cnonce")
    case _                                    =>
      ZIO.succeed(baseA1)
  }
}
```

The `a2` computation encompasses HTTP method and URI hash calculation. For `auth-int` quality of protection, the request body is included in the calculation:

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

Here is the final response generation:

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

The `hash` function computes cryptographic hashes using specified algorithms, implemented with Java's `MessageDigest` or equivalent libraries:

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

Now that all required services are implemented, we are ready to implement the `DigestAuthService` that uses nonce management and digest computation services to handle authentication challenges and response validation.

We have to implement two main methods in the `DigestAuthService`:
1. `generateChallenge`: Generates a digest challenge with parameters like `realm`, `nonce`, `qop`, and `algorithm`.
2. `validateResponse`: Validates client-provided digest responses against stored user credentials, ensuring nonce freshness and preventing replay attacks.

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

The `opaque` parameter is a server-selected optional value that clients must return unchanged within `Authorization` headers when responding to challenges.

Here is the `validateResponse` method implementation, which validates the client's digest response by comparing it with server-calculated expected values:

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
    supportedQop: Set[QualityOfProtection],
    body: Option[String] = None,
  ): ZIO[Any, DigestAuthError, Unit] = {
    val r = response
    def mapNonceError: NonceError => InvalidResponse = _ => InvalidResponse(r.response)
    for {
      _        <- ZIO.when(!supportedQop.contains(r.qop))(ZIO.fail(UnsupportedQop(r.qop.name)))
      _        <- nonceService.validateNonce(r.nonce, Duration.fromSeconds(NONCE_MAX_AGE)).mapError(mapNonceError)
      _        <- nonceService.isNonceUsed(r.nonce, r.nc).mapError(mapNonceError)
      expected <- digestService.computeResponse(r.username, r.realm, password, r.nonce, r.nc, r.cnonce, r.algorithm, r.qop, r.uri, method, body)
      _        <- isEqual(expected, r.response)
      _        <- nonceService.markNonceUsed(r.nonce, r.nc)
    } yield ()
  }
}
```

Please note that in the `DigestAuthService` layer, we do not expose nonce errors directly to the client for security reasons. Instead, all nonce-related errors are mapped to `InvalidResponse`, a more generic error type.

The validation process involves several steps:
1. **Nonce Validation**: Check if the nonce is valid and not expired using the `NonceService`.
2. **Replay Attack Prevention**: Ensure the `nonce` and `nc` combination has not been previously used.
3. **Calculate the Expected Response**: Compute the expected response digest using the provided parameters and the user's password.
4. **Compare Responses**: Compare the expected response with the one provided by the client.
5. **Mark Nonce as Used**: If the response is valid, mark the nonce as used to prevent replay attacks in future requests.

Among these steps, the remaining implementation detail is the comparison of the expected response with the one provided by the client. This is done using a constant-time comparison to prevent timing attacks:

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

We used `MessageDigest.isEqual()`, which is a secure, constant-time comparison method provided by Java's cryptography APIs specifically designed for comparing sensitive cryptographic values.

## User Management Service

Besides the `DigestAuthService`, we also need a `UserService` to store and retrieve user credentials:

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

```scala
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

We initialize the `UserService` with some sample users, which can be used for testing purposes. The `getUser` method retrieves a user by username, while `addUser` allows adding new users, and `updateEmail` updates the user's email address.

## Middleware Implementation

The middleware uses the `UserService` to retrieve user credentials in order to validate the digest. We can make our middleware more flexible by passing the user details to the outgoing request context in case of successful validation, so that downstream handlers can access the authenticated user information:

```scala
import zio._
import zio.http._

object DigestAuthHandlerAspect {

  def apply(
    realm: String,
    qop: Set[QualityOfProtection] = Set(Auth),
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
                user <-
                  ZIO
                    .serviceWithZIO[UserService](_.getUser(digest.username))
                body <- request.body.asString.option
                _    <- ZIO
                  .serviceWithZIO[DigestAuthService](
                    _.validateResponse(DigestResponse.fromHeader(digest), user.password, request.method, qop, body),
                  )
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

This middleware accepts three configuration parameters:

- **`realm`**: The authentication realm string identifying the protected resource space
- **`qop`**: A set of `QualityOfProtection` values that specify the quality of protection to use, such as `auth`, `auth-int`, or both. The challenge generated by the middleware will include these values in the `qop` parameter. For example, if `qop` is set to `Set(Auth, AuthInt)`, the challenge will include both `auth` and `auth-int` in the `qop` parameter, allowing the client to choose which one to use.
- **`supportedAlgorithms`**: A set of `DigestAlgorithm` values that specify the supported hashing algorithms for digest authentication. The middleware will generate challenges for each of the supported algorithms, allowing the client to choose which one to use. For example, if `supportedAlgorithms` is set to `Set(MD5, SHA256)`, the challenge will include both `MD5` and `SHA256` headers, allowing the client to pick one of them.

## Middleware Application

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
      } @@ DigestAuthHandlerAspect(realm = "User Profile", qop = Set(AuthInt))
    }
```

Here are some points to consider when writing this route:

First, we use `Set(AuthInt)` as the Quality of Protection (`qop`) parameter. This requires the client to include the request body in the digest calculation, ensuring that the integrity of the request body is verified by the server. Please note that we can also include the `Auth` value in the `qop` list, which allows the client to optionally choose either `auth` or `auth-int` for the authentication request.

Second, in the implementation of the route, we extract the user details from the ZIO environment using the `ZIO.service[User]` method, which is made available by the `DigestAuthHandlerAspect`. Besides this, we also need to obtain the `UserService` from the ZIO environment to update the user's email. If we do both of these inside one handler, the type of our handler becomes `Handler[User & UserService, Response, Request, Response]`. On the other hand, the type of the `DigestAuthHandlerAspect` is `HandlerAspect[DigestAuthService & UserService, User]`. The type of the handler aspect tells us that it can only be applied to a handler whose environment is a subset of `User`. Therefore, it can't be applied to a handler that requires both `User` and `UserService` in its environment. To solve this, we chain two handlers using `flatMap`, where the first handler extracts the `UserService` from the environment, and the second handler uses it to update the user's email. The inner handler has the type `Handler[User, Response, Request, Response]`, which is compatible with the `DigestAuthHandlerAspect` that requires only `User` in its environment. Now we can apply the `DigestAuthHandlerAspect` to the inner handler and chain it with the outer handler that extracts the `UserService` from the environment.

## Client Implementation for Testing Digest Authentication

As we saw, the digest authentication process involves at least two requests: the first request to get the challenge and the second request to send the response (response to the challenge). However, this is not always the case. If the client already has a valid `nonce`, it can use it for subsequent requests without needing to get a new challenge until the nonce expires.

To create a client that reuses `nonce` between calls, we need a client that manages state. First, let's define the interface of the client:

```scala
trait DigestAuthClient {
  def makeRequest(request: Request): ZIO[Any, DigestAuthError, Response]
}
```

We need the `Client`, `DigestService`, and `NonceService` to implement the `DigestAuthClient`. We also need to maintain two states: 
- one for the digest challenge parameters (`Ref[Option[DigestChallenge]]`). We made it optional because the first time the client makes a request, it won't have any cached digest challenge parameters.
- another for maintaining the nonce count (`Ref[NC]`).

To simplify the implementation, we pass the username and password when constructing the client. The client will use these credentials to compute the digest response when making requests:

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

The only method we need to implement is `makeRequest`, which takes a `Request`, performs an authenticated request, and returns the `Response`. This method implements the core digest authentication flow by handling both initial authentication challenges and request retries. 

The method first attempts to authenticate the incoming request using any cached digest challenge parameters and then sends it to the server. If the response returns successfully (non-401 status), it's returned immediately. However, if the server responds with **401 Unauthorized**, the method enters the standard digest authentication challenge-response flow: it parses the WWW-Authenticate header to extract and cache the new digest challenge parameters, then computes the response and re-authenticates the original request with a response digest.

Here is the implementation of the `authenticate` helper method. It takes a `Request`, uses the cached digest to compute the response, and adds the proper digest header to the given request:

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

We also need another helper method that chooses between two security levels based on request characteristics and server capabilities. It takes an HTTP request and a set of supported QoP values as parameters, then returns `AuthInt` (authentication with integrity protection) if the request has a non-empty body and the server supports it; otherwise, it defaults to `Auth` (authentication only):

```scala
def selectQop(request: Request, supportedQop: Set[QualityOfProtection]): QualityOfProtection =
  if (!request.body.isEmpty && supportedQop.contains(QualityOfProtection.AuthInt))
    QualityOfProtection.AuthInt
  else
    QualityOfProtection.Auth
```

To extract and store the digest challenge from unauthorized response calls, here is another helper method called `handleUnauthorized`:

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

To run this program, you need to have the `DigestAuthClient` and `DigestService` in your ZIO environment:

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

**Third Call:** The `PUT /profile/email` request attempts to update the user's email address. The client computes the digest response using the cached challenge and increments the nonce count to `00000003`. However, since this endpoint requires `auth-int` (authentication with integrity protection) quality of protection to verify that the request body hasn't been tampered with, the server rejects this request with `401 Unauthorized` because the request's response header is computed using the wrong `qop` parameter, namely `auth`. The client receives a new digest challenge specifying `auth-int` QOP, caches this updated challenge, and retries the request. For the new challenge, the nonce count starts from `00000001`, and the client successfully authenticates using `auth-int`, which includes the hash of the request body in the digest calculation.

**Fourth Call:** The final `GET /profile/me` request retrieves the updated profile information. The client uses the most recently cached challenge (from the PUT request), incrementing the nonce count to `00000002`. Since this is a GET request with an empty body, the client uses standard `auth` quality of protection rather than `auth-int`. The server responds with the updated profile, confirming that the email address change was successful.

This design provides transparent handling of digest authentication, allowing clients to make requests normally without manually managing the challenge-response handshake. The method efficiently addresses both cold-start scenarios, where the first request triggers a challenge, and challenge-refresh situations, where server nonces expire, while maintaining authentication state between requests for optimal performance.

## Web Client Demo

Similar to the client we wrote in the previous section, we can write a web client. The overall structure of the web client is similar, but since the implementation details go beyond the scope of this guide, we will not cover them here. However, by running the `DigestAuthenticationServer`, it will serve the `digest-auth-client.html` at `http://localhost:8080`, which is a simple web client that allows you to demo and test the digest authentication flow in your browser. You can use it to send requests to the server and see how digest authentication works in practice.

## Conclusion

In this guide, we demonstrated an implementation of Digest Authentication using ZIO HTTP, from understanding the underlying cryptographic protocols to building a nearly production-ready middleware and client implementations. Through our exploration, we've covered how to use the essential capabilities of ZIO and ZIO HTTP to create a secure, modular, and maintainable authentication system:

1. **ZIO HTTP Middleware/HandlerAspect** - We created a reusable authentication middleware that can be applied to any route, allowing for easy integration of Digest Authentication into existing applications.
2. **Mutable References (`Ref`)** - We utilized mutable references to manage stateful components like nonce tracking and cached challenges, demonstrating how ZIO's functional programming model can handle stateful operations safely and efficiently.
3. **ZIO Service Pattern** - We designed the entire authentication system using ZIO services, promoting separation of concerns and modularity. Each service (NonceService, DigestService, DigestAuthService, UserService) encapsulates specific functionality, making the codebase easier to maintain and test, applying the principles of object-oriented design to functional programming.

Our implementation successfully addresses the core security requirements of modern web applications:

**1. Password Protection**: By never transmitting passwords across the network—even in hashed form—our implementation ensures that user credentials remain secure. The challenge-response mechanism proves credential knowledge without exposing sensitive data.

**2. Replay Attack Prevention**: Through the combination of temporal nonces with HMAC-based validation and incrementing nonce counts, we've built a robust defense against replay attacks. The stateful tracking of used nonce-count values ensures that captured authentication tokens cannot be reused maliciously.

**3. Request Integrity**: By incorporating HTTP methods, URIs, and optionally request bodies into the digest calculation, our implementation prevents request tampering and ensures that authentication tokens cannot be reused for unintended endpoints.

While Digest Authentication is not the most modern authentication mechanism, it remains relevant for legacy systems and specific use cases where stateless authentication is required. However, it is essential to understand its limitations and consider more modern alternatives for new projects, such as OAuth 2.0, OpenID Connect, or JSON Web Tokens (JWT) with refresh tokens, which we will explore in future guides.
