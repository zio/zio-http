---
id: self-contained-jwt-token-authentication
title: "Securing Your APIs: Self-contained JWT Token Authentication"
sidebar_label: "Self-contained JWT Token Authentication"
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

When permissions must change instantly, session-based systems offer better control. You modify server-side data, and changes take effect immediately. While with JWTs, you're waiting for expiration or building complex revocation systems.

## Anatomy of a JWT (Header, Payload, Signature)

If you open any JWT in a decoder, and you'll see something that looks like this: three chunks of gibberish separated by dots. Something like `eyJhbGciOiJIUzI1NiIs...`. But this has a beautiful structure underneath.

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
  "roles": ["admin", "user"],
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

But here's what catches developers off guard: the server doesn't store these tokens anywhere. There's no database table of valid tokens, no session store to query. The token's validity comes entirely from its signature. If the signature checks out and the token hasn't expired, it's valid—period. This statelessness is both JWT's greatest strength and also its most significant limitation.

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
6. 
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

We need a service to issue and verify JWT tokens. Let's define an interface for a such service:

```scala
trait JwtTokenService {
  def issue(username: String): UIO[String]
  def verify(token: String): Task[String]
}
```

The `issue` method takes a `username` and generate a JWT token. It uses the standard `sub` claim to store the username. The `verify` method takes a JWT token and decodes it, returning the username if the token is valid, or failing if the token is invalid or expired.