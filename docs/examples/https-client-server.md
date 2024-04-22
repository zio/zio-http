---
id: https-client-server
title: HTTPS Client and Server Example
sidebar_label: Https Client and Server
---

## Client Example

This code demonstrate a simple HTTPS client that send an HTTP GET request to a specific URL and retrieve the response:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HttpsClient.scala")
```

**Explanation**

1. **URL and Headers**:
   - The `url` variable contains the target URL for the HTTPS request.
   - The `headers` variable contains the headers to be included in the request, specifically the `Host` header.

2. **SSL Configuration**:
   - SSL configuration is set up using `ClientSSLConfig.FromTrustStoreResource`.
   - It specifies the trust store path (`truststore.jks`) and password (`changeit`).

3. **Client Configuration**:
   - The `clientConfig` variable is set up with the SSL configuration.

4. **Request Execution**:
   - The `program` variable contains the ZIO effect for making the HTTPS request.
   - It sends a GET request to the specified URL with the provided headers.
   - Upon receiving the response, it extracts the response body as a string.

5. **Running the Client**:
   - The `run` effect is set up to execute the `program`.
   - It's provided with the necessary dependencies:
     - `clientConfig`: SSL configuration
     - `Client.customized`: Customized client
     - `NettyClientDriver.live`: Netty client driver
     - `DnsResolver.default`: Default DNS resolver
     - `NettyConfig.default`: Default Netty configuration
     - `Scope.default`: Default scope



## Server Example

This example demonstrates how to use ZIO to create an HTTP server with HTTPS support and configure SSL using a keystore:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HttpsHelloWorld.scala")
```
**Explanation**

1. **HTTP Routes**:
   - The `app` variable defines two HTTP routes:
     - GET `/text`: Responds with "Hello World!" as plain text.
     - GET `/json`: Responds with `{"greetings": "Hello World!"}` as JSON.

2. **SSL Configuration**:
   - The SSL configuration is loaded from resources using the `SSLConfig.fromResource` method.
   - It specifies the path to the certificate (`server.crt`) and the private key (`server.key`).
   - The `behaviour` parameter is set to `Accept`, meaning the server accepts HTTPS connections.

3. **Server Configuration**:
   - The server configuration is created using `Server.Config.default`.
   - It sets the server port to `8090` and includes the SSL configuration.

4. **Server Startup**:
   - The `run` effect starts the server using `Server.serve`.
   - It's provided with the server configuration wrapped in a layer (`configLayer`) and the `Server.live` environment.