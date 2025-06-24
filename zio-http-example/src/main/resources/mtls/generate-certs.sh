#!/usr/bin/env bash

echo "Generating certificates in PKIX/X.509 format..."

# Generate CA private key
openssl genrsa -out ca.key 4096

# Generate CA certificate (self-signed, X.509 format)
openssl req -new -x509 -days 3650 -key ca.key -out ca.crt \
  -subj "/C=US/ST=State/L=City/O=Example CA/CN=Example CA"

# Generate server private key
openssl genrsa -out server.key 4096

# Generate server certificate request
openssl req -new -key server.key -out server.csr \
  -subj "/C=US/ST=State/L=City/O=Example Server/CN=localhost"

# Create server certificate extensions file for proper PKIX compliance
cat > server_ext.cnf <<EOF
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:localhost,IP:127.0.0.1
EOF

# Sign server certificate with CA (X.509 v3 with extensions)
openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key \
  -CAcreateserial -out server.crt -extfile server_ext.cnf

# Generate client private key
openssl genrsa -out client.key 4096

# Generate client certificate request
openssl req -new -key client.key -out client.csr \
  -subj "/C=US/ST=State/L=City/O=Example Client/CN=client1"

# Create client certificate extensions file for proper PKIX compliance
cat > client_ext.cnf <<EOF
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=clientAuth
EOF

# Sign client certificate with CA (X.509 v3 with extensions)
openssl x509 -req -days 365 -in client.csr -CA ca.crt -CAkey ca.key \
  -CAcreateserial -out client.crt -extfile client_ext.cnf

# Optional: Convert to PKCS#12 format (includes private key and certificate)
echo "Creating PKCS#12 bundles..."
openssl pkcs12 -export -out server.p12 -inkey server.key -in server.crt \
  -certfile ca.crt -password pass:changeit -name "server"

openssl pkcs12 -export -out client.p12 -inkey client.key -in client.crt \
  -certfile ca.crt -password pass:changeit -name "client"

# Optional: Convert to DER format (binary format)
echo "Creating DER format certificates..."
openssl x509 -in ca.crt -outform DER -out ca.der
openssl x509 -in server.crt -outform DER -out server.der
openssl x509 -in client.crt -outform DER -out client.der

# Verify certificates
echo ""
echo "Verifying certificates..."
openssl verify -CAfile ca.crt server.crt
openssl verify -CAfile ca.crt client.crt

# Display certificate details
echo ""
echo "CA Certificate:"
openssl x509 -in ca.crt -text -noout | grep -E "(Subject:|Issuer:|Not Before:|Not After:|Key Usage:|Extended Key Usage:)" | head -10

echo ""
echo "Server Certificate:"
openssl x509 -in server.crt -text -noout | grep -E "(Subject:|Issuer:|Not Before:|Not After:|Key Usage:|Extended Key Usage:|Subject Alternative Name:)" | head -10

echo ""
echo "Client Certificate:"
openssl x509 -in client.crt -text -noout | grep -E "(Subject:|Issuer:|Not Before:|Not After:|Key Usage:|Extended Key Usage:)" | head -10

# Clean up temporary files
rm -f *.csr *.srl *.cnf

# Set appropriate permissions
chmod 600 *.key
chmod 644 *.crt *.der
chmod 600 *.p12

echo ""
echo "Certificates generated successfully!"
echo ""
echo "Available formats:"
echo "- PEM format: *.crt, *.key (Base64 encoded, ASCII)"
echo "- DER format: *.der (Binary format)"
echo "- PKCS#12 format: *.p12 (Bundle with private key, password: changeit)"
echo ""
echo "For Java/Scala KeyStore, you can use the PKCS#12 files directly or import them:"
echo "keytool -importkeystore -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass changeit -destkeystore server.jks -deststorepass changeit"