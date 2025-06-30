---
id: implementing-tls-using-self-signed-server-certificate
title: Implementing TLS Using Self-signed Server Certificate
---

## Introduction

Self-signed certificates are TLS/SSL certificates that are signed by the same entity that creates them, rather than by a trusted Certificate Authority (CA). While not suitable for production public-facing applications, they are invaluable for development, testing, and internal services.

This tutorial demonstrates how to implement TLS using self-signed certificates with ZIO HTTP, covering certificate generation, server configuration, client setup, and security considerations. By the end of this guide, you'll understand how to create a complete TLS-enabled application using self-signed certificates.

## Understanding Self-signed Certificates

A self-signed certificate is a digital certificate that is signed by the same entity that it certifies, rather than by a trusted Certificate Authority. Unlike CA-issued certificates, self-signed certificates create their own chain of trust, making them both the issuer and the subject.

```
┌─────────────────────────┐
│   Self-signed Cert      │
├─────────────────────────┤
│ Subject: CN=localhost   │
│ Issuer:  CN=localhost   │ ← The issuer is the same as the subject!
│ Signed by: Own key      │
└─────────────────────────┘
```

This means the certificate's issuer and subject are the same entity - essentially, they're vouching for themselves. Unlike regular certificates that come with built-in trust because they're backed by recognized authorities, self-signed certificates don't have this third-party validation. Therefore, when users encounter a self-signed certificate, their browsers or applications typically show security warnings because there's no established chain of trust.

To use self-signed certificates properly, administrators must manually add them to each client's trusted certificate store, instructing the system to accept and trust that specific certificate. While this manual process can be time-consuming for large organizations, self-signed certificates are popular for internal company networks, testing environments, and situations where the cost and complexity of obtaining CA-issued certificates isn't justified by the security requirements.

### When to Use Self-signed Certificates

Self-signed certificates are well-suited for specific scenarios where public trust isn't required and security can be managed through controlled environments. They work effectively in development and testing environments where developers need SSL/TLS encryption for local servers and applications without the overhead of obtaining CA-issued certificates. 

Internal microservices communication within private networks is another use case, as services can trust each other through pre-configured certificate stores without needing external validation. However, please note that this configuration has drawbacks, such as the need for manual trust management and revocation processes, which can become cumbersome as the number of services grows.

They're also valuable for proof of concepts, demos, and experimental projects where quick setup is more important than established trust chains. 

However, self-signed certificates should never be used for public-facing production websites, e-commerce applications, or any service that requires users to trust the connection without manual certificate installation. In these public scenarios, the security warnings and trust barriers they create can damage user confidence, reduce conversion rates, and potentially expose users to man-in-the-middle attacks if they're trained to ignore certificate warnings. 

The general rule is that self-signed certificates are appropriate for closed, controlled environments where administrators can manage trust relationships directly, but they're unsuitable for any application where unknown users need to establish trust automatically.

## Generating Self-signed Certificates

The certificate generation process involves creating a private key, generating a self-signed certificate, and preparing keystores for both server and client use:

```bash
openssl req -x509 -newkey rsa:4096 -keyout server-key.pem \
    -out server-cert.pem -days 365 -nodes \
    -subj "/CN=localhost" 
```

With this command, we generate a new RSA private key (`server-key.pem`) and a self-signed certificate valid for 365 days (`server-cert.pem`). The `-subj` option specifies the subject details, which in this case is set to `CN=localhost`, indicating that the certificate is intended for use with the localhost domain.

### Generating the Server Key Store

To create a PKCS12 keystore that combines the private key and certificate into a single file, which is easier to manage in Java applications:

```bash
openssl pkcs12 -export -in server-cert.pem \
    -inkey server-key.pem \
    -out server-keystore.p12 -name server -password pass:serverkeypass
```

The `server-keystore.p12` file is a password-protected keystore containing the private key and certificate. Later, we can use this keystore in our ZIO HTTP server configuration.

### Creating the Client Trust Store

Since the certificate is self-signed, clients need to explicitly trust it. Therefore, we need to create a truststore that contains the server's certificate:

```bash
keytool -importcert -file server-cert.pem \
    -keystore client-truststore.p12 \
    -storetype PKCS12 \
    -storepass clienttrustpass \
    -alias server \
    -noprompt
```

Now we are ready to implement the server and client applications.

## Implementation

Before we start coding, let's set up the project structure. We will create a ZIO HTTP project with the following directory structure:

```
src/main/
├── scala/example/ssl/tls/selfsigned/
│   ├── ServerApp.scala
│   └── ClientApp.scala
└── resources/certs/tls/self-signed/
    ├── server-keystore.p12    # Server keystore with private key and certificate
    ├── client-truststore.p12  # Client truststore with server certificate
    ├── server-cert.pem        # PEM format certificate
    └── server-key.pem         # PEM format private key
```

### Server Implementation

To set up a self-signed TLS server using ZIO HTTP, we need to load the server's private key and certificate. We can either use `SSLConfig.fromJavaxNetSslKeyStoreResource` or `SSLConfig.fromResource`. The first option uses keystores, and the second one uses PEM files directly:

```scala mdoc:silent
import zio.Config.Secret
import zio.http._

// Option 1: Using PKCS12 keystore
private val sslConfig =
  SSLConfig.fromJavaxNetSslKeyStoreResource(
    keyManagerResource = "certs/tls/self-signed/server-keystore.p12",
    keyManagerPassword = Some(Secret("keystorepass")),
  )

// Option 2: Using PEM files directly
// Note: This might require the PEM files to be in the correct format
private val sslConfigPem =
  SSLConfig.fromResource(
    certPath = "certs/tls/self-signed/server-cert.pem",
    keyPath = "certs/tls/self-signed/server-key.pem",
  )
```

After loading the SSL configuration, we can set up the server to listen on a specific port (e.g., 8443) and serve requests over HTTPS:

```scala mdoc:compile-only
import zio.Config.Secret
import zio._
import zio.http._

object ServerApp extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "hello" ->
      handler(Response.text("Hello from self-signed TLS server! Connection secured!")),
  )

  private val serverConfig =
    ZLayer.succeed {
      Server.Config.default
        .port(8443)
        .ssl(sslConfig) // Using PKCS12 keystore
    }

  val run =
    Console.printLine("Self-signed TLS Server starting on https://localhost:8443/") *>
      Server.serve(routes).provide(serverConfig, Server.live)
}
```

**Key Points:**

- The server loads its private key and certificate from a PKCS12 keystore
- The `SSLConfig` handles all TLS configuration
- The server listens on port 8443 (standard HTTPS alternative port)

Now that the server is ready, let's move on to the client implementation.

### Client Implementation

Similar to the server, the client needs to be configured with specific SSL settings, but this time it will use a truststore that contains the server's self-signed certificate. This allows the client to trust the server's certificate during the TLS handshake:

```scala mdoc:silent:nest
import zio._

val sslConfig =
  ZLayer.succeed {
    ZClient.Config.default.ssl(
      ClientSSLConfig.FromTrustStoreResource(
        trustStorePath = "certs/tls/self-signed/client-truststore.p12",
        trustStorePassword = "clienttrustpass",
      )
    )
  }
```

Please note that `ClientSSLConfig` provides several constructors for reading the truststore. It also includes a `ClientSSLConfig#Default` instance, which is useful when you want the client to ignore certificate verification. However, in this case, we want to ensure that the client trusts the self-signed certificate, so we use the `FromTrustStoreResource` constructor.

Now we can implement the client application that will connect to the self-signed TLS server and make a secure HTTPS request:

```scala mdoc:compile-only
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
      sslConfig,
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
      ZClient.live,
    )
}
```

Note that without the truststore configuration, the client would reject the self-signed certificate, leading to an SSL handshake failure. The truststore allows the client to recognize and trust the server's self-signed certificate, enabling secure communication.

## How It Works

The TLS handshake process with self-signed certificates follows these steps:

1. **Client Hello**: Client initiates connection and sends supported cipher suites
2. **Server Hello**: Server responds with chosen cipher suite and sends certificate
3. **Certificate Verification**: Client validates certificate against truststore
4. **Key Exchange**: Client and server establish shared encryption keys
5. **Encrypted Communication**: All subsequent communication is encrypted

Unlike CA-issued certificates, self-signed certificates require explicit trust configuration. The client must have the server's certificate in its truststore to complete validation.

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

## Running the Example

### 1. Start the Server

```bash
sbt "zioHttpExample/runMain example.ssl.tls.selfsigned.ServerApp"
```

Output:
```
Self-signed TLS Server starting on https://localhost:8443/
```

### 2. Run the Client

```bash
sbt "zioHttpExample/runMain example.ssl.tls.selfsigned.ClientApp"
```

Output:
```
Making secure HTTPS request to self-signed server...
Response status: Ok
Response body: Hello from self-signed TLS server! Connection secured!
```

### 3. Testing with curl

You can test the server using various curl configurations to understand different certificate validation scenarios:

```bash
curl -v https://localhost:8443/hello
```

Running this command will show you the certificate verification process. Since the server uses a self-signed certificate, you will see an error about the certificate not being trusted unless you take additional steps to trust it:

```
*   Trying [::1]:8443...
* Connected to localhost (::1) port 8443
* ALPN: curl offers h2,http/1.1
* TLSv1.3 (OUT), TLS handshake, Client hello (1):
  * TLSv1.3 (IN), TLS handshake, Server hello (2):
  * TLSv1.3 (IN), TLS handshake, Encrypted Extensions (8):
  * TLSv1.3 (IN), TLS handshake, Certificate (11):
  * TLSv1.3 (OUT), TLS alert, unknown CA (560):
  * OpenSSL/3.0.14: error:16000069:STORE routines::unregistered scheme
* Closing connection
curl: (35) OpenSSL/3.0.14: error:16000069:STORE routines::unregistered scheme
```

To successfully connect, you can use the following curl command:

```bash
curl --cacert resources/certs/tls/self-signed/server-cert.pem https://localhost:8443/hello
```

This will print the following output:

```
Hello from self-signed TLS server! Connection secured!
```

You can use the `-v` option to see the details of the TLS handshake.

Note that it is the client's responsibility to perform certificate verification. Therefore, if the client decides to ignore certificate verification, the connection will succeed without any errors, but it will not be secure:

```bash
curl -k https://localhost:8443/hello
```

The `-k` option tells curl to skip certificate verification.

## Conclusion

Self-signed certificates provide a simple way to enable TLS encryption during development and testing. While they lack third-party validation, they offer the same encryption strength as CA-signed certificates. The key is understanding their limitations and using them appropriately.

Key takeaways:
- Self-signed certificates are perfect for development and testing
- Clients must explicitly trust self-signed certificates
- Never use them for public-facing production services

In the [next guide](implementing-tls-with-root-ca-signed-server-certificate.md), we'll explore using CA-signed certificates, which provide third-party validation and are suitable for production use.
