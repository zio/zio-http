---
id: implementing-mutual-tls
title: Implementing Mutual TLS (mTLS) 
---

## Introduction

Mutual TLS (mTLS) extends standard TLS by requiring both the client and server to present and verify certificates. This creates bidirectional authentication, ensuring both parties are who they claim to be. mTLS is crucial for zero-trust architectures, API security, and microservices communication.

Until now, all the guides in these series were based on standard TLS. This article demonstrates implementing mTLS with practical examples in Scala using the ZIO HTTP library.

## Understanding Mutual TLS

In standard TLS (One-way TLS) only the server presents a certificate to the client, allowing the client to verify the server's identity. The client remains anonymous, and there is no verification of the client's identity. 

However, in mutual TLS (mTLS), both the server and client present certificates to each other. This allows both parties to verify each other's identities, providing a higher level of security.

Here is a diagram that illustrates the difference between standard TLS and mutual TLS:

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

## Creating Certificates for mTLS

The certificate generation process involves creating a Certificate Authority (CA), signing server and client certificates, and creating keystores and truststores. This process is similar for both the server and client, with some differences in the certificate attributes.

### Step 1: Create Certificate Authority

We need to create a Certificate Authority (CA) that will sign both the server and client certificates. This CA will be used to verify the authenticity of the certificates presented by the server and client during the TLS handshake.

First, we will generate a private key for the CA:

```bash
# Generate CA private key
openssl genrsa -out ca-key.pem 4096
```

Now we can create a self-signed certificate for the CA.

```bash
# Generate CA certificate
openssl req -new -x509 -days 3650 -key ca-key.pem -out ca-cert.pem -subj "/CN=MyCA"
```

### Step 2: Create Server Certificate

Next, we will create a server certificate that will be signed by the CA. The server certificate will be used to authenticate the server to the client:

```bash
# Generate server private key
openssl genrsa -out server-key.pem 4096

# Generate server certificate signing request
openssl req -new -key server-key.pem -out server.csr -subj "/CN=localhost"

# Sign server certificate with CA
openssl x509 -req -days 365 -in server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem
```

### Step 3: Create Client Certificate

Similarly, we will create a client certificate that will also be signed by the CA. The client certificate will be used to authenticate the client to the server. This is where the mutual aspect of mTLS comes into play, where client is also required to get a certificate:

```bash
# Generate client private key
openssl genrsa -out client-key.pem 4096

# Generate client certificate signing request
openssl req -new -key client-key.pem -out client.csr -subj "/CN=client"

# Sign client certificate with CA
openssl x509 -req -days 365 -in client.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out client-cert.pem -extfile client-ext.cnf
```

### Step 4: Create PKCS12 Keystores

We have to create key stores for both the server and client:

```bash
# Create server-keystore.p12 (contains server certificate and private key)
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
    -out server-keystore.p12 -name server -password pass:serverkeypass \
    -certfile ca-cert.pem

# Create client-keystore.p12 (contains client certificate and private key)
openssl pkcs12 -export -in client-cert.pem -inkey client-key.pem \
    -out client-keystore.p12 -name client -password pass:clientkeypass \
    -certfile ca-cert.pem
```

### Step 5: Create PKCS12 Truststores

Both the server and client need a strustore which contains the CA certificate:

```bash
# Server truststore - Server needs to trust the CA that signed client certificates
keytool -importcert -file ca-cert.pem \
    -keystore server-truststore.p12 \
    -storetype PKCS12 \
    -storepass servertrustpass \
    -alias ca \
    -noprompt \
    -trustcacerts

# Client truststore - Client needs to trust the CA that signed server certificates
keytool -importcert -file ca-cert.pem \
    -keystore client-truststore.p12 \
    -storetype PKCS12 \
    -storepass clienttrustpass \
    -alias ca \
    -noprompt \
    -trustcacerts
```

## Implementation Example

Before we start coding, let's set up the project structure. We will create a ZIO HTTP project with the following directory structure:

### Project Structure

```
src/main/
├── scala/example/ssl/mtls/
│   ├── ServerApp.scala
│   └── ClientApp.scala
└── resources/certs/mtls/
    ├── server-keystore.p12    # Server's keystore (private key + certificate)
    ├── server-truststore.p12  # Server's trust store (contains CA cert)
    ├── client-keystore.p12    # Client's keystore (private key + certificate)
    ├── client-truststore.p12  # Client's trust store (cotains CA cert)
    └── ca-cert.pem            # Root CA certificate
```

### Server Implementation

The difference between one-way TLS and mutual TLS (mTLS) in server implementation lies in the direction of authentication. In one-way TLS, the server is only responsible for presenting its certificate to the client. Therefore, it is configured with a keystore that contains its certificate along with the server private key.

In contrast, mTLS requires both parties to authenticate each other. The server must also verify the client's certificate during the TLS handshake. To do this, the server is configured with both a keystore (containing its own certificate and private key) and a truststore (containing the CA certificate used to sign the client's certificate).

```scala mdoc:compile-only
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

Please note that we enabled the `ClientAuth.Required` option in the SSL configuration. This forces clients to present their certificates during the TLS handshake. If a client does not provide a valid certificate, the connection will be rejected.

If we want to access the client certificate, we can enable the `includeClientCert` option in the SSL configuration. This allows us to access the client certificate via `req.remoteCertificate` in the request handler.

### Client Implementation

Similarly, the client implementation for mTLS requires both a keystore (containing the client's certificate and private key) and a truststore (containing the CA certificate used to verify the server's certificate). The client will automatically send its certificate during the TLS handshake if configured correctly:

```scala mdoc:compile-only
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

## How mTLS Works

As you see the diagram, during the handshake process both the client and server exchange their certificates. The client sends its certificate to the server, and the server sends its certificate to the client. This allows both parties to verify each other's identities:

```
Client                                          Server
  |                                               |
  |-------------- ClientHello ------------------->|
  |<------------- ServerHello --------------------|
  |<------------- Server Certificate -------------| -> Server Certificate
  |<------------- ServerHelloDone ----------------|
  |-------------- Client Certificate ------------>| -> Client Certificate
  |-------------- ClientHelloDone --------------->|
  |                                               |                                               
  |-------------- ClientKeyExchange ------------->|
  |-------------- ChangeCipherSpec -------------->|
  |-------------- Finished ---------------------->|
  |<------------- ChangeCipherSpec ---------------|
  |<------------- Finished -----------------------|
  |                                               |
  |<========= Encrypted Application Data ========>|
```

## Conclusion

Mutual TLS provides the strongest form of authentication in TLS, ensuring both parties verify each other's identity through certificates. It's essential for high-security environments, zero-trust architectures, and service-to-service communication.

In this guide, we covered how to implement mTLS in a ZIO HTTP application, including generating certificates, configuring the server and client, and understanding the handshake process.

With mTLS, you can build highly secure systems where every connection is authenticated, authorized, and encrypted, providing defense in depth for your critical applications.
