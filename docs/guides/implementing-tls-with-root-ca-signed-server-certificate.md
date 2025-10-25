---
id: implementing-tls-with-root-ca-signed-server-certificate
title: Implementing TLS with Root CA-Signed Server Certificate
---

## Introduction

Root Certificate Authority (CA) signed certificates form the backbone of trust on the internet. Unlike self-signed certificates, CA-signed certificates are validated by a trusted third party, enabling secure communication without manual trust configuration.

This article demonstrates implementing TLS with CA-signed certificates, using examples in Scala with the ZIO HTTP library.

Please note that this article focuses specifically on implementing CA-signed certificates rather than the administrative aspects of certificate management. While we won't cover the detailed processes of establishing a production certificate authority or purchasing certificates from commercial CAs, we will create a root certificate authority manually to demonstrate the core implementation concepts you would encounter in a production environment.

## Understanding CA-Signed Certificates

### What is a CA-Signed Certificate?

A CA-signed certificate is a digital certificate that has been:

- Verified and signed by a trusted Certificate Authority
- Validated to ensure the requester controls the domain
- Issued with the CA's digital signature

```
┌─────────────────────────┐
│   Root CA Certificate   │
│   (In Trust Stores)     │
└───────────┬─────────────┘
            |
            │ Signs
            |
            ▼
┌─────────────────────────┐
│   Server Certificate    │
├─────────────────────────┤
│ Subject: CN=example.com │
│ Issuer:  CN=Root CA     │
│ Signed by: CA's key     │
└─────────────────────────┘
```

### Trust Model

The trust model for CA-signed certificates relies on a hierarchy of trust. Root CAs are pre-installed in operating systems and browsers, or manually added to trust stores. When a server presents a certificate signed by a trusted CA, clients can verify the certificate's authenticity without manual intervention. Any certificate signed by these root CAs is automatically trusted.

## Creating a Private CA for Development

### Step 1: Create Root CA

For this example, we'll create our own Root CA to simulate the production process:

```bash
# Generate Root CA Private Key
openssl genrsa -out ca-key.pem 4096

# Generate Root CA Certificate
openssl req -new -x509 -days 3650 -key ca-key.pem -out ca-cert.pem \
    -subj "/C=US/ST=State/L=City/O=Example CA/OU=IT/CN=Example Root CA"
```

### Step 2: Create Server Certificate Signing Request (CSR)

The next step is to create a server certificate signed by this Root CA. To do this, we have to create a Certificate Signing Request (CSR) for the server, which should be signed by the Root CA.

To create a CSR, we have to provide the server's private key and specify the subject details. Let's generate a private key for the server:

```bash
# Generate Server Private Key
openssl genrsa -out server-key.pem 4096
```

Now, we are ready to generate the server's CSR:

```bash
# Generate Server Certificate Signing Request (CSR)
openssl req -new -key server-key.pem -out server.csr \
    -subj "/C=US/ST=State/L=City/O=Example Server/OU=IT/CN=localhost"
```

### Step 3: Sign Server Certificate with Root CA

Before signing the server certificate, we need to create an extensions file that specifies the certificate's properties, such as key usage and subject alternative names (SANs):

```bash
# Create Extensions File for Server Certificate
cat > server-ext.cnf << EOF
subjectAltName = DNS:localhost,IP:127.0.0.1
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
EOF
```

Now it is time to sign the server certificate using the Root CA's private key:

```bash
# Sign Server Certificate With Root CA
openssl x509 -req -days 365 -in server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem -extfile server-ext.cnf
```

### Step 4: Create Server Keystore

When using TLS, the server needs a keystore that contains its private key and certificate. We will create a PKCS12 keystore that includes the server's private key and the signed certificate:

```bash
# Create server-keystore.p12 (Contains server-cert.pem and server-key.pem)
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
    -out server-keystore.p12 -name server -password pass:serverkeypass
```

### Step 5: Create Client Trust Store

The client needs a trust store that contains the Root CA certificate. This allows the client to verify the server's certificate during the TLS handshake.

```bash
# Create client-truststore.p12 (Contains ca-cert.pem)
keytool -importcert -file ca-cert.pem \
    -keystore client-truststore.p12 \
    -storetype PKCS12 \
    -storepass clienttrustpass \
    -alias ca \
    -noprompt \
    -trustcacerts
```

Please note the difference between the client trust store configuration in this tutorial and the previous one. In the previous tutorial, we used a self-signed certificate for the server, which required importing that specific server certificate into the client trust store. However, in this tutorial, we're using a Root CA-signed certificate for the server, so we only need to import the Root CA certificate into the client trust store. This allows the client to trust any certificate signed by that Root CA, including our server's certificate.

Now all the cryptographic materials are ready for our TLS implementation. Let's move on to the actual implementation of client and server applications.

## Implementation Example

### Project Structure

Before we start coding, let's set up the project structure. We will create a ZIO HTTP project with the following directory structure:

```
src/main/
├── scala/example/ssl/tls/rootcasigned/
│   ├── ServerApp.scala
│   └── ClientApp.scala
└── resources/certs/tls/root-ca-signed/
    ├── server-keystore.p12    # Server's private key and certificate
    └── client-truststore.p12  # Client's truststore with Root CA
```

### Server Implementation

```scala mdoc:compile-only
import zio.Config.Secret
import zio._
import zio.http._

object ServerApp extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "hello" ->
      handler(Response.text("Hello from TLS server! Connection secured!"))
  )

  private val sslConfig =
    SSLConfig.fromJavaxNetSslKeyStoreResource(
      keyManagerResource = "certs/tls/root-ca-signed/server-keystore.p12",
      keyManagerPassword = Some(Secret("serverkeypass"))
    )

  private val serverConfig =
    ZLayer.succeed {
      Server.Config.default
        .port(8443)
        .ssl(
          sslConfig
        )
    }

  override val run =
    Server.serve(routes).provide(serverConfig, Server.live)

}
```

**Key Points:**

- Server uses a certificate signed by the Root CA
- Only needs its own keystore (private key + certificate)
- No trust store needed for basic TLS (only for mTLS)

### Client Implementation

The client will connect to the server using the Root CA's certificate in its trust store. This allows it to verify the server's certificate without manual configuration.

```scala mdoc:compile-only
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

  private val sslConfig =
    ZClient.Config.default.ssl(
      ClientSSLConfig.FromTrustStoreResource(
        "certs/tls/root-ca-signed/client-truststore.p12",
        "clienttrustpass",
      )
    )

  override val run =
    app.provide(
      ZLayer.succeed(sslConfig),
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
      ZClient.live,
    )

}
```

**Key Points:**

- Client only needs the Root CA in its trust store
- Automatically trusts any certificate signed by this Root CA
- No need to import individual server certificates

## How It Works

### Certificate Validation Process

When a client receives a CA-signed certificate from the server during the TLS handshake, it performs a comprehensive validation process to establish trust. Here's a simplified overview of the steps involved:

1. **Find the Certificate Authority** - Client looks at who signed the server's certificate and searches its trust store for that CA's certificate
2. **Verify the Signature** - Uses the CA's public key to verify the server certificate is genuine and was actually signed by the trusted CA
3. **Check Certificate Validity** - Ensures the certificate hasn't expired and is being used within its valid date range
4. **Verify the Hostname** - Checks that the certificate was issued for the correct server by matching the server name with what's in the certificate

If all checks pass, the connection proceeds securely. If any check fails, the connection is rejected with an error.

```
Client                                          Server
  |                                               |
  |-------------- ClientHello ------------------->|
  |                                               |
  |<------------- ServerHello --------------------|
  |<------------- Certificate --------------------|  ← Server Cert
  |<------------- ServerHelloDone ----------------|
  |                                               |
  | [Certificate Validation Process]              |
  | 1. Extract issuer and Find it in truststore ✓ |
  | 2. Verify the Signature ✓                     |
  | 4. Check Certificate Validity ✓               |
  | 5. Verify the Hostname ✓                      |
  |                                               |
  |-------------- ClientKeyExchange ------------->|
  |-------------- ChangeCipherSpec -------------->|
  |-------------- Finished ---------------------->|
  |                                               |
  |<------------- ChangeCipherSpec ---------------|
  |<------------- Finished -----------------------|
  |                                               |
  |========== Encrypted Application Data ======-==|
```

The key advantage of CA-signed certificates is that clients already trust the Root CA certificates pre-installed in their system, enabling automatic verification without any manual configuration. This makes the validation process significantly more streamlined compared to self-signed certificates, where each certificate must be manually added to the client's trust store to establish trust.

## Running the Example

### 1. Generate Certificates (One-time setup)

Run the certificate generation script

```bash
cd src/main/resources/certs/tls/root-ca-signed
./generate-certificates.sh
```

### 2. Start the Server

```bash
sbt "zioHttpExample/runMain example.ssl.tls.rootcasigned.ServerApp"
```

### 3. Run the Client

```bash
sbt "zioHttpExample/runMain example.ssl.tls.rootcasigned.ClientApp"
```

Output:
```
Making secure HTTPS requests...
Text response: Hello from TLS server! Connection secured!
```

## Conclusion

CA-signed certificates provide the foundation for secure communication on the internet. By leveraging the existing trust infrastructure, they enable seamless secure connections without manual configuration.

Key takeaways:

- CA-signed certificates are automatically trusted by clients
- The trust model relies on pre-installed root certificates
- Production certificates should come from recognized CAs
- Private CAs are excellent for development and internal services

In the [next article](implementing-tls-with-intermediate-ca-signed-server-certificate.md), we'll explore certificate chains with intermediate CAs, which provide additional security and flexibility in certificate management.
