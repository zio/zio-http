---
id: endpoint-scala3
title: "Endpoint Scala 3 Syntax"
sidebar_label: "Endpoint Scala 3 Syntax"
---

```scala
import zio.http.*
import zio.http.codec.*
import zio.http.endpoint.*

import java.util.UUID

type NotFound[EntityId] = EntityId
type EntityId           = UUID

val union: ContentCodec[String | UUID | Boolean] =
  HttpCodec.content[String] || HttpCodec.content[UUID] || HttpCodec.content[Boolean]

val unionEndpoint =
  Endpoint(Method.GET / "api" / "complex-union")
    .outCodec(union)

val unionWithErrorEndpoint
    : Endpoint[Unit, Unit, NotFound[EntityId] | String, UUID | Unit, AuthType.None] =
  Endpoint(Method.GET / "api" / "union-with-error")
    .out[UUID]
    .orOut[Unit](Status.NoContent)
    .outError[NotFound[EntityId]](Status.NotFound)
    .orOutError[String](Status.BadRequest)

val impl = unionWithErrorEndpoint.implementEither { _ =>
  val result: Either[NotFound[EntityId] | String, UUID | Unit] = Left("error")
  result
}
```
