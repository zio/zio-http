#!/usr/bin/env bash

# Step 1: Create a Root Certificate Authority (Root CA)
echo "Creating Certificate Authority..."

# Generate Root CA Private Key
openssl genrsa -out ca-key.pem 4096

# Generate Root CA Certificate
openssl req -new -x509 -days 3650 -key ca-key.pem -out ca-cert.pem \
    -subj "/C=US/ST=State/L=City/O=Example CA/OU=IT/CN=Example CA"

echo "Root CA certificate created."

# Step 2: Create Server Certificate
echo "Creating Server Certificate..."

# Generate Server Private Key
openssl genrsa -out server-key.pem 4096

# Generate Server Certificate Signing Request (CSR)
openssl req -new -key server-key.pem -out server.csr \
    -subj "/C=US/ST=State/L=City/O=Example Server/OU=IT/CN=localhost"

# Create Extensions File for Server Certificate
cat > server-ext.cnf << EOF
subjectAltName = DNS:localhost,IP:127.0.0.1
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
EOF

# Sign Server Certificate With Root CA
openssl x509 -req -days 365 -in server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem -extfile server-ext.cnf

echo "Server certificate created."

# Step 3: Create PKCS12 Keystore for Server
echo "Creating server keystore..."

# Create server-keystore.p12 (Contains server-cert.pem and server-key.pem)
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
    -out server-keystore.p12 -name server -password pass:serverkeypass \

echo "Server keystore created."

# Step 4: Create Truststore Using Keytool
echo "Creating truststore..."

# Remove Old Truststore if it Exists
rm -f client-truststore.p12

# Create client-truststore.p12 (Contains ca-cert.pem)
keytool -importcert -file ca-cert.pem \
    -keystore client-truststore.p12 \
    -storetype PKCS12 \
    -storepass clienttrustpass \
    -alias ca \
    -noprompt \
    -trustcacerts

echo "Truststore created."

# Step 5: Verify the Certificates
echo -e "\nVerifying certificates..."

# Verify Server Certificate Against Root CA
openssl verify -CAfile ca-cert.pem server-cert.pem

# List Contents of Keystores
echo -e "\nContents of server-keystore.p12:"
keytool -list -keystore server-keystore.p12 -storepass serverkeypass -storetype PKCS12

echo -e "\nContents of client-truststore.p12:"
keytool -list -keystore client-truststore.p12 -storepass clienttrustpass -storetype PKCS12

# Clean up Temporary Files
rm -f server.csr server-ext.cnf ca-cert.srl

echo -e "\nCertificate creation complete!"
echo "Files created:"
echo "  - ca-cert.pem           : CA certificate"
echo "  - ca-key.pem            : CA private key"
echo "  - server-cert.pem       : Server certificate"
echo "  - server-key.pem        : Server private key"
echo "  - server-keystore.p12   : Server keystore (contains server-cert.pem and server-key.pem)"
echo "  - client-truststore.p12 : Client truststore (contains ca-cert.pem)"
