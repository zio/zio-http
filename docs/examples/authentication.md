---
id: authentication
title: "Authentication Example"
sidebar_label: "Authentication"
---

Authentication is the process of verifying a user's identity before granting access to a web server or application. It's like a security guard checking your ID at the entrance to a building.


## Authentication Server Example

```scala mdoc:passthrough
import utils._
printSource("zio-http-example/src/main/scala/example/AuthenticationServer.scala")
```
**Explanation**
This code sets up an HTTP server with protected routes that require user login.

* **Protected Area:**  The route `/profile/me` is secured.  Users MUST provide a valid token to access it. 
* **Login:** The `/login` route allows users to get a token by providing a username and password. 
    * **Clever Trick:** For demonstration, the valid password is simply the username reversed.
* **Token Power:** Tokens (JWTs) are used for authorization.  
    * The server checks incoming requests for valid tokens.
    * Valid tokens include the username. 

**Security Middleware:**

* The `bearerAuthWithContext` middleware does all the heavy lifting:
    *  Checks for tokens on incoming requests
    *  Verifies token validity
    *  Adds the username to the request if the token is good




## Authentication Client Example

```scala mdoc:passthrough
import utils._
printSource("zio-http-example/src/main/scala/example/AuthenticationClient.scala")
```

### Explanation 

This code shows how a client app interacts with a secure server.

1.  **Get a Key:** The client sends its username and password to the server's login system.  If correct, it receives a special code (a JWT token).
2.  **Use the Key:** The client includes this code in the header of a request to a protected area of the server.
3.  **Server Check:**  The server verifies the code. If valid, it allows the client to access the protected resource.

**Key Points**

* **Security:** This demonstrates how apps can control access to sensitive data or features using JWT tokens for authentication.
* **Two-Part Process:**  Clients first get a token, then use that token to access protected things. 



## Middleware Basic Authentication Example

```scala mdoc:passthrough
import utils._
printSource("zio-http-example/src/main/scala/example/BasicAuth.scala")
```

**Explanation**

* **HTTP Routes:** 
  - This part sets up a route that listens for requests like `http://your-server-address/user/bob/greet` where "bob" is the provided user name. 
  - The code anticipates getting a username this way and will use it to personalize the greeting response.

* **Authentication Middleware:**
  - This is the crucial security component. It intercepts requests before they reach your route handler.
  - The middleware enforces Basic Authentication. This means:
     - Clients (like web browsers) must include an `Authorization` header in requests.
     -  The header contains the username/password encoded in Base64 format.
  - It checks if the provided credentials match the hardcoded "admin" / "admin" values.

* **HTTP Application:**
  - This ties everything together. It tells the system:
     -  Use the authentication middleware for security.
     -  If authentication passes, then handle requests with the route definition. 

* **Server Setup:**
  - This starts the HTTP server and makes it listen for incoming web requests on a particular port.

**How It Works:**

1. A user tries to access `/user/{name}/greet` in their browser.
2. The middleware intercepts and sees no `Authorization` header.
3. The server sends back a `401 Unauthorized` status and a `WWW-Authenticate: Basic` header telling the browser what kind of auth is needed.
4. The browser usually prompts the user for a username and password.
5. The user enters "admin" / "admin" (or other credentials if you changed those).
6. The request is repeated, this time with the Base64 encoded `Authorization` header.
7. The middleware validates the credentials match.
8. Since it's now authorized, the request gets to the route handler, generating a greeting.

**Important Considerations:**

* **Security:**
   - Basic Authentication sends credentials in a lightly encoded format (Base64). It's **essential** to use HTTPS with this to encrypt traffic.
   -  Storing passwords in plain text within the code is a terrible security practice! Implement proper password storage (hashing, salting) in a real-world application. 
* **Flexibility:**
   - For larger projects, you'd likely extract authentication logic into a separate, more configurable middleware or module.
* **Advanced Authentication:**
    - Basic Authentication is very simplistic. Look into more robust methods like token-based authentication (JWT) or OAuth for production environments.

