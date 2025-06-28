---
id: implementing-mutual-tls
title: Implementing Mutual TLS (mTLS) 
---

## Introduction

Mutual TLS (mTLS) extends standard TLS by requiring both the client and server to present and verify certificates. This creates bidirectional authentication, ensuring both parties are who they claim to be. mTLS is crucial for zero-trust architectures, API security, and microservices communication.

This article demonstrates implementing mTLS with practical examples in Scala using the ZIO HTTP library.

## Understanding Mutual TLS

### Standard TLS vs. Mutual TLS

**Standard TLS:**
- Server presents certificate to client
- Client verifies server's identity
- Client remains anonymous

**Mutual TLS:**
- Server presents certificate to client
- Client verifies server's identity
- Client presents certificate to server
- Server verifies client's identity

```
Standard TLS:                    Mutual TLS:
┌────────┐      ┌────────┐      ┌────────┐           ┌────────┐
│ Client │      │ Server │      │ Client │           │ Server │
└───┬────┘      └────┬───┘      └───┬────┘           └────┬───┘
    │   Who are you? │              │   Who are you?      │
    │<───────────────┤              │<────────────────────┤
    │   Certificate  │              │   Certificate       │
    │                │              │                     │
    │                │              │ Who are YOU?        │
    │                │              ├────────────────────>│
    │                │              │   Certificate       │
    │                │              │                     │
    └─ Authenticated ┘              └─ Both Authenticated ┘
```

### Use Cases for mTLS

1. **Microservices Communication**: Service-to-service authentication
2. **API Security**: Strong client authentication for sensitive APIs
3. **IoT Devices**: Device authentication at scale
4. **Zero-Trust Networks**: Every connection requires authentication
5. **Financial Services**: High-security transaction systems
6. **Healthcare**: HIPAA-compliant data exchange

## Creating Certificates for mTLS

### Step 1: Create Certificate Authority

```bash
# Create Root CA (same for both client and server trust)
openssl genrsa -out rootCA.key 4096

openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 3650 \
  -out rootCA.crt \
  -subj "/C=US/ST=State/L=City/O=MyOrg/OU=Security/CN=Root CA"
```

### Step 2: Create Server Certificate

```bash
# Generate server private key
openssl genrsa -out server.key 2048

# Create server CSR
openssl req -new -key server.key -out server.csr \
  -subj "/C=US/ST=State/L=City/O=MyOrg/OU=IT/CN=localhost"

# Sign server certificate
openssl x509 -req -in server.csr -CA rootCA.crt -CAkey rootCA.key \
  -CAcreateserial -out server.crt -days 365 -sha256 \
  -extfile <(echo "
    basicConstraints=CA:FALSE
    keyUsage=digitalSignature,keyEncipherment
    extendedKeyUsage=serverAuth
    subjectAltName=DNS:localhost,IP:127.0.0.1
  ")
```

### Step 3: Create Client Certificate

```bash
# Generate client private key
openssl genrsa -out client.key 2048

# Create client CSR
openssl req -new -key client.key -out client.csr \
  -subj "/C=US/ST=State/L=City/O=MyOrg/OU=Engineering/CN=client-service"

# Sign client certificate
openssl x509 -req -in client.csr -CA rootCA.crt -CAkey rootCA.key \
  -CAcreateserial -out client.crt -days 365 -sha256 \
  -extfile <(echo "
    basicConstraints=CA:FALSE
    keyUsage=digitalSignature,keyEncipherment
    extendedKeyUsage=clientAuth
  ")
```

### Step 4: Create Keystores and Truststores

```bash
# Server keystore (server's private key and certificate)
openssl pkcs12 -export -out server-keystore.p12 \
  -inkey server.key -in server.crt \
  -name "server" -password pass:serverkeypass

# Server truststore (to verify client certificates)
keytool -import -alias rootca -file rootCA.crt \
  -keystore server-truststore.p12 -storetype PKCS12 \
  -storepass servertrustpass -noprompt

# Client keystore (client's private key and certificate)  
openssl pkcs12 -export -out client-keystore.p12 \
  -inkey client.key -in client.crt \
  -name "client" -password pass:clientkeypass

# Client truststore (to verify server certificates)
keytool -import -alias rootca -file rootCA.crt \
  -keystore client-truststore.p12 -storetype PKCS12 \
  -storepass clienttrustpass -noprompt
```

## Implementation Example

### Project Structure

```
src/main/
├── scala/example/ssl/mtls/
│   ├── ServerApp.scala
│   └── ClientApp.scala
└── resources/certs/mtls/
    ├── server-keystore.p12    # Server's identity
    ├── server-truststore.p12  # Server's trust (for client certs)
    ├── client-keystore.p12    # Client's identity
    ├── client-truststore.p12  # Client's trust (for server certs)
    └── rootCA.crt             # Root CA certificate
```

### Server Implementation

```scala
package example.ssl.mtls

import zio.Config.Secret
import zio._
import zio.http.SSLConfig.Data.FromJavaxNetSsl
import zio.http._

object ServerApp extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "hello" ->
      handler { (req: Request) =>
        ZIO.debug(req.remoteCertificate) *> ZIO.succeed(
          Response.text("Hello from TLS server! Connection secured!"),
        )
      },
  )

  private val sslConfig =
    SSLConfig.fromJavaxNetSsl(
      data = SSLConfig.Data.FromJavaxNetSsl(
        keyManagerSource = FromJavaxNetSsl.Resource("certs/mtls/server-keystore.p12"),
        keyManagerPassword = Some(Secret("serverkeypass")),
        trustManagerKeyStore = Some(
          SSLConfig.Data.TrustManagerKeyStore(
            trustManagerSource = FromJavaxNetSsl.Resource("certs/mtls/server-truststore.p12"),
            trustManagerPassword = Some(Secret("servertrustpass")),
          ),
        ),
      ),
      includeClientCert = false,
      clientAuth = Some(ClientAuth.Required),
    )

  private val serverConfig =
    ZLayer.succeed {
      Server.Config.default
        .port(8443)
        .ssl(sslConfig)
    }

  override val run =
    Server.serve(routes).provide(serverConfig, Server.live)

}
```

**Key Points:**
- `ClientAuth.Required`: Forces clients to present certificates
- Server has both keystore (its identity) and truststore (to verify clients)
- The handler can access client certificate via `req.remoteCertificate`

### Client Implementation

```scala
package example.ssl.mtls

import zio.Config.Secret
import zio._
import zio.http._
import zio.http.netty.NettyConfig

object ClientApp extends ZIOAppDefault {
  val app: ZIO[Client, Throwable, Unit] =
    for {
      _            <- Console.printLine("Making secure HTTPS requests...")
      textResponse <- Client.batched(
        Request.get("https://localhost:8443/hello"),
      )
      textBody     <- textResponse.body.asString
      _            <- Console.printLine(s"Text response: $textBody")
    } yield ()

  private val config =
    ZClient.Config.default.ssl(
      ClientSSLConfig.FromJavaxNetSsl(
        keyManagerSource = ClientSSLConfig.FromJavaxNetSsl.Resource("certs/mtls/client-keystore.p12"),
        keyManagerPassword = Some(Secret("clientkeypass")),
        trustManagerSource = ClientSSLConfig.FromJavaxNetSsl.Resource("certs/mtls/client-truststore.p12"),
        trustManagerPassword = Some(Secret("clienttrustpass")),
      ),
    )

  override val run =
    app.provide(
      ZLayer.succeed(config),
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
      ZClient.live,
    )

}
```

**Key Points:**
- Client also has both keystore (its identity) and truststore
- Client certificate is automatically sent during TLS handshake
- No additional code needed beyond SSL configuration

## How mTLS Works

### The mTLS Handshake

```
Client                                          Server
  |                                               |
  |-------------- ClientHello ------------------->|
  |                                               |
  |<------------- ServerHello --------------------|
  |<------------- Certificate --------------------|  ← Server cert
  |<------------- CertificateRequest -------------|  ← Request for client cert
  |<------------- ServerHelloDone ----------------|
  |                                               |
  | [Verify server certificate]                   |
  |                                               |
  |-------------- Certificate ------------------->|  ← Client cert
  |-------------- ClientKeyExchange ------------->|
  |-------------- CertificateVerify ------------->|  ← Proof of private key
  |-------------- ChangeCipherSpec -------------->|
  |-------------- Finished ---------------------->|
  |                                               |
  |              [Verify client certificate]      |
  |                                               |
  |<------------- ChangeCipherSpec ---------------|
  |<------------- Finished -----------------------|
  |                                               |
  |========== Encrypted Application Data =========|
```

### Client Authentication Types

```scala
// Server-side client authentication options
sealed trait ClientAuth
object ClientAuth {
  case object None extends ClientAuth      // Standard TLS (no client cert)
  case object Want extends ClientAuth      // Request but don't require
  case object Required extends ClientAuth  // Require client certificate
}
```

## Advanced mTLS Features

### 1. Client Certificate Information

```scala
// Enhanced server handler that extracts client information
val routes: Routes[Any, Response] = Routes(
  Method.GET / "hello" ->
    handler { (req: Request) =>
      val clientInfo = req.remoteCertificate.map { cert =>
        val x509Cert = cert.asInstanceOf[X509Certificate]
        s"""
          |Client Certificate Info:
          |  Subject: ${x509Cert.getSubjectDN}
          |  Issuer: ${x509Cert.getIssuerDN}
          |  Serial: ${x509Cert.getSerialNumber}
          |  Valid: ${x509Cert.getNotBefore} to ${x509Cert.getNotAfter}
        """.stripMargin
      }.getOrElse("No client certificate provided")
      
      Response.text(s"Hello from mTLS server!\n$clientInfo")
    }
)
```

### 2. Certificate-Based Authorization

```scala
// Authorize based on certificate attributes
def authorizeClient(cert: X509Certificate): Boolean = {
  val subject = cert.getSubjectDN.getName
  val allowedOUs = Set("Engineering", "DevOps", "Security")
  
  // Extract OU from subject DN
  val ouPattern = "OU=([^,]+)".r
  ouPattern.findFirstMatchIn(subject)
    .map(_.group(1))
    .exists(allowedOUs.contains)
}

val secureRoutes = Routes(
  Method.GET / "admin" ->
    handler { (req: Request) =>
      req.remoteCertificate match {
        case Some(cert) if authorizeClient(cert.asInstanceOf[X509Certificate]) =>
          Response.text("Welcome to admin area!")
        case Some(_) =>
          Response.forbidden("Your certificate is not authorized for this resource")
        case None =>
          Response.unauthorized("Client certificate required")
      }
    }
)
```

### 3. Certificate Revocation Checking

```scala
// Implement CRL checking
def checkCertificateRevocation(cert: X509Certificate): Task[Boolean] = {
  ZIO.attempt {
    // Load CRL
    val crlUrl = cert.getExtensionValue("2.5.29.31") // CRL Distribution Points
    if (crlUrl != null) {
      val url = new URL(extractCrlUrl(crlUrl))
      val crlStream = url.openStream()
      val cf = CertificateFactory.getInstance("X.509")
      val crl = cf.generateCRL(crlStream).asInstanceOf[X509CRL]
      
      // Check if certificate is revoked
      !crl.isRevoked(cert)
    } else {
      true // No CRL, assume valid
    }
  }
}
```

### 4. Dynamic Certificate Selection

```scala
// Client-side: Select certificate based on server
class DynamicKeyManager extends X509ExtendedKeyManager {
  override def chooseClientAlias(
    keyType: Array[String], 
    issuers: Array[Principal], 
    socket: Socket
  ): String = {
    val serverHost = socket.getInetAddress.getHostName
    serverHost match {
      case "api.production.com" => "production-client-cert"
      case "api.staging.com"    => "staging-client-cert"
      case _                    => "default-client-cert"
    }
  }
  // ... other required methods
}
```

## Production Best Practices

### 1. Certificate Lifecycle Management

```yaml
# Example certificate rotation strategy
certificates:
  rotation:
    frequency: 30d
    overlap: 7d
    strategy:
      - Generate new certificate
      - Deploy to canary instances
      - Monitor for 24h
      - Roll out to all instances
      - Keep old cert active for overlap period
      - Remove old certificate
```

### 2. Monitoring and Alerting

```scala
// Certificate expiration monitoring
def monitorCertificateExpiration(keystorePath: String): Task[Unit] = {
  for {
    keyStore <- ZIO.attempt {
      val ks = KeyStore.getInstance("PKCS12")
      ks.load(new FileInputStream(keystorePath), "password".toCharArray)
      ks
    }
    _ <- ZIO.foreach(keyStore.aliases.asScala) { alias =>
      val cert = keyStore.getCertificate(alias).asInstanceOf[X509Certificate]
      val daysUntilExpiry = 
        (cert.getNotAfter.getTime - System.currentTimeMillis) / (1000 * 60 * 60 * 24)
      
      if (daysUntilExpiry < 30) {
        ZIO.logWarning(s"Certificate $alias expires in $daysUntilExpiry days")
      } else {
        ZIO.logInfo(s"Certificate $alias valid for $daysUntilExpiry days")
      }
    }
  } yield ()
}
```

### 3. High Availability Considerations

```scala
// Load balancer health check endpoint that verifies mTLS
val healthRoutes = Routes(
  Method.GET / "health" / "mtls" ->
    handler { (req: Request) =>
      req.remoteCertificate match {
        case Some(cert) =>
          val x509 = cert.asInstanceOf[X509Certificate]
          val now = new Date()
          if (x509.getNotAfter.after(now) && x509.getNotBefore.before(now)) {
            Response.ok
          } else {
            Response.status(Status.ServiceUnavailable)
          }
        case None =>
          Response.status(Status.ServiceUnavailable)
      }
    }
)
```

## Security Considerations

### 1. Certificate Pinning

```scala
// Pin specific client certificates
val pinnedCertificates = Set(
  "SHA256:ABCD1234...", // Production client cert fingerprint
  "SHA256:EFGH5678..."  // Backup client cert fingerprint
)

def validatePinnedCertificate(cert: X509Certificate): Boolean = {
  val md = MessageDigest.getInstance("SHA-256")
  val fingerprint = md.digest(cert.getEncoded)
    .map("%02X".format(_)).mkString
  
  pinnedCertificates.contains(s"SHA256:$fingerprint")
}
```

### 2. Perfect Forward Secrecy

```scala
// Configure cipher suites for PFS
private val sslConfig = SSLConfig.fromJavaxNetSsl(
  // ... other config ...
  cipherSuites = Some(List(
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"
  ))
)
```

### 3. Rate Limiting by Certificate

```scala
// Rate limit based on client certificate
val rateLimiter = new ConcurrentHashMap[String, RateLimiter]()

def getRateLimiter(cert: X509Certificate): RateLimiter = {
  val clientId = cert.getSubjectDN.getName
  rateLimiter.computeIfAbsent(clientId, _ => 
    RateLimiter.create(100.0) // 100 requests per second
  )
}
```

## Common Issues and Solutions

### Issue 1: Client Certificate Not Sent

**Error:**
```
javax.net.ssl.SSLHandshakeException: null cert chain
```

**Solution:**
- Ensure client keystore contains both private key and certificate
- Verify ClientAuth is set to Required or Want on server
- Check client SSL configuration includes keyManager

### Issue 2: Certificate Verification Failed

**Error:**
```
javax.net.ssl.SSLHandshakeException: certificate_unknown
```

**Solution:**
```bash
# Verify certificate chain
openssl verify -CAfile rootCA.crt client.crt

# Check certificate purpose
openssl x509 -in client.crt -text | grep -A1 "Extended Key Usage"
# Should include: TLS Web Client Authentication
```

### Issue 3: Hostname Verification in mTLS

**Note:** Client certificates typically don't have hostname constraints

```scala
// Disable hostname verification for client certificates
val sslConfig = ClientSSLConfig.FromJavaxNetSsl(
  // ... other config ...
  hostnameVerifier = Some((hostname, session) => true)
)
```

## Testing mTLS

### Using curl

```bash
# Test mTLS with curl
curl --cert client.crt --key client.key \
     --cacert rootCA.crt \
     https://localhost:8443/hello

# With PKCS12 client certificate
curl --cert-type P12 --cert client.p12:clientkeypass \
     --cacert rootCA.crt \
     https://localhost:8443/hello
```

### Using OpenSSL

```bash
# Test mTLS connection
openssl s_client -connect localhost:8443 \
  -cert client.crt -key client.key \
  -CAfile rootCA.crt \
  -showcerts
```

## mTLS in Different Environments

### Kubernetes

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-ssl-cert: "arn:aws:acm:..."
    service.beta.kubernetes.io/aws-load-balancer-backend-protocol: "ssl"
    service.beta.kubernetes.io/aws-load-balancer-ssl-negotiation-policy: "ELBSecurityPolicy-TLS-1-2"
spec:
  type: LoadBalancer
  ports:
  - port: 443
    targetPort: 8443
    protocol: TCP
```

### Service Mesh (Istio)

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
spec:
  mtls:
    mode: STRICT
---
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: my-service
spec:
  host: my-service
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL
```

## Conclusion

Mutual TLS provides the strongest form of authentication in TLS, ensuring both parties verify each other's identity through certificates. It's essential for high-security environments, zero-trust architectures, and service-to-service communication.

Key takeaways:
- mTLS requires both client and server to present certificates
- Each party needs both a keystore (identity) and truststore (trust)
- Client authentication can be optional (Want) or mandatory (Required)
- Certificate attributes enable fine-grained authorization
- Proper certificate lifecycle management is crucial
- mTLS is the foundation for zero-trust security models

With mTLS, you can build highly secure systems where every connection is authenticated, authorized, and encrypted, providing defense in depth for your critical applications.
