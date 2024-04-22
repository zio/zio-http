---
id: how-to-utilize-signed-cookies
title: "How to Utilize Signed Cookies with ZIO HTTP"
---

**What are Signed Cookies?**

* Signed cookies use a cryptographic signature to protect their contents from tampering. This ensures the data the server sent to the client remains unchanged.

**Why Use Signed Cookies?**

* **Security:** Prevents clients from modifying sensitive data stored in cookies.
* **Data Integrity:** Ensures that the information received by the server is the same that was originally sent.

**Step-by-Step Guide**

1. **Import ZIO HTTP:**
   ```scala
   import zio.http._
   ```

2. **Define Your Cookie:**
   ```scala
   private val cookie = Cookie.Response("key", "hello", maxAge = Some(5 days))
   ```
   * **"key"**: The name of the cookie.
   * **"hello"**: The value stored in the cookie.
   * **maxAge**: (Optional) Sets an expiration time (here, 5 days).

3. **Create a Route to Set the Cookie:**
   ```scala
  private val app = Http.collect[Request] { case Method.GET -> Root / "cookie" =>
    Response.ok.addCookie(cookie.sign("secret"))
  }
   ```
   * **"secret"**:  A secret key known only to your server, used for signing.

4. **Start the Server:**
   ```scala
   val run = Server.serve(app).provide(Server.default)
   ```

**Explanation**

* The provided code sets a signed cookie named "key" with the value "hello". The cookie will expire in 5 days.
* When the user visits the `/cookie` route, the server will send this signed cookie in the response.
* The client's browser will store the cookie and include it in future requests to your server.
* To read the signed cookie on the server, you'll need to use the same secret key you used for signing.

**Important Notes**

* **Choose a Strong Secret:** Use a long, complex secret key to protect your signed cookies. 
* **Store Sensitive Data Cautiously:**  While signed cookies offer protection, avoid storing highly sensitive data in them if possible.
