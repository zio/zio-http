---
id: authentication
title: "Authentication Server Example"
---


This code shows how to implement a server with bearer authentication middle ware in `zio-http`


```scala
import java.time.Clock

import zio._

import zio.http._
import zio.http.Middleware.bearerAuth
import zio.http.codec.PathCodec.string

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

object AuthenticationServer extends ZIOAppDefault {

  /**
   * This is an example to demonstrate barer Authentication middleware. The
   * Server has 2 routes. The first one is for login,Upon a successful login, it
   * will return a jwt token for accessing protected routes. The second route is
   * a protected route that is accessible only if the request has a valid jwt
   * token. AuthenticationClient example can be used to makes requests to this
   * server.
   */

  // Secret Authentication key
  val SECRET_KEY = "secretKey"

  implicit val clock: Clock = Clock.systemUTC

  // Helper to encode the JWT token
  def jwtEncode(username: String): String = {
    val json  = s"""{"user": "${username}"}"""
    val claim = JwtClaim {
      json
    }.issuedNow.expiresIn(300)
    Jwt.encode(claim, SECRET_KEY, JwtAlgorithm.HS512)
  }

  // Helper to decode the JWT token
  def jwtDecode(token: String): Option[JwtClaim] = {
    Jwt.decode(token, SECRET_KEY, Seq(JwtAlgorithm.HS512)).toOption
  }

  // Http app that is accessible only via a jwt token
  def user: HttpApp[Any] = Routes(
    Method.GET / "user" / string("name") / "greet" -> handler { (name: String, req: Request) =>
      Response.text(s"Welcome to the ZIO party! ${name}")
    },
  ).toHttpApp @@ bearerAuth(jwtDecode(_).isDefined)

  // App that let's the user login
  // Login is successful only if the password is the reverse of the username
  def login: HttpApp[Any] =
    Routes(
      Method.GET / "login" / string("username") / string("password") ->
        handler { (username: String, password: String, req: Request) =>
          if (password.reverse.hashCode == username.hashCode) Response.text(jwtEncode(username))
          else Response.text("Invalid username or password.").status(Status.Unauthorized)
        },
    ).toHttpApp

  // Composing all the HttpApps together
  val app: HttpApp[Any] = login ++ user

  // Run it like any simple app
  override val run = Server.serve(app).provide(Server.default)
}
```

### Explanation 

It showcases two routes: one for user login and another for accessing protected routes using a JWT (JSON Web Token) authentication mechanism. The protected route is accessible only if the request contains a valid JWT token.
