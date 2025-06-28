---
id: implementing-tls-using-self-signed-server-certificates
title: Implementing TLS Using Self-Signed Server Certificates
---

## Introduction

Self-signed certificates are TLS/SSL certificates that are signed by the same entity that creates them, rather than by a trusted Certificate Authority (CA). While not suitable for production public-facing applications, they are invaluable for development, testing, and internal services.

This article demonstrates how to implement TLS using ZIO HTTP using self-signed certificates.

## Understanding Self-Signed Certificates

### What is a Self-Signed Certificate?

A self-signed certificate is a digital certificate where the organization or person creating it is also the one validating it, rather than having an independent Certificate Authority (CA) like DigiCert or Let's Encrypt verify their identity first.

```
┌─────────────────────────┐
│   Self-Signed Cert      │
├─────────────────────────┤
│ Subject: CN=localhost   │
│ Issuer:  CN=localhost   │ ← The issuer is same as subject!
│ Signed by: Own key      │
└─────────────────────────┘
```

This means the certificate's issuer and subject are the same entity - essentially, they're vouching for themselves. Unlike regular certificates that come with built-in trust because they're backed by recognized authorities, self-signed certificates don't have this third-party validation. So, when users encounter a self-signed certificate, their browsers or applications may typically show security warnings because there's no established chain of trust.

To use self-signed certificates properly, administrators must manually add them to each client's trusted certificate store, telling the system to accept and trust that specific certificate. While this manual process can be time-consuming for large organizations, self-signed certificates are popular for internal company networks, testing environments, and situations where the cost and complexity of getting CA-issued certificates isn't justified by the security requirements.

### When to Use Self-Signed Certificates

Self-signed certificates are well-suited for specific scenarios where public trust isn't required and security can be managed through controlled environments. They work effectively in development and testing environments where developers need SSL/TLS encryption for local servers and applications without the overhead of obtaining CA-issued certificates. 

Internal microservices communication within private networks is another use case, as services can trust each other through pre-configured certificate stores without needing external validation. But please note this configuration has drawbacks, such as the need for manual trust management and revocation processes, which can become cumbersome as the number of services grows.

They're also valuable for proof of concepts, demos, and experimental projects where quick setup is more important than established trust chains. 

However, self-signed certificates should never be used for public-facing production websites, e-commerce applications, or any service that requires users to trust the connection without manual certificate installation. In these public scenarios, the security warnings and trust barriers they create can damage user confidence, reduce conversion rates, and potentially expose users to man-in-the-middle attacks if they're trained to ignore certificate warnings. 

The general rule is that self-signed certificates are appropriate for closed, controlled environments where administrators can manage trust relationships directly, but they're unsuitable for any application where unknown users need to establish trust automatically.

## Generating Self-Signed Certificates

### Using OpenSSL

```bash
# Generate private key
openssl genrsa -out server-key.pem 2048

# Generate self-signed certificate
openssl req -new -x509 -key server-key.pem -out server-cert.pem -days 365 \
  -subj "/C=US/ST=State/L=City/O=MyCompany/OU=IT/CN=localhost"

# Convert to PKCS12 format for Java/Scala
openssl pkcs12 -export -out server.p12 -inkey server-key.pem -in server-cert.pem \
  -name "server" -password pass:changeit
```

### Using Java Keytool

```bash
# Generate self-signed certificate directly in PKCS12 keystore
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 \
  -keystore server.p12 -storetype PKCS12 -storepass changeit \
  -validity 365 -dname "CN=localhost,OU=IT,O=MyCompany,L=City,ST=State,C=US" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"
```

### Creating the Client Trust Store

Since the certificate is self-signed, clients need to explicitly trust it:

```bash
# Export certificate from keystore
keytool -export -alias server -keystore server.p12 -storepass changeit \
  -file server-cert.der

# Import into client truststore
keytool -import -alias server -file server-cert.der \
  -keystore truststore.p12 -storetype PKCS12 -storepass trustpass \
  -noprompt
```

## Implementation Example

### Project Structure

```
src/main/
├── scala/example/ssl/tls/selfsigned/
│   ├── ServerApp.scala
│   └── ClientApp.scala
└── resources/certs/tls/self-signed/
    ├── server.p12          # Server keystore with private key and certificate
    ├── truststore.p12      # Client truststore with server certificate
    ├── server-cert.pem     # Optional: PEM format certificate
    └── server-key.pem      # Optional: PEM format private key
```

### Server Implementation

```scala
package example.ssl.tls.selfsigned

import zio.Config.Secret
import zio._
import zio.http._

object ServerApp extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "hello" ->
      handler(Response.text("Hello from self-signed TLS server! Connection secured!")),
  )

  // Option 1: Using PKCS12 keystore (recommended)
  private val sslConfig =
    SSLConfig.fromJavaxNetSslKeyStoreResource(
      keyManagerResource = "certs/tls/self-signed/server.p12",
      keyManagerPassword = Some(Secret("changeit")),
    )

  // Option 2: Using PEM files directly
  // Note: This might require the PEM files to be in the correct format
  private val sslConfigPem =
    SSLConfig.fromResource(
      certPath = "certs/tls/self-signed/server-cert.pem",
      keyPath = "certs/tls/self-signed/server-key.pem",
    )

  private val serverConfig =
    ZLayer.succeed {
      Server.Config.default
        .port(8443)
        .ssl(sslConfig) // Using PKCS12 keystore
    }

  val run =
    Console.printLine("Self-Signed TLS Server starting on https://localhost:8443/") *>
      Server.serve(routes).provide(serverConfig, Server.live)
}
```

**Key Points:**
- The server loads its private key and certificate from a PKCS12 keystore
- The `SSLConfig` handles all TLS configuration
- The server listens on port 8443 (standard HTTPS alternative port)
- Two options shown: PKCS12 (recommended) and PEM files

### Client Implementation

```scala
package example.ssl.tls.selfsigned

import zio._
import zio.http._
import zio.http.netty.NettyConfig

object ClientApp extends ZIOAppDefault {

  val app: ZIO[Client, Throwable, Unit] =
    for {
      _        <- Console.printLine("Making secure HTTPS request to self-signed server...")
      response <- Client.batched(Request.get("https://localhost:8443/hello"))
      body     <- response.body.asString
      _        <- Console.printLine(s"Response status: ${response.status}")
      _        <- Console.printLine(s"Response body: $body")
    } yield ()

  override val run =
    app.provide(
      ZLayer.succeed {
        ZClient.Config.default.ssl(
          ClientSSLConfig.FromTrustStoreResource(
            trustStorePath = "certs/tls/self-signed/truststore.p12",
            trustStorePassword = "trustpass",
          ),
        )
      },
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
      ZClient.live,
    )
}
```

**Key Points:**
- Client must have the server's certificate in its truststore
- Without the truststore configuration, the client would reject the self-signed certificate

## How It Works

### The TLS Handshake with Self-Signed Certificates

```
Client                                          Server
  |                                               |
  |-------------- ClientHello ------------------->|
  |                                               |
  |<------------- ServerHello --------------------|
  |<------------- Certificate --------------------|  ← Self-signed Server Cert
  |<------------- ServerHelloDone ----------------|
  |                                               |
  | [Verify certificate against truststore]       |
  | ✓ Found matching certificate in truststore    |
  |                                               |
  |-------------- ClientKeyExchange ------------->|
  |-------------- ChangeCipherSpec -------------->|
  |-------------- Finished ---------------------->|
  |                                               |
  |<------------- ChangeCipherSpec ---------------|
  |<------------- Finished -----------------------|
  |                                               |
  |========== Encrypted Application Data -========|
```

### Trust Validation Process

1. **Server sends certificate**: The self-signed certificate is sent to the client
2. **Client checks truststore**: The client looks for this exact certificate in its truststore
3. **Match found**: Since we imported the certificate, validation succeeds
4. **Connection established**: Encrypted communication begins

## Running the Example

### 1. Start the Server

```bash
sbt "runMain example.ssl.tls.selfsigned.ServerApp"
```

Output:
```
Self-Signed TLS Server starting on https://localhost:8443/
```

### 2. Run the Client

```bash
sbt "runMain example.ssl.tls.selfsigned.ClientApp"
```

Output:
```
Making secure HTTPS request to self-signed server...
Response status: Ok
Response body: Hello from self-signed TLS server! Connection secured!
```

### 3. Testing with curl

```bash
# This will fail (certificate not trusted)
curl https://localhost:8443/hello

# This will work (skip certificate verification)
curl -k https://localhost:8443/hello

# This will work (specify the certificate)
curl --cacert server-cert.pem https://localhost:8443/hello
```

## Common Issues and Solutions

### Issue 1: Certificate Not Trusted

**Error:**
```
javax.net.ssl.SSLHandshakeException: PKIX path building failed
```

**Solution:**
Ensure the server certificate is properly imported into the client's truststore:
```bash
keytool -list -keystore truststore.p12 -storepass trustpass
```

### Issue 2: Hostname Verification Failed

**Error:**
```
javax.net.ssl.SSLPeerUnverifiedException: Certificate for <localhost> doesn't match
```

**Solution:**
Ensure the certificate's CN or SAN includes the hostname you're connecting to:
```bash
# Check certificate details
keytool -printcert -file server-cert.der | grep -E "CN=|Subject Alternative"
```

### Issue 3: Certificate Expired

**Error:**
```
java.security.cert.CertificateExpiredException: NotAfter: ...
```

**Solution:**
Generate a new certificate with a longer validity period:
```bash
keytool -genkeypair -alias server -validity 3650 ... # 10 years
```

## Security Considerations

### Advantages of Self-Signed Certificates

1. **Complete Control**: You control the entire certificate lifecycle
2. **No External Dependencies**: No need for CA infrastructure
3. **Free**: No costs involved
4. **Quick Setup**: Fast to generate and deploy
5. **Privacy**: No information shared with external CAs

### Limitations and Risks

1. **No Third-Party Validation**: Anyone can create a certificate claiming to be your server
2. **Manual Trust Management**: Each client must be configured to trust the certificate
3. **No Revocation**: Cannot revoke compromised certificates through standard mechanisms
4. **Browser Warnings**: Web browsers will show security warnings
5. **Scalability Issues**: Difficult to manage trust relationships at scale

### Best Practices

1. **Use Only in Development**: Never use self-signed certificates for public production services
2. **Secure Private Keys**: Protect private keys with appropriate file permissions
3. **Use Strong Keys**: Minimum 2048-bit RSA or 256-bit ECDSA
4. **Include Proper Extensions**: Add Subject Alternative Names (SAN) for flexibility
5. **Document Trust Requirements**: Clearly document which certificates clients need to trust
6. **Regular Rotation**: Even in development, rotate certificates periodically
7. **Use Descriptive Names**: Make certificates identifiable through their CN/OU fields

## Transitioning to Production

When moving from development to production, replace self-signed certificates with:

1. **CA-Signed Certificates**: For public-facing services
2. **Internal CA**: For private networks and microservices
3. **Let's Encrypt**: For free, automated certificates
4. **Managed Certificate Services**: Cloud provider solutions (AWS ACM, Azure Key Vault)

## Conclusion

Self-signed certificates provide a simple way to enable TLS encryption during development and testing. While they lack third-party validation, they offer the same encryption strength as CA-signed certificates. The key is understanding their limitations and using them appropriately.

Key takeaways:
- Self-signed certificates are perfect for development and testing
- Clients must explicitly trust self-signed certificates
- The encryption is just as strong as CA-signed certificates
- Never use them for public-facing production services
- Always have a plan to transition to proper certificates for production

In the next article, we'll explore using CA-signed certificates, which provide third-party validation and are suitable for production use.