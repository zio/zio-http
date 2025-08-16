#!/usr/bin/env bash

# Step 1: Create a Certificate Authority (CA)
echo "Creating Certificate Authority for mTLS..."

# Generate CA private key
openssl genrsa -out ca-key.pem 4096

# Generate CA certificate
openssl req -new -x509 -days 3650 -key ca-key.pem -out ca-cert.pem -subj "/CN=MyCA"

echo "CA certificate created."

# Step 2: Create Server Certificate
echo "Creating Server Certificate..."

# Generate server private key
openssl genrsa -out server-key.pem 4096

# Generate server certificate signing request
openssl req -new -key server-key.pem -out server.csr -subj "/CN=localhost"

# Sign server certificate with CA
openssl x509 -req -days 365 -in server.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem

echo "Server certificate created."

# Step 3: Create Client Certificate
echo "Creating Client Certificate..."

# Generate client private key
openssl genrsa -out client-key.pem 4096

# Generate client certificate signing request
openssl req -new -key client-key.pem -out client.csr -subj "/CN=client"

# Sign client certificate with CA
openssl x509 -req -days 365 -in client.csr -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out client-cert.pem

echo "Client certificate created."

# Step 4: Create PKCS12 keystores
echo "Creating server keystore..."

# Create server-keystore.p12 (contains server certificate and private key)
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
    -out server-keystore.p12 -name server -password pass:serverkeypass \
    -certfile ca-cert.pem

echo "Server keystore created."

echo "Creating client keystore..."

# Create client-keystore.p12 (contains client certificate and private key)
openssl pkcs12 -export -in client-cert.pem -inkey client-key.pem \
    -out client-keystore.p12 -name client -password pass:clientkeypass \
    -certfile ca-cert.pem

echo "Client keystore created."

# Step 5: Create truststores for both server and client
echo "Creating server truststore..."

# Remove old truststore if it exists
rm -f server-truststore.p12

# Server needs to trust the CA that signed client certificates
keytool -importcert -file ca-cert.pem \
    -keystore server-truststore.p12 \
    -storetype PKCS12 \
    -storepass servertrustpass \
    -alias ca \
    -noprompt \
    -trustcacerts

echo "Server truststore created."

echo "Creating client truststore..."

# Remove old truststore if it exists
rm -f client-truststore.p12

# Client needs to trust the CA that signed server certificates
keytool -importcert -file ca-cert.pem \
    -keystore client-truststore.p12 \
    -storetype PKCS12 \
    -storepass clienttrustpass \
    -alias ca \
    -noprompt \
    -trustcacerts

echo "Client truststore created."

# Step 6: Verify the certificates
echo -e "\nVerifying certificates..."

# Verify server certificate against CA
echo "Server certificate verification:"
openssl verify -CAfile ca-cert.pem server-cert.pem

# Verify client certificate against CA
echo "Client certificate verification:"
openssl verify -CAfile ca-cert.pem client-cert.pem

# List contents of keystores
echo -e "\nContents of server-keystore.p12:"
keytool -list -keystore server-keystore.p12 -storepass serverkeypass -storetype PKCS12

echo -e "\nContents of client-keystore.p12:"
keytool -list -keystore client-keystore.p12 -storepass clientkeypass -storetype PKCS12

echo -e "\nContents of server-truststore.p12:"
keytool -list -keystore server-truststore.p12 -storepass servertrustpass -storetype PKCS12

echo -e "\nContents of client-truststore.p12:"
keytool -list -keystore client-truststore.p12 -storepass clienttrustpass -storetype PKCS12

# Clean up temporary files
rm -f server.csr client.csr server-ext.cnf client-ext.cnf ca-cert.srl

echo -e "\nmTLS Certificate creation complete!"
echo "Files created:"
echo "  CA files:"
echo "    - ca-cert.pem           : CA certificate"
echo "    - ca-key.pem            : CA private key"
echo ""
echo "  Server files:"
echo "    - server-cert.pem       : Server certificate"
echo "    - server-key.pem        : Server private key"
echo "    - server-keystore.p12   : Server keystore (contains server cert+ private key)"
echo "    - server-truststore.p12 : Server truststore (to verify client certs)"
echo ""
echo "  Client files:"
echo "    - client-cert.pem       : Client certificate"
echo "    - client-key.pem        : Client private key"
echo "    - client-keystore.p12   : Client keystore (contains client cert+ private key)"
echo "    - client-truststore.p12 : Client truststore (to verify server cert)"
echo ""