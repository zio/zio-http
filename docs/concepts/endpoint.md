# Endpoint API

The Endpoint API is a declarative DSL for defining HTTP endpoints. It is a way to define a type safe API for your application.
It comes with batteries included and supports out of the box JSON, protobuf, plain text and binary data serialization and deserialization. It also supports automatic validation via ZIO Schema, and automatic OpenAPI documentation generation.
Endpoints can be used to implement not only servers but also clients.

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.codec.PathCodec.path
import zio.http.codec._
import zio.http.endpoint._
import zio.schema._
import zio.schema.annotation._
import zio.http.endpoint.openapi._
import zio.http.template.Dom
import zio.schema.validation.Validation

final case class UserParams(city: String, @validate(Validation.greaterThan(17)) age: Int)

object UserParams {
  implicit val schema: Schema[UserParams] = DeriveSchema.gen[UserParams]
}

val endpoint =
  // typed path parameter "user"
  Endpoint(Method.GET / "hello" / string("user"))
    // reads the two query parameters city and age from the request and validates the age
    .query(HttpCodec.query[UserParams])
    // support for HTML templates included
    .out[Dom]

/* SERVER */

// Generates OpenAPI documentation for the endpoint
val openApi = OpenAPIGen.fromEndpoints("User API", "1.0.0", endpoint)

// Routes for the endpoint and the Swagger UI
val routes =
  endpoint.implement { case (user, params) =>
    ZIO.succeed(Dom.text(s"Hello $user, you are ${params.age} years old and live in ${params.city}"))
  }.toRoutes ++ SwaggerUI.routes("intern" / "apidoc", openApi)

/* CLIENT */

val baseUrl = url"http://localhost:8080"

def endpointExecutor(client: Client) = EndpointExecutor(client, baseUrl)

val clientApp: ZIO[Scope with Client, Nothing, Dom] = for {
  client <- ZIO.service[Client]
  dom   <- endpointExecutor(client)(endpoint("John", UserParams("New York", 25)))
} yield dom
```

For more details on the Endpoint API, see the [documentation](./../reference/endpoint.md).
