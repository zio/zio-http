---
id: concrete-entity
title: "Concrete Entity Example"
sidebar_label: "Concrete Entity"
---

```scala mdoc:silent

import zio._

import zio.http._

/**
 * Example to build app on concrete entity
 */
object ConcreteEntity extends ZIOAppDefault {
  // Request
  case class CreateUser(name: String)

  // Response
  case class UserCreated(id: Long)

  val user: Handler[Any, Nothing, CreateUser, UserCreated] =
    Handler.fromFunction[CreateUser] { case CreateUser(_) =>
      UserCreated(2)
    }

  val app: HttpApp[Any] =
    user
      .contramap[Request](req => CreateUser(req.path.encode))     // Http[Any, Nothing, Request, UserCreated]
      .map(userCreated => Response.text(userCreated.id.toString)) // Http[Any, Nothing, Request, Response]
      .toHttpApp

  // Run it like any simple app
  val run =
    Server.serve(app).provide(Server.default)
}
```