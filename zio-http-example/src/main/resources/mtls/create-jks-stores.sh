#!/usr/bin/env bash

# This script converts PEM certificates to JKS format
# Run this after generate-certs.sh

echo "Creating JKS keystores from existing certificates..."

# First, ensure we have the certificates
if [ ! -f "ca.crt" ] || [ ! -f "client.crt" ] || [ ! -f "client.key" ]; then
    echo "Error: Certificates not found. Run generate-certs.sh first."
    exit 1
fi

# Create a PKCS12 keystore first (intermediate step for client cert + key)
echo "Creating PKCS12 keystore for client..."
openssl pkcs12 -export \
    -in client.crt \
    -inkey client.key \
    -out client-temp.p12 \
    -name client \
    -password pass:temppass

# Convert PKCS12 to JKS for client keystore
echo "Converting to JKS format for client keystore..."
keytool -importkeystore \
    -srckeystore client-temp.p12 \
    -srcstoretype PKCS12 \
    -srcstorepass temppass \
    -destkeystore client.jks \
    -deststoretype JKS \
    -deststorepass changeit \
    -destkeypass changeit \
    -alias client

# Create truststore with CA certificate
echo "Creating JKS truststore with CA certificate..."
keytool -import \
    -file ca.crt \
    -alias ca \
    -keystore truststore.jks \
    -storepass trustpass \
    -noprompt

# Create server JKS (optional, if you want server to use JKS too)
echo "Creating server JKS keystore..."
openssl pkcs12 -export \
    -in server.crt \
    -inkey server.key \
    -out server-temp.p12 \
    -name server \
    -password pass:temppass

keytool -importkeystore \
    -srckeystore server-temp.p12 \
    -srcstoretype PKCS12 \
    -srcstorepass temppass \
    -destkeystore server.jks \
    -deststoretype JKS \
    -deststorepass changeit \
    -destkeypass changeit \
    -alias server

# List contents of keystores for verification
echo ""
echo "Client keystore contents:"
keytool -list -keystore client.jks -storepass changeit

echo ""
echo "Truststore contents:"
keytool -list -keystore truststore.jks -storepass trustpass

echo ""
echo "Server keystore contents:"
keytool -list -keystore server.jks -storepass changeit

# Clean up temporary files
rm -f client-temp.p12 server-temp.p12

echo ""
echo "JKS keystores created successfully!"
echo ""
echo "Files created:"
echo "- client.jks (password: changeit) - Contains client private key and certificate"
echo "- truststore.jks (password: trustpass) - Contains CA certificate for server verification"
echo "- server.jks (password: changeit) - Contains server private key and certificate"
echo ""
echo "To run the JKS client: sbt \"runMain MtlsClientJKS\""
