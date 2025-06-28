---
id: implementing-tls-using-root-ca-signed-server-certificates
title: Implementing TLS with Root CA-Signed Server Certificate
---

## Introduction

Root Certificate Authority (CA) signed certificates form the backbone of trust on the internet. Unlike self-signed certificates, CA-signed certificates are validated by a trusted third party, enabling secure communication without manual trust configuration.

This article demonstrates implementing TLS with CA-signed certificates, using examples in Scala with the ZIO HTTP library.

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

The CA trust model works because:
1. **Pre-installed Trust**: Operating systems and browsers come with pre-installed root CA certificates
2. **Chain of Trust**: Any certificate signed by these CAs is automatically trusted
3. **Validation**: CAs verify domain ownership before issuing certificates
4. **Revocation**: CAs can revoke compromised certificates

## Types of Certificate Validation

### 1. Domain Validation (DV)
- Verifies domain control only
- Automated process
- Fastest and cheapest
- Example: Let's Encrypt

### 2. Organization Validation (OV)
- Verifies domain control and organization identity
- Manual verification process
- Shows organization name in certificate

### 3. Extended Validation (EV)
- Extensive verification of legal entity
- Strictest validation process
- Historically showed green bar in browsers

## Creating a Private CA for Development

For this example, we'll create our own CA to simulate the production process:

### Step 1: Create Root CA

```bash
# Generate Root CA private key
openssl genrsa -out rootCA.key 4096

# Generate Root CA certificate
openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 3650 \
  -out rootCA.crt \
  -subj "/C=US/ST=State/L=City/O=RootCA/OU=Security/CN=Root CA"

# Convert to PKCS12 for Java (optional)
openssl pkcs12 -export -out rootCA.p12 -inkey rootCA.key -in rootCA.crt \
  -name "rootca" -password pass:rootpass
```

### Step 2: Create Server Certificate Signing Request (CSR)

```bash
# Generate server private key
openssl genrsa -out server.key 2048

# Create CSR
openssl req -new -key server.key -out server.csr \
  -subj "/C=US/ST=State/L=City/O=MyCompany/OU=IT/CN=localhost"
```

### Step 3: Sign Server Certificate with Root CA

```bash
# Create extensions file for server certificate
cat > server_ext.cnf <<EOF
basicConstraints=CA:FALSE
keyUsage = digitalSignature, keyEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = *.localhost
IP.1 = 127.0.0.1
EOF

# Sign the server certificate
openssl x509 -req -in server.csr -CA rootCA.crt -CAkey rootCA.key \
  -CAcreateserial -out server.crt -days 365 -sha256 \
  -extfile server_ext.cnf

# Create PKCS12 keystore for server
openssl pkcs12 -export -out server-keystore.p12 \
  -inkey server.key -in server.crt \
  -name "server" -password pass:serverkeypass
```

### Step 4: Create Client Trust Store

```bash
# Import Root CA into client truststore
keytool -import -alias rootca -file rootCA.crt \
  -keystore client-truststore.p12 -storetype PKCS12 \
  -storepass clienttrustpass -noprompt
```

## Implementation Example

### Project Structure

```
src/main/
├── scala/example/ssl/tls/rootcasigned/
│   ├── ServerApp.scala
│   └── ClientApp.scala
└── resources/certs/tls/root-ca-signed/
    ├── server-keystore.p12    # Server's private key and certificate
    ├── client-truststore.p12  # Client's truststore with Root CA
    ├── rootCA.crt             # Root CA certificate
    ├── server.crt             # Server certificate (signed by Root CA)
    └── server.key             # Server private key
```

### Server Implementation

```scala
package example.ssl.tls.rootcasigned

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

```scala
package example.ssl.tls.rootcasigned

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
      ),
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

```
Client                                          Server
  |                                               |
  |-------------- ClientHello ------------------->|
  |                                               |
  |<------------- ServerHello --------------------|
  |<------------- Certificate --------------------|  ← CA-signed cert
  |<------------- ServerHelloDone ----------------|
  |                                               |
  | [Certificate Validation Process]              |
  | 1. Extract issuer (Root CA)                   |
  | 2. Find Root CA in truststore ✓               |
  | 3. Verify server cert signature with CA key ✓ |
  | 4. Check validity dates ✓                     |
  | 5. Verify hostname matches CN/SAN ✓           |
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

### Trust Chain Verification

1. **Server presents certificate**: Signed by Root CA
2. **Client checks issuer**: Identifies Root CA as issuer
3. **Client verifies signature**: Uses Root CA's public key from trust store
4. **Additional checks**: Validity period, hostname verification, key usage
5. **Connection established**: All checks pass, secure channel created

## Running the Example

### 1. Generate Certificates (One-time setup)

```bash
# Run the certificate generation script
./generate-ca-certificates.sh
```

### 2. Start the Server

```bash
sbt "runMain example.ssl.tls.rootcasigned.ServerApp"
```

### 3. Run the Client

```bash
sbt "runMain example.ssl.tls.rootcasigned.ClientApp"
```

Output:
```
Making secure HTTPS requests...
Text response: Hello from TLS server! Connection secured!
```

### 4. Verify Certificate Chain

```bash
# Check server certificate details
openssl s_client -connect localhost:8443 -showcerts < /dev/null

# Verify certificate chain
openssl verify -CAfile rootCA.crt server.crt
```

## Production Considerations

### Using Commercial CAs

For production, you'll typically:

1. **Generate CSR**: Create a certificate signing request
```bash
openssl req -new -key server.key -out server.csr \
  -subj "/C=US/ST=State/L=City/O=YourCompany/CN=yourdomain.com"
```

2. **Submit to CA**: Upload CSR to your chosen CA (DigiCert, Sectigo, etc.)

3. **Complete Validation**:
    - DV: Respond to email or add DNS record
    - OV/EV: Provide business documentation

4. **Install Certificate**: CA provides signed certificate

### Using Let's Encrypt (Free CA)

```bash
# Using Certbot
certbot certonly --standalone -d yourdomain.com

# Using acme.sh
acme.sh --issue -d yourdomain.com --standalone
```

### Cloud Provider Solutions

**AWS Certificate Manager:**
```bash
aws acm request-certificate --domain-name yourdomain.com \
  --validation-method DNS
```

**Azure Key Vault:**
```bash
az keyvault certificate create --vault-name MyKeyVault \
  --name MyServerCert --policy "$(az keyvault certificate get-default-policy)"
```

## Security Best Practices

### 1. Certificate Security

- **Protect Private Keys**: Use hardware security modules (HSMs) for production
- **Key Rotation**: Regularly renew certificates (90 days recommended)
- **Strong Algorithms**: Use RSA 2048+ or ECDSA P-256+
- **Secure Storage**: Encrypt keystores and use strong passwords

### 2. Certificate Validation

```scala
// Enhanced client configuration with hostname verification
private val sslConfig = ZClient.Config.default.ssl(
  ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "client-truststore.p12",
    trustStorePassword = "trustpass"
  ).copy(
    // Additional security settings
    enableHostnameVerification = true,
    enableOcspStapling = true
  )
)
```

### 3. Monitor Certificate Expiration

```scala
// Certificate expiration monitoring
def checkCertificateExpiration(keystorePath: String): Task[Int] = {
  ZIO.attempt {
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(new FileInputStream(keystorePath), "password".toCharArray)
    
    val cert = keyStore.getCertificate("server").asInstanceOf[X509Certificate]
    val daysUntilExpiry = 
      (cert.getNotAfter.getTime - System.currentTimeMillis) / (1000 * 60 * 60 * 24)
    
    daysUntilExpiry.toInt
  }
}
```

## Common Issues and Solutions

### Issue 1: Certificate Chain Incomplete

**Error:**
```
unable to verify the first certificate
```

**Solution:**
Ensure you're sending the complete certificate chain:
```bash
# Combine certificates
cat server.crt intermediate.crt > fullchain.crt
```

### Issue 2: Hostname Mismatch

**Error:**
```
javax.net.ssl.SSLPeerUnverifiedException: Hostname localhost not verified
```

**Solution:**
Ensure certificate includes correct Subject Alternative Names:
```bash
openssl x509 -in server.crt -text | grep -A1 "Subject Alternative Name"
```

### Issue 3: Untrusted Root CA

**Error:**
```
PKIX path building failed: unable to find valid certification path
```

**Solution:**
Add Root CA to Java trust store:
```bash
keytool -import -trustcacerts -keystore $JAVA_HOME/lib/security/cacerts \
  -storepass changeit -alias myca -file rootCA.crt
```

## Advantages Over Self-Signed Certificates

1. **Automatic Trust**: No manual trust store configuration for clients
2. **Browser Compatibility**: No security warnings
3. **Scalability**: Easy to deploy across many clients
4. **Professional**: Inspires confidence in users
5. **Revocation Support**: Can revoke compromised certificates
6. **Validation**: Third-party verification of identity

## Conclusion

CA-signed certificates provide the foundation for secure communication on the internet. By leveraging the existing trust infrastructure, they enable seamless secure connections without manual configuration.

Key takeaways:
- CA-signed certificates are automatically trusted by clients
- The trust model relies on pre-installed root certificates
- Production certificates should come from recognized CAs
- Private CAs are excellent for development and internal services
- Certificate management is crucial for maintaining security

In the next article, we'll explore certificate chains with intermediate CAs, which provide additional security and flexibility in certificate management.