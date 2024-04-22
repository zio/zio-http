---
id: concrete-entity
title: "Concrete Entity Example"
sidebar_label: "Concrete Entity"
---
`Concrete entities` refer to specific data models or classes that represent the request and response payloads in an HTTP application. 


```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/ConcreteEntity.scala")
```

**Explaination**

The code demonstrates building a simple HTTP application that deals with concrete entities.

- It defines a request entity `CreateUser` with a single field `name`, representing the name of the user to be created.
- There's also a response entity `UserCreated` with a single field `id`, representing the ID of the newly created user.
- The `user` handler function takes a `CreateUser` request and returns a `UserCreated` response with a hardcoded ID value.
- The `app` HTTP application is constructed by first mapping the `user` handler to adapt the incoming request to `CreateUser`, then mapping the resulting `UserCreated` response to a text response containing the ID.
- Finally, the `run` function serves the `app` using a default server configuration.