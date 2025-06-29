#!/usr/bin/env bash

# Step 1: Create Root Certificate Authority (Root CA)
echo "Creating Root Certificate Authority..."

# Generate Root CA private key
openssl genrsa -out root-ca-key.pem 4096

# Generate Root CA certificate (self-signed, valid for 10 years)
openssl req -new -x509 -days 3650 -key root-ca-key.pem -out root-ca-cert.pem \
    -subj "/CN=Root CA"

echo "Root CA certificate created."

# Step 2: Create Intermediate Certificate Authority
echo "Creating Intermediate Certificate Authority..."

# Generate Intermediate CA private key
openssl genrsa -out intermediate-ca-key.pem 4096

# Generate Intermediate CA certificate signing request
openssl req -new -key intermediate-ca-key.pem -out intermediate-ca.csr \
    -subj "/CN=Intermediate CA"

# Create extensions file for Intermediate CA
cat > intermediate-ca-ext.cnf << EOF
basicConstraints = CA:TRUE, pathlen:0
keyUsage = digitalSignature, keyCertSign, cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer:always
EOF

# Sign Intermediate CA certificate with Root CA
openssl x509 -req -days 1825 -in intermediate-ca.csr \
    -CA root-ca-cert.pem -CAkey root-ca-key.pem \
    -CAcreateserial -out intermediate-ca-cert.pem \
    -extfile intermediate-ca-ext.cnf

echo "Intermediate CA certificate created."

# Step 3: Create Server Certificate (signed by Intermediate CA)
echo "Creating Server Certificate..."

# Generate server private key
openssl genrsa -out server-key.pem 4096

# Generate server certificate signing request
openssl req -new -key server-key.pem -out server.csr \
    -subj "/CN=localhost"

# Sign server certificate with Intermediate CA
openssl x509 -req -days 365 -in server.csr \
    -CA intermediate-ca-cert.pem -CAkey intermediate-ca-key.pem \
    -CAcreateserial -out server-cert.pem \

echo "Server certificate created."

# Step 4: Create PKCS12 keystore for server with full chain (except the root CA)
echo "Creating server keystore with certificate chain..."

# Create server-keystore.p12 with the certificate chain (without -chain option)
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
    -out server-keystore.p12 -name server -password pass:keystorepass \
    -certfile intermediate-ca-cert.pem \
    -caname intermediate

# Step 5: Create truststore with ONLY the root CA certificate
echo "Creating truststore with root CA only..."

# Remove old truststore if it exists
rm -f truststore.p12

# Import ONLY the Root CA certificate into truststore
keytool -importcert -file root-ca-cert.pem \
    -keystore client-truststore.p12 \
    -storetype PKCS12 \
    -storepass clienttrustpass \
    -alias rootca \
    -noprompt \
    -trustcacerts

echo "Truststore created with root CA only."

# Step 6: Verify the certificate chain
echo -e "\nVerifying certificate chain..."

# Verify server certificate against the chain
cat intermediate-ca-cert.pem root-ca-cert.pem > ca-chain.pem
openssl verify -CAfile ca-chain.pem server-cert.pem

# Verify intermediate CA against root CA
openssl verify -CAfile root-ca-cert.pem intermediate-ca-cert.pem

# Display certificate chain
echo -e "\nCertificate chain:"
openssl crl2pkcs7 -nocrl -certfile server-chain.pem | \
    openssl pkcs7 -print_certs -noout

# List contents of keystores
echo -e "\nContents of server.p12:"
keytool -list -keystore server.p12 -storepass changeit -storetype PKCS12 -v | grep "Alias name:"

echo -e "\nContents of truststore.p12 (should only contain root CA):"
keytool -list -keystore truststore.p12 -storepass trustpass -storetype PKCS12

# Clean up temporary files
rm -f *.csr *-ext.cnf *.srl ca-chain.pem

echo -e "\nCertificate chain creation complete!"
echo "Files created:"
echo "  - root-ca-cert.pem         : Root CA certificate"
echo "  - root-ca-key.pem          : Root CA private key"
echo "  - intermediate-ca-cert.pem : Intermediate CA certificate"
echo "  - intermediate-ca-key.pem  : Intermediate CA private key"
echo "  - server-cert.pem          : Server certificate"
echo "  - server-key.pem           : Server private key"
echo "  - server-chain.pem         : Server certificate chain (server + intermediate)"
echo "  - server-keystore.p12      : Server keystore with full chain (except the root CA)"
echo "  - client-truststore.p12    : Client truststore (contains ONLY root CA)"
echo ""
echo "Certificate chain structure:"
echo "  Root CA (in client truststore)"
echo "    └── Intermediate CA (sent by server)"
echo "          └── Server Certificate (sent by server)"
