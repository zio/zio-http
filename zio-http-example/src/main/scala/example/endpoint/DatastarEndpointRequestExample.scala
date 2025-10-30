package example.endpoint

import zio._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

/**
 * This example demonstrates building Datastar fetch expressions from ZIO HTTP
 * Endpoint definitions.
 *
 * Run this example from the root project: {{{ sbt "zioHttpExample/runMain
 * example.endpoint.DatastarEndpointRequestExample" }}}
 *
 * Then visit: http://localhost:8080/
 */
object DatastarEndpointRequestExample extends ZIOAppDefault {

  // Home page with examples
  def renderHomePage(): String =
    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |  <title>Datastar Endpoint Request Example</title>
       |  <style>
       |    body { font-family: Arial, sans-serif; margin: 40px; }
       |    h1 { color: #333; }
       |    code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; }
       |    pre { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; }
       |  </style>
       |</head>
       |<body>
       |  <h1>Datastar Endpoint Request Example</h1>
       |  
       |  <h2>What this demonstrates:</h2>
       |  <p>This example shows how to generate Datastar fetch expressions from ZIO HTTP Endpoint definitions.</p>
       |  
       |  <h2>Example Code:</h2>
       |  <pre><code>import zio.http._
       |import zio.http.endpoint._
       |import zio.http.codec.PathCodec
       |
       |// Define an endpoint
       |val endpoint = Endpoint(RoutePattern.GET / "api" / "users")
       |
       |// Generate Datastar request expression using toDatastarRequest
       |val request = endpoint.toDatastarRequest.build()
       |// Result: @get('/api/users')
       |
       |// With path parameters - substitute with actual values
       |val userEndpoint = Endpoint(RoutePattern.GET / "api" / "users" / PathCodec.int("id"))
       |val userRequest = userEndpoint.toDatastarRequest.build(42)
       |// Result: @get('/api/users/42')
       |
       |// With path parameters - use signals for dynamic values
       |val deleteRequest = userEndpoint.toDatastarRequest.buildWithSignals()
       |// Result: @get('/api/users/${'$'}{id}')
       |
       |// Using dataFetch helper (shorthand)
       |import zio.http.datastar._
       |val fetchRequest = dataFetch.get("/api/users")
       |// Result: @get('/api/users')
       |
       |// With options
       |val advancedRequest = endpoint.toDatastarRequest
       |  .withIncludeHeaders(true)
       |  .withHeader("X-Custom", "value")
       |  .withOnlyIfMissing(true)
       |  .build()
       |// Result: @get('/api/users', {headers: {...}, onlyIfMissing: true})</code></pre>
       |
       |  <h2>Available Methods:</h2>
       |  <ul>
       |    <li><code>toDatastarRequest</code> - Main method to convert Endpoint to Datastar request</li>
       |    <li><code>build()</code> - Generate request without path parameters</li>
       |    <li><code>build(params)</code> - Generate request with actual path parameter values</li>
       |    <li><code>buildWithSignals()</code> - Generate request with signal placeholders for dynamic values</li>
       |    <li><code>withHeader(key, value)</code> - Add custom headers</li>
       |    <li><code>withIncludeHeaders(true)</code> - Include all headers in request</li>
       |    <li><code>with OnlyIfMissing(true)</code> - Only fetch if data doesn't exist</li>
       |  </ul>
       |
       |  <h2>HTTP Methods Supported:</h2>
       |  <ul>
       |    <li><code>GET</code> - generates <code>@get(...)</code></li>
       |    <li><code>POST</code> - generates <code>@post(...)</code></li>
       |    <li><code>PUT</code> - generates <code>@put(...)</code></li>
       |    <li><code>PATCH</code> - generates <code>@patch(...)</code></li>
       |    <li><code>DELETE</code> - generates <code>@delete(...)</code></li>
       |  </ul>
       |
       |  <h2>Usage in HTML with Datastar:</h2>
       |  <pre><code>&lt;button data-on-click="@get('/api/users')"&gt;Load Users&lt;/button&gt;
       |&lt;form data-on-submit.prevent="@post('/api/users')"&gt;...&lt;/form&gt;
       |&lt;button data-on-click="@delete('/api/users/${'$'}{userId}')"&gt;Delete&lt;/button&gt;</code></pre>
       |
       |</body>
       |</html>
       |""".stripMargin

  val homeRoute = Routes(
    Method.GET / "" -> handler(
      Response(
        body = Body.fromString(renderHomePage()),
        headers = Headers(Header.ContentType(MediaType.text.html)),
      ),
    ),
  )

  def run = Server.serve(homeRoute).provide(Server.default)
}
