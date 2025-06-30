---
id: implementing-tls-with-intermediate-ca-signed-server-certificate
title: Implementing TLS with Intermediate CA-signed Server Certificate
---

## Introduction

In production environments, server certificates are rarely signed directly by root Certificate Authorities. Instead, they use intermediate CAs to create a certificate chain. This approach provides better security, flexibility, and follows industry best practices.

This article demonstrates implementing TLS with certificate chains using intermediate CAs, with practical examples in Scala using the ZIO HTTP library.

## Understanding Certificate Chains

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

Let's examine a real certificate chain from a major site, e.g. google.com:

```bash
# Check Google's certificate chain
openssl s_client -connect google.com:443 -showcerts < /dev/null

# You'll see something like:

# 0 s:CN=*.google.com (server certificate)
# 1 s:C=US, O=Google Trust Services, CN=WR2 (intermediate)
# 2 s:C=US, O=Google Trust Services LLC, CN=GTS Root R1 (root)
```

## Creating a Certificate using Intermediate CA

In this tutorial, we will create root and intermediate CAs to simulate the process of signing a server certificate with an intermediate CA. This will help you understand how to set up a secure TLS environment using certificate chains. In real-world scenarios, you can obtain certificates for your servers from well-known Certificate Authorities and no need to create your own CAs, unless you are managing your own PKI (Public Key Infrastructure) in an internal network.

### Step 1: Create Root CA

```bash
# Generate Root CA private key (keep this extremely secure!)
openssl genrsa -out root-ca-key.pem 4096

# Generate Root CA certificate (self-signed, valid for 10 years)
openssl req -new -x509 -days 3650 -key root-ca-key.pem -out root-ca-cert.pem \
    -subj "/C=Country/ST=State/L=City/O=RootCA/OU=Security/CN=Root CA"
```

### Step 2: Create Intermediate CA

Generate intermediate CA private key:

```bash
openssl genrsa -out intermediate-ca-key.pem 4096
```

Then generate Intermediate CA certificate signing request (CSR):

```bash
openssl req -new -key intermediate-ca-key.pem -out intermediate-ca.csr \
    -subj "/C=US/ST=State/L=City/O=IntermediateCA/OU=Security/CN=Intermediate CA"
```

Create extensions file for Intermediate CA:

```bash
cat > intermediate-ca-ext.cnf << EOF
basicConstraints = CA:TRUE, pathlen:0
keyUsage = digitalSignature, keyCertSign, cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer:always
EOF
```

Explanation of all these fields is beyond the scope of this article, but generally this configuration file defines the extensions that make a certificate function as an intermediate CA.

Now we can sign Intermediate CA certificate with Root CA:

```bash
openssl x509 -req -days 1825 -in intermediate-ca.csr \
    -CA root-ca-cert.pem -CAkey root-ca-key.pem \
    -CAcreateserial -out intermediate-ca-cert.pem \
    -extfile intermediate-ca-ext.cnf
```

### Step 3: Create Server Certificate

To generate server certificate, first we should generate a private key for the server:

```bash
openssl genrsa -out server-key.pem 4096
```

Then, we have to generate server certificate signing request (CSR):

```bash
# Generate server certificate signing request
openssl req -new -key server-key.pem -out server.csr \
    -subj "/C=Country/ST=State/L=City/O=MyCompany/OU=IT/CN=localhost"
```

Now we can sign server certificate with intermediate CA using the CSR configuration:

```bash
openssl x509 -req -days 365 -in server.csr \
    -CA intermediate-ca-cert.pem -CAkey intermediate-ca-key.pem \
    -CAcreateserial -out server-cert.pem
```

### Step 4: Create Server Keystore

Now that we have the server certificate signed by the intermediate CA, we need to create a keystore that contains both the server certificate and the intermediate CA certificate. This is crucial because during the TLS handshake, the server will send its certificate along with the intermediate CA certificate to the client.

During the handshake, the client will receive both the server certificate and the intermediate CA certificate. The client will then verify the server certificate against the intermediate CA certificate, which in turn is signed by the root CA already present in the client's trust store.

Now, let's create the server keystore with the certificate chain:

```bash
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
    -out server-keystore.p12 -name server -password pass:keystorepass \
    -certfile intermediate-ca-cert.pem \
    -caname intermediate
```

## Step 5: Create Client Truststore

The client only needs the Root CA certificate, so let's create a trust store containing the Root CA:

```bash
keytool -importcert -file root-ca-cert.pem \
    -keystore client-truststore.p12 \
    -storetype PKCS12 \
    -storepass clienttrustpass \
    -alias rootca \
    -noprompt \
    -trustcacerts
```

## Implementation Example

### Project Structure

Before we start coding, let's set up the project structure. We will create a ZIO HTTP project with the following directory structure:

```
src/main/
├── scala/example/ssl/tls/intermediatecasigned/
│   ├── ServerApp.scala
│   └── ClientApp.scala
└── resources/certs/tls/intermediate-ca-signed/
    ├── server-keystore.p12    # Server keystore with full chain
    └── client-truststore.p12  # Client truststore with Root CA only
```

### Server Implementation

```scala mdoc:compile-only
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
    keyManagerResource = "certs/tls/intermediate-ca-signed/server-keystore.p12",
    keyManagerPassword = Some(Secret("serverkeystore")),
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
        _ <- Console.printLine("\nThe server will send the following certificate chain during the SSL handshake:")
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

```scala mdoc:compile-only
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
  } yield ()

  override val run = app.provide(
    ZLayer.succeed {
      ZClient.Config.default.ssl(
        ClientSSLConfig.FromTrustStoreResource(
          trustStorePath = "certs/tls/intermediate-ca-signed/client-truststore.p12",
          trustStorePassword = "clienttrustpass",
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
- Intermediate certificate is provided by the server during SSL handshake
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
  | 4. Check Certificate Validity ✓               |
  | 5. Verify hostname                            |
  |                                               |
  |-------------- ClientKeyExchange ------------->|
  |-------------- ChangeCipherSpec -------------->|
  |-------------- Finished ---------------------->|
  |                                               |
  |<------------- ChangeCipherSpec ---------------|
  |<------------- Finished -----------------------|
  |                                               |
  |========== Encrypted Application Data =========|
```

## Running the Example

### 1. Start the Server

To run the server, open a terminal and execute the following command:

```bash
sbt "zioHttpExample/runMain example.ssl.tls.intermediatecasigned.ServerApp"
```

Output:
```
Certificate Chain TLS Server starting on https://localhost:8443/
Endpoint:
  - https://localhost:8443/hello       : Basic hello endpoint

The server will send the following certificate chain during the SSL handshake:
  1. Server Certificate (signed by Intermediate CA)
  2. Intermediate CA Certificate (signed by Root CA)

Press Ctrl+C to stop...
```

### 2. Run the Client

To run the client, open a new terminal and execute:

```bash
sbt "zioHttpExample/runMain example.ssl.tls.intermediatecasigned.ClientApp"
```

## Conclusion

Certificate chains with intermediate CAs provide essential security benefits by isolating risk and keeping root CAs offline. This approach is the industry standard used by major Certificate Authorities worldwide.

Our ZIO HTTP implementation demonstrated how certificate chains work in practice: servers automatically present the complete chain during TLS handshake, while clients only need to trust the root CA. This delegation of trust creates a scalable and secure architecture. This foundation prepares you to build production-ready applications that meet enterprise security standards in modern distributed systems.

In the [next article](implementing-mutual-tls.md), we'll explore mutual TLS (mTLS), where both client and server present certificates for bidirectional authentication.
