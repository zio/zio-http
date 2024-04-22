---
id: cookie-authentication
title: "Cookie Authentication in ZIO Server"
---

**Cookie Authentication in ZIO Server**

**Objectives**

* Set authentication cookies in HTTP responses.
* Understand how to make cookies secure using flags.
* Clear (delete) cookies to log out users.

**Prerequisites**

* Basic understanding of ZIO HTTP and web server concepts.
* Familiarity with cookies and their role in authentication.

**Steps**

1. **Import Dependencies:**

   ```scala
   import zio._
   import zio.http._
   ```

2. **Define a Cookie:**

   ```scala
   private val cookie = Cookie.Response("key", "value", maxAge = Some(5 days))
   ```

   * **"key"**: The name of the authentication cookie.
   * **"value"**: A value to identify the logged-in user (could be a session ID or similar).
   * **maxAge**: (Optional) Sets the cookie's expiration time.

3. **Set Cookies in Responses:**

   ```scala
   Response.ok.addCookie(cookie)
   ```

4. **Enhance Cookie Security:**

   ```scala
   cookie.copy(isSecure = true, path = Some(Path.root / "secure-cookie"), isHttpOnly = true)
   ```

   * **isSecure**: Ensures the cookie is only transmitted over HTTPS connections.
   * **path**:  Restricts the cookie to a specific path on your server.
   * **isHttpOnly**: Prevents JavaScript from accessing the cookie (mitigates XSS risks).

5.  **Delete a Cookie (for logout functionality):**

    ```scala
    Response.ok.addCookie(Cookie.clear("key")) 
    ``` 
    This sets the cookie value to an empty string and effectively expires it.

**Complete Example**

See the [original code](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/CookieServerSide.scala) snippet for a full working example demonstrating these steps.

**Important Considerations**

* **Store Sensitive Information Securely:** Avoid storing highly sensitive data directly in cookies. Use session IDs and reference user data in a secure server-side database.
* **Cookie Validation:**  On subsequent requests, extract the cookie from the request and validate it against your server-side authentication data.