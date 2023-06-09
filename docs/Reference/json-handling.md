**JSON Handling in ZIO HTTP**

ZIO HTTP provides built-in support for handling JSON data in your applications. This allows you to easily parse incoming JSON requests, serialize data into JSON responses, and work with JSON data structures in a type-safe manner. Here's an overview of how JSON handling is typically done in ZIO HTTP.

**Parsing JSON Requests**

To parse incoming JSON requests, you can use the `zio-json` library, which integrates seamlessly with ZIO HTTP. `zio-json` provides a type-safe API for working with JSON data. Here's an example of how you can parse a JSON request body:

```scala
import zio.http._
import zio.json._
import zio._

case class MyRequest(name: String, age: Int)

val httpApp: HttpApp[Any, Throwable] = Http.collectM {
  case request @ Method.POST -> Root / "api" / "endpoint" =>
    request
      .asJsonDecode[MyRequest]
      .flatMap { myRequest =>
        // Handle the parsed JSON request
        // myRequest will be an instance of MyRequest
        // ...
        // Return an HTTP response
        ZIO.succeed(Response.text("Request handled successfully"))
      }
}
```

In this example, we define a case class `MyRequest` that represents the expected structure of the JSON request body. We use the `.asJsonDecode` method to parse the request body into an instance of `MyRequest`. If the parsing is successful, we can access the parsed request and perform further processing.

**Serializing JSON Responses**

To serialize data into JSON responses, you can use the `.asJsonEncode` method provided by the `zio-json` library. Here's an example of how you can serialize a response object into JSON:

```scala
import zio.http._
import zio.json._
import zio._

case class MyResponse(message: String)

val httpApp: HttpApp[Any, Throwable] = Http.collect {
  case Method.GET -> Root / "api" / "endpoint" =>
    val myResponse = MyResponse("Hello, World!")
    Response.jsonString(myResponse.asJson.spaces2)
}
```

In this example, we define a case class `MyResponse` that represents the data we want to serialize into JSON. We use the `.asJson` method to convert the response object into a JSON value, and then use `Response.jsonString` to create an HTTP response with the JSON payload.

**Working with JSON Data Structures**

`zio-json` provides a type-safe API for working with JSON data structures. You can use case classes or ADTs (Algebraic Data Types) to represent JSON objects and their fields. By deriving the necessary JSON codecs using annotations or manually defining them, you can ensure type safety during JSON parsing and serialization.

Here's an example of how you can define a case class with nested objects and use it for JSON handling:

```scala
import zio.http._
import zio.json._
import zio._

case class Address(city: String, country: String)
case class Person(name: String, age: Int, address: Address)

object Person {
  implicit val addressCodec: JsonCodec[Address] = DeriveJsonCodec.gen[Address]
  implicit val personCodec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
}

val httpApp: HttpApp[Any, Throwable] = Http.collectM {
  case request @ Method.POST -> Root / "api" / "endpoint" =>
    request
      .asJsonDecode[Person]
      .flatMap { person =>
        // Handle the parsed JSON request
        // person will be an instance of Person with nested Address object
        // ...
        // Return an HTTP response


        ZIO.succeed(Response.text("Request handled successfully"))
      }
}
```

In this example, we define case classes `Address` and `Person` to represent a nested JSON structure. We derive the necessary JSON codecs using `DeriveJsonCodec.gen`. This allows ZIO HTTP to automatically handle the parsing and serialization of these objects.

**Summary**

ZIO HTTP provides seamless integration with `zio-json`, allowing you to easily handle JSON requests and responses in a type-safe manner. By using case classes or ADTs and deriving the necessary JSON codecs, you can ensure proper parsing and serialization of JSON data structures.