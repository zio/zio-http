---
id: tls-with-intermediate-ca-signed-server-certificates
title: Implementing TLS with Intermediate CA-signed Server Certificates
---

## Introduction

In production environments, server certificates are rarely signed directly by root Certificate Authorities. Instead, they use intermediate CAs to create a certificate chain. This approach provides better security, flexibility, and follows industry best practices.

This article demonstrates implementing TLS with certificate chains using intermediate CAs, with practical examples in Scala using the ZIO HTTP library.

## Understanding Certificate Chains

### Why Intermediate CAs?

Root CAs are extremely valuable and must be protected at all costs. If a root CA's private key is compromised, it affects every certificate it has ever signed. To minimize risk:

1. **Root CAs are kept offline**: Private keys stored in secure, air-gapped systems
2. **Intermediate CAs handle daily operations**: These can be revoked if compromised
3. **Isolation of risk**: Compromise of an intermediate CA has limited impact

### Certificate Chain Structure

```
┌──────────────────────────┐
│     Root CA              │ Self-signed, in trust stores
│  CN=GlobalTrust Root     │ Validity: 20-30 years
└───────────┬──────────────┘
            │ Signs
            ▼
┌──────────────────────────┐
│   Intermediate CA        │ Signed by Root CA
│  CN=GlobalTrust SSL CA   │ Validity: 5-10 years
└───────────┬──────────────┘
            │ Signs
            ▼
┌──────────────────────────┐
│   Server Certificate     │ Signed by Intermediate
│  CN=www.example.com      │ Validity: 90 days - 2 years
└──────────────────────────┘
```

### Real-World Example

Let's examine a real certificate chain from a major website:

```bash
# Check Google's certificate chain
openssl s_client -connect google.com:443 -showcerts < /dev/null

# You'll see something like:
# 0: CN=*.google.com (server certificate)
# 1: CN=GTS CA 1C3, O=Google Trust Services (intermediate)
# 2: CN=GTS Root R1, O=Google Trust Services (root - not sent)
```

## Creating a Certificate Chain

### Step 1: Create Root CA

```bash
# Generate Root CA private key (keep this extremely secure!)
openssl genrsa -out rootCA.key 4096

# Generate Root CA certificate
openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 7300 \
  -out rootCA.crt \
  -subj "/C=US/ST=State/L=City/O=RootCA/OU=Security/CN=Root CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"
```

### Step 2: Create Intermediate CA

```bash
# Generate Intermediate CA private key
openssl genrsa -out intermediateCA.key 2048

# Create Intermediate CA CSR
openssl req -new -key intermediateCA.key -out intermediateCA.csr \
  -subj "/C=US/ST=State/L=City/O=IntermediateCA/OU=Security/CN=Intermediate CA"

# Create extensions file for Intermediate CA
cat > intermediate_ext.cnf <<EOF
basicConstraints=critical,CA:TRUE,pathlen:0
keyUsage=critical,keyCertSign,cRLSign
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
EOF

# Sign Intermediate CA certificate with Root CA
openssl x509 -req -in intermediateCA.csr -CA rootCA.crt -CAkey rootCA.key \
  -CAcreateserial -out intermediateCA.crt -days 1825 -sha256 \
  -extfile intermediate_ext.cnf
```

### Step 3: Create Server Certificate

```bash
# Generate server private key
openssl genrsa -out server.key 2048

# Create server CSR
openssl req -new -key server.key -out server.csr \
  -subj "/C=US/ST=State/L=City/O=MyCompany/OU=IT/CN=localhost"

# Create extensions file for server certificate
cat > server_ext.cnf <<EOF
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=@alt_names
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid,issuer

[alt_names]
DNS.1=localhost
DNS.2=*.localhost
IP.1=127.0.0.1
EOF

# Sign server certificate with Intermediate CA
openssl x509 -req -in server.csr -CA intermediateCA.crt -CAkey intermediateCA.key \
  -CAcreateserial -out server.crt -days 365 -sha256 \
  -extfile server_ext.cnf
```

### Step 4: Create Certificate Chain and Keystores

```bash
# Create full certificate chain (server + intermediate)
cat server.crt intermediateCA.crt > fullchain.crt

# Create PKCS12 keystore with full chain
openssl pkcs12 -export -out server.p12 \
  -inkey server.key -in server.crt \
  -certfile intermediateCA.crt \
  -name "server" -password pass:changeit

# Create client truststore with only Root CA
keytool -import -alias rootca -file rootCA.crt \
  -keystore truststore.p12 -storetype PKCS12 \
  -storepass trustpass -noprompt
```

## Implementation Example

### Project Structure

```
src/main/
├── scala/example/ssl/tls/intermediatecasigned/
│   ├── ServerApp.scala
│   └── ClientApp.scala
└── resources/certs/tls/intermediate-ca-signed/
    ├── server.p12          # Server keystore with full chain
    ├── truststore.p12      # Client truststore with Root CA only
    ├── rootCA.crt          # Root CA certificate
    ├── intermediateCA.crt  # Intermediate CA certificate
    ├── server.crt          # Server certificate
    └── fullchain.crt       # Complete certificate chain
```

### Server Implementation

```scala
package example.ssl.tls.intermediatecasigned

import zio.Config.Secret
import zio._
import zio.http._

import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import scala.util.Try

object ServerApp extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "hello" -> handler {
      Response.text(
        """Hello from TLS server with certificate chain!
          |The server sent a certificate chain for verification.
          |""".stripMargin,
      )
    },
  )

  // SSL configuration using PKCS12 keystore with certificate chain
  private val sslConfig = SSLConfig.fromJavaxNetSslKeyStoreResource(
    keyManagerResource = "certs/tls/intermediate-ca-signed/server.p12",
    keyManagerPassword = Some(Secret("changeit")),
  )

  private val serverConfig = ZLayer.succeed {
    Server.Config.default
      .port(8443)
      .ssl(sslConfig)
  }

  override val run = {
    {
      for {
        _ <- Console.printLine("Certificate Chain TLS Server starting on https://localhost:8443/")
        _ <- Console.printLine("Endpoint:")
        _ <- Console.printLine("  - https://localhost:8443/hello       : Basic hello endpoint")
        _ <- Console.printLine("\nThe server will send the full certificate chain:")
        _ <- Console.printLine("  1. Server Certificate (signed by Intermediate CA)")
        _ <- Console.printLine("  2. Intermediate CA Certificate (signed by Root CA)")
        _ <- Console.printLine("\nPress Ctrl+C to stop...")
      } yield ()
    } *>
      Server.serve(routes).provide(serverConfig, Server.live)
  }

}
```

**Key Points:**
- Server keystore contains the complete certificate chain
- The server automatically sends both server and intermediate certificates
- Root CA certificate is not sent (clients should already have it)

### Client Implementation

```scala
package example.ssl.tls.intermediatecasigned

import zio._
import zio.http._
import zio.http.netty.NettyConfig

object ClientApp extends ZIOAppDefault {

  val app: ZIO[Client, Throwable, Unit] = for {
    _             <- Console.printLine("\nMaking HTTPS request to /hello")
    helloResponse <- ZClient.batched(Request.get("https://localhost:8443/hello"))
    helloBody     <- helloResponse.body.asString
    _             <- Console.printLine(s"Response Status: ${helloResponse.status}")
    _             <- Console.printLine(s"Response: $helloBody")
    _             <- displayChainVerificationExplanation()
  } yield ()

  def displayChainVerificationExplanation(): Task[Unit] =
    Console.printLine {
      """
Certificate Chain Verification Process:
=====================================
1. Server sends its certificate chain:
   - Server Certificate (CN=localhost)
   - Intermediate CA Certificate

2. Client verifies the chain:
   ✓ Server cert is signed by Intermediate CA
   ✓ Intermediate CA is signed by Root CA
   ✓ Root CA is in client's truststore (trusted)

Trust Chain Path:
┌─ Root CA (in client truststore)
│   CN=Root CA, OU=Security, O=RootCA
│
└─> Intermediate CA (received from server)
    CN=Intermediate CA, OU=Security, O=IntermediateCA
    │
    └─> Server Certificate (received from server)
        CN=localhost, OU=IT, O=MyCompany

Key Points:
- Client only needs Root CA in truststore
- Server provides intermediate certificates
- Full chain is validated automatically
      """
    }

  override val run = app.provide(
    ZLayer.succeed {
      ZClient.Config.default.ssl(
        ClientSSLConfig.FromTrustStoreResource(
          trustStorePath = "certs/tls/intermediate-ca-signed/truststore.p12",
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
- Client only needs the Root CA in its trust store
- Intermediate certificate is provided by the server
- Chain verification happens automatically

## How Certificate Chains Work

### The TLS Handshake with Certificate Chains

```
Client                                          Server
  |                                               |
  |-------------- ClientHello ------------------->|
  |                                               |
  |<------------- ServerHello --------------------|
  |<------------- Certificate --------------------|
  |              [Server Cert]                    |
  |              [Intermediate CA Cert]           |
  |<------------- ServerHelloDone ----------------|
  |                                               |
  | [Certificate Chain Validation]                |
  | 1. Build certificate path                     |
  | 2. Verify each signature in chain             |
  | 3. Check trust anchor (Root CA)               |
  | 4. Validate constraints and extensions        |
  | 5. Verify hostname                            |
  |                                               |
  |-------------- ClientKeyExchange ------------->|
  |========== Encrypted Application Data =========|
```

### Chain Validation Algorithm

```scala
// Simplified chain validation logic
def validateCertificateChain(chain: List[X509Certificate], 
                            trustStore: Set[X509Certificate]): Boolean = {
  
  def verifySignature(cert: X509Certificate, issuer: X509Certificate): Boolean = {
    try {
      cert.verify(issuer.getPublicKey)
      true
    } catch {
      case _: Exception => false
    }
  }
  
  def findIssuer(cert: X509Certificate, 
                 candidates: List[X509Certificate]): Option[X509Certificate] = {
    candidates.find(c => 
      c.getSubjectX500Principal.equals(cert.getIssuerX500Principal)
    )
  }
  
  @annotation.tailrec
  def validate(current: X509Certificate, 
               remaining: List[X509Certificate]): Boolean = {
    // Check if current certificate is trusted (root)
    if (trustStore.contains(current)) {
      true
    } else {
      // Find issuer in remaining certificates
      findIssuer(current, remaining) match {
        case Some(issuer) =>
          if (verifySignature(current, issuer)) {
            validate(issuer, remaining.filterNot(_ == issuer))
          } else {
            false
          }
        case None => false
      }
    }
  }
  
  chain.headOption.exists(serverCert => 
    validate(serverCert, chain.tail)
  )
}
```

## Running the Example

### 1. Verify Certificate Chain

```bash
# Examine the certificate chain
openssl pkcs12 -in server.p12 -nokeys -password pass:changeit | \
  openssl x509 -text | grep -E "Subject:|Issuer:"

# Verify chain integrity
openssl verify -CAfile rootCA.crt -untrusted intermediateCA.crt server.crt
```

### 2. Start the Server

```bash
sbt "runMain example.ssl.tls.intermediatecasigned.ServerApp"
```

Output:
```
Certificate Chain TLS Server starting on https://localhost:8443/
Endpoint:
  - https://localhost:8443/hello       : Basic hello endpoint

The server will send the full certificate chain:
  1. Server Certificate (signed by Intermediate CA)
  2. Intermediate CA Certificate (signed by Root CA)

Press Ctrl+C to stop...
```

### 3. Run the Client

```bash
sbt "runMain example.ssl.tls.intermediatecasigned.ClientApp"
```

### 4. Test with OpenSSL

```bash
# View the certificate chain sent by server
openssl s_client -connect localhost:8443 -showcerts < /dev/null 2>/dev/null | \
  openssl x509 -text | grep -E "Subject:|Issuer:"
```

## Best Practices for Certificate Chains

### 1. Chain Completeness

Always send the complete chain (except root):
```bash
# Correct: server cert + intermediate(s)
cat server.crt intermediate.crt > fullchain.crt

# Incorrect: server cert only
# This will cause validation failures
```

### 2. Chain Order

Certificates must be in the correct order:
```
1. Server certificate (leaf)
2. Intermediate CA that signed server cert
3. Higher intermediate (if any)
4. DO NOT include root CA
```

### 3. Path Length Constraints

```bash
# Check intermediate CA constraints
openssl x509 -in intermediateCA.crt -text | grep -A1 "Basic Constraints"
# Should show: CA:TRUE, pathlen:0
```

`pathlen:0` means this intermediate cannot sign other CAs, only end-entity certificates.

### 4. Certificate Extensions

Ensure proper extensions for each certificate type:

```bash
# Root CA extensions
basicConstraints=critical,CA:TRUE
keyUsage=critical,keyCertSign,cRLSign

# Intermediate CA extensions  
basicConstraints=critical,CA:TRUE,pathlen:0
keyUsage=critical,keyCertSign,cRLSign

# Server certificate extensions
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
```

## Production Considerations

### Major CA Certificate Chains

Most commercial CAs use 2-3 level chains:

**Let's Encrypt Example:**
```
DST Root CA X3 (or ISRG Root X1)
  └─> Let's Encrypt Authority X3
      └─> Your server certificate
```

**DigiCert Example:**
```
DigiCert Global Root CA
  └─> DigiCert SHA2 Secure Server CA
      └─> Your server certificate
```

### Cross-Signed Certificates

Some CAs use cross-signing for compatibility:

```
Old Root CA ─────────┐
                     ├─> Intermediate CA
New Root CA ─────────┘
```

This allows certificates to be validated through multiple paths.

### Certificate Transparency

Modern certificates include SCT (Signed Certificate Timestamp):

```bash
# Check for Certificate Transparency
openssl x509 -in server.crt -text | grep -A5 "CT Precertificate"
```

## Troubleshooting Common Issues

### Issue 1: Incomplete Certificate Chain

**Error:**
```
unable to verify the first certificate
verify error:num=21:unable to verify the first certificate
```

**Solution:**
```bash
# Check what certificates are being sent
openssl s_client -connect localhost:8443 -showcerts

# Ensure intermediate is included in server keystore
keytool -list -v -keystore server.p12 -storepass changeit
```

### Issue 2: Wrong Chain Order

**Error:**
```
certificate verify failed: invalid certificate chain
```

**Solution:**
Ensure certificates are concatenated in the correct order:
```bash
# Correct order
cat server.crt intermediate.crt > fullchain.crt

# Verify order
openssl crl2pkcs7 -nocrl -certfile fullchain.crt | \
  openssl pkcs7 -print_certs -noout
```

### Issue 3: Path Length Violation

**Error:**
```
path length constraint exceeded
```

**Solution:**
Check intermediate CA constraints:
```bash
openssl x509 -in intermediate.crt -text | grep pathlen
```

## Security Benefits

### 1. Isolation of Risk
- Root CA keys can be kept completely offline
- Compromise of intermediate CA has limited impact
- Intermediate CAs can be revoked without affecting root trust

### 2. Operational Flexibility
- Different intermediates for different purposes
- Easier certificate lifecycle management
- Gradual migration between certificate authorities

### 3. Compliance
- Meets industry standards (CA/Browser Forum)
- Required for public trust
- Enables proper audit trails

## Monitoring and Maintenance

### Certificate Chain Health Checks

```scala
def checkCertificateChainHealth(keystorePath: String): Task[ChainHealth] = {
  ZIO.attempt {
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(new FileInputStream(keystorePath), "changeit".toCharArray)
    
    val certChain = keyStore.getCertificateChain("server")
      .map(_.asInstanceOf[X509Certificate])
    
    ChainHealth(
      chainLength = certChain.length,
      expirations = certChain.map(cert => 
        (cert.getSubjectDN.getName, cert.getNotAfter)
      ),
      weakestLink = certChain.minBy(_.getNotAfter.getTime)
    )
  }
}
```

## Conclusion

Certificate chains with intermediate CAs represent the standard for production TLS deployments. They provide enhanced security through isolation of root keys while maintaining the flexibility needed for operational certificate management.

Key takeaways:
- Intermediate CAs protect root CA keys by keeping them offline
- Servers must send the complete chain (except root)
- Clients only need root CAs in their trust store
- Proper chain order and extensions are critical
- Certificate chains enable better security and operational practices

In the next article, we'll explore mutual TLS (mTLS), where both client and server present certificates for bidirectional authentication.