#!/usr/bin/env bash

# Generate self-signed certificate and private key in one step
echo "Creating self-signed certificate..."

# Generate private key and self-signed certificate
openssl req -x509 -newkey rsa:4096 -keyout server-key.pem \
    -out server-cert.pem -days 365 -nodes \
    -subj "/CN=localhost" \

echo "Self-signed certificate created."

# Create PKCS12 keystore for server
echo "Creating server keystore..."

# Create server-keystore.p12 (contains server certificate and private key)
openssl pkcs12 -export -in server-cert.pem \
    -inkey server-key.pem \
    -out server-keystore.p12 -name server -password pass:serverkeypass

echo "Server keystore created."

# Create truststore with the self-signed certificate
echo "Creating truststore..."

# Import self-signed certificate directly into truststore
keytool -importcert -file server-cert.pem \
    -keystore client-truststore.p12 \
    -storetype PKCS12 \
    -storepass clienttrustpass \
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
keytool -list -keystore truststore.p12 -storepass clienttrustpass -storetype PKCS12

echo -e "\nCertificate creation complete!"
echo "Files created in :"
echo "  - server-cert.pem   : Self-signed server certificate"
echo "  - server-key.pem    : Server private key"
echo "  - server-keystore.p12        : Server keystore (for SimpleTlsServer)"
echo "  - client-truststore.p12    : Client truststore (for SimpleTlsClient)"