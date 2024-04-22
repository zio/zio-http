---
id: multipart-form-data
title: "How to Handle Multipart Form Data"
---

Handling multipart form data is a common task in web development, especially when dealing with file uploads or complex form submissions. This guide shows how to handle multipart form data in Scala using the ZIO HTTP library.

## Defining the Route

First, we need to define a route that will handle the multipart form data. In the example below, we define a POST route at `/upload`:

```scala
private val app: HttpApp[Any] =
  Routes(
    Method.POST / "upload" ->
      handler { (req: Request) =>
        // Handle multipart form data here
      }
  ).sandbox.toHttpApp
```

## Checking the Content Type

Before processing the request body, we should check if the `Content-Type` header is set to `multipart/form-data`. If not, we can return a `404 Not Found` response:

```scala
if (req.header(Header.ContentType).exists(_.mediaType == MediaType.multipart.`form-data`))
  // Process multipart form data
else ZIO.succeed(Response(status = Status.NotFound))
```

## Decoding the Request Body

To decode the request body as multipart form data, we use the `asMultipartForm` method:

```scala
for {
  form <- req.body.asMultipartForm
    .mapError(ex =>
      Response(
        Status.InternalServerError,
        body = Body.fromString(s"Failed to decode body as multipart/form-data (${ex.getMessage}"),
      )
    )
  // Process form data here
} yield response
```

If the decoding fails, we return a `500 Internal Server Error` response with the error message.

## Processing Form Fields

After successfully decoding the request body, we can access the form fields using the `get` method on the `Form` instance. In this example, we look for a field named `"file"` and expect it to be a binary file:

```scala
response <- form.get("file") match {
  case Some(file) =>
    file match {
      case FormField.Binary(_, data, contentType, transferEncoding, filename) =>
        ZIO.succeed(
          Response.text(
            s"Received ${data.length} bytes of $contentType filename $filename and transfer encoding $transferEncoding",
          )
        )
      case _ =>
        ZIO.fail(
          Response(Status.BadRequest, body = Body.fromString("Parameter 'file' must be a binary file"))
        )
    }
  case None =>
    ZIO.fail(Response(Status.BadRequest, body = Body.fromString("Missing 'file' from body")))
}
```

If the `"file"` field is missing or not a binary file, we return a `400 Bad Request` response with an appropriate error message.

## Running the Server and Client

The provided code also includes examples of running the server and a client that sends a multipart form data request to the server:

```scala
private def program: ZIO[Client with Server with Scope, Throwable, Unit] =
  for {
    port <- Server.install(app)
    _ <- ZIO.logInfo(s"Server started on port $port")
    client <- ZIO.service[Client]
    response <- client
      .host("localhost")
      .port(port)
      .post("/upload")(
        Body.fromMultipartForm(
          Form(
            FormField.binaryField(
              "file",
              Chunk.fromArray("Hello, world!".getBytes),
              MediaType.application.`octet-stream`,
              filename = Some("hello.txt"),
            ),
          ),
          Boundary("AaB03x"),
        ),
      )
    responseBody <- response.body.asString
    _ <- ZIO.logInfo(s"Response: [${response.status}] $responseBody")
    _ <- ZIO.never
  } yield ()
```

This example demonstrates how to send a multipart form data request with a binary file field named `"file"` using the `Body.fromMultipartForm` method.

By following this guide, you should now be able to handle multipart form data in your ZIO HTTP applications, including processing file uploads and other form fields.