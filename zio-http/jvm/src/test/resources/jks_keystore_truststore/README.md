# Test JKS Keystore and Truststore File

To create a testing keystore and truststore for both a client and a server for mutual TLS, follow these steps:

# 1. **Generate a Key Pair for the Server**:

   ```sh

   keytool -genkeypair -alias server -keyalg RSA -keystore server_keystore_with_pass.jks -storepass 123456 -keypass 123456 -dname "CN=server, OU=Test, O=Test, L=Test, ST=Test, C=US" -validity 3650

   ```


# 2. **Generate a Key Pair for the Client**:

   ```sh

   keytool -genkeypair -alias client -keyalg RSA -keystore client_keystore_with_pass.jks -storepass 123456 -keypass 123456 -dname "CN=client, OU=Test, O=Test, L=Test, ST=Test, C=US" -validity 3650

   ```


# 3. **Export the Server's Public Certificate**:

   ```sh

   keytool -export -alias server -keystore server_keystore_with_pass.jks -file server.crt -storepass 123456

   ```


# 4. **Export the Client's Public Certificate**:

   ```sh

   keytool -export -alias client -keystore client_keystore_with_pass.jks -file client.crt -storepass 123456

   ```


# 5. **Create a Truststore for the Server and Import the Client's Certificate**:

   ```sh

   keytool -import -alias client-cert -file client.crt -keystore server_truststore_with_pass.jks -storepass 123456 -noprompt

   ```


# 6. **Create a Truststore for the Client and Import the Server's Certificate**:

   ```sh
keytool -import -alias server-cert -file server.crt -keystore client_truststore_with_pass.jks -storepass 123456 -noprompt


   ```


This setup ensures that both the server and the client have their own keystores and truststores, and they trust each other's certificates. Adjust the paths and passwords as needed.
