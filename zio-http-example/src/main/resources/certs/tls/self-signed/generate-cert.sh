#!/usr/bin/env bash

# Generate self-signed certificate and private key in one step
echo "Creating self-signed certificate..."

# Generate private key and self-signed certificate
openssl req -x509 -newkey rsa:4096 -keyout server-key.pem \
    -out server-cert.pem -days 365 -nodes \
    -subj "/C=US/ST=State/L=City/O=MyCompany/OU=IT/CN=localhost" \
    -addext "subjectAltName = DNS:localhost,IP:127.0.0.1"

echo "Self-signed certificate created."

# Create PKCS12 keystore for server
echo "Creating server keystore..."

# Create server.p12 (contains server certificate and private key)
openssl pkcs12 -export -in server-cert.pem \
    -inkey server-key.pem \
    -out server.p12 -name server -password pass:changeit

echo "Server keystore created."

# Create truststore with the self-signed certificate
echo "Creating truststore..."

# Import self-signed certificate directly into truststore
keytool -importcert -file server-cert.pem \
    -keystore truststore.p12 \
    -storetype PKCS12 \
    -storepass trustpass \
    -alias server \
    -noprompt

echo "Truststore created."

# Verify the certificates
echo -e "\nVerifying certificates..."

# Display certificate details
openssl x509 -in server-cert.pem -text -noout | grep -E "(Subject:|Issuer:|Not Before|Not After)"

# List contents of keystores
echo -e "\nContents of server.p12:"
keytool -list -keystore server.p12 -storepass changeit -storetype PKCS12

echo -e "\nContents of truststore.p12:"
keytool -list -keystore truststore.p12 -storepass trustpass -storetype PKCS12

echo -e "\nCertificate creation complete!"
echo "Files created in :"
echo "  - server-cert.pem   : Self-signed server certificate"
echo "  - server-key.pem    : Server private key"
echo "  - server.p12        : Server keystore (for SimpleTlsServer)"
echo "  - truststore.p12    : Client truststore (for SimpleTlsClient)"