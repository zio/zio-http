package zio.http.datastar

import scala.language.implicitConversions

import zio.Chunk

import zio.http._
import zio.http.codec._
import zio.http.endpoint.{AuthType, Endpoint}
import zio.http.template2._

/**
 * Represents a Datastar request action that can be used with data-on-*
 * attributes to make HTTP requests to endpoints.
 */
sealed trait EndpointRequest {
  def method: Method
  def url: String
  def headers: Map[String, String]
  def includeHeaders: Boolean
  def onlyIfMissing: Boolean

  /**
   * Returns the Datastar action expression for this request.
   * Example: @get('/api/users')
   */
  def toActionExpression: Js

  /**
   * Returns the full Datastar fetch expression with all options.
   * Example: @get('/api/users', headers: {})
   */
  def toFetchExpression: Js = {
    val parts = scala.collection.mutable.ListBuffer[String]()
    
    if (headers.nonEmpty) {
      val headerStr = headers.map { case (k, v) => s"'$k': '$v'" }.mkString("{", ", ", "}")
      parts += s"headers: $headerStr"
    }
    
    if (includeHeaders) {
      parts += "includeHeaders: true"
    }
    
    if (onlyIfMissing) {
      parts += "onlyIfMissing: true"
    }

    val options = if (parts.nonEmpty) s", ${parts.mkString(", ")}" else ""
    js"@${method.toString().toLowerCase}('$url'$options)"
  }

  /**
   * Sets whether to include request headers from the current page.
   */
  def withIncludeHeaders(include: Boolean = true): EndpointRequest

  /**
   * Sets whether to only fetch if the signal is missing.
   */
  def withOnlyIfMissing(only: Boolean = true): EndpointRequest

  /**
   * Adds a custom header to the request.
   */
  def withHeader(key: String, value: String): EndpointRequest

  /**
   * Adds multiple custom headers to the request.
   */
  def withHeaders(newHeaders: Map[String, String]): EndpointRequest
}

object EndpointRequest {

  private final case class EndpointRequestImpl(
    method: Method,
    url: String,
    headers: Map[String, String] = Map.empty,
    includeHeaders: Boolean = false,
    onlyIfMissing: Boolean = false,
  ) extends EndpointRequest {

    override def toActionExpression: Js = js"@${method.toString().toLowerCase}('$url')"

    override def withIncludeHeaders(include: Boolean): EndpointRequest =
      copy(includeHeaders = include)

    override def withOnlyIfMissing(only: Boolean): EndpointRequest =
      copy(onlyIfMissing = only)

    override def withHeader(key: String, value: String): EndpointRequest =
      copy(headers = headers + (key -> value))

    override def withHeaders(newHeaders: Map[String, String]): EndpointRequest =
      copy(headers = headers ++ newHeaders)
  }

  /**
   * Creates a Datastar request action from an endpoint definition.
   */
  def fromEndpoint[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
  ): EndpointRequestBuilder[PathInput, Input, Err, Output, Auth] =
    EndpointRequestBuilder(endpoint)

  /**
   * Creates a GET request to the specified URL.
   */
  def get(url: String): EndpointRequest =
    EndpointRequestImpl(Method.GET, url)

  /**
   * Creates a POST request to the specified URL.
   */
  def post(url: String): EndpointRequest =
    EndpointRequestImpl(Method.POST, url)

  /**
   * Creates a PUT request to the specified URL.
   */
  def put(url: String): EndpointRequest =
    EndpointRequestImpl(Method.PUT, url)

  /**
   * Creates a PATCH request to the specified URL.
   */
  def patch(url: String): EndpointRequest =
    EndpointRequestImpl(Method.PATCH, url)

  /**
   * Creates a DELETE request to the specified URL.
   */
  def delete(url: String): EndpointRequest =
    EndpointRequestImpl(Method.DELETE, url)

  /**
   * Builder for creating endpoint requests with parameter substitution.
   */
  final case class EndpointRequestBuilder[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    headers: Map[String, String] = Map.empty,
    includeHeaders: Boolean = false,
    onlyIfMissing: Boolean = false,
  ) {

    /**
     * Builds the request with path and query parameters substituted.
     * Path parameters are extracted from the endpoint's route pattern.
     */
    def build(pathParams: PathInput = null.asInstanceOf[PathInput]): EndpointRequest = {
      val method = extractMethod(endpoint.route)
      val path   = buildPath(endpoint.route, pathParams)

      EndpointRequestImpl(
        method = method,
        url = path,
        headers = headers,
        includeHeaders = includeHeaders,
        onlyIfMissing = onlyIfMissing,
      )
    }

    /**
     * Builds the request with path parameters as signals (for dynamic substitution).
     */
    def buildWithSignals(pathParams: String => String = identity): EndpointRequest = {
      val method = extractMethod(endpoint.route)
      val path   = buildPathWithSignals(endpoint.route, pathParams)

      EndpointRequestImpl(
        method = method,
        url = path,
        headers = headers,
        includeHeaders = includeHeaders,
        onlyIfMissing = onlyIfMissing,
      )
    }

    /**
     * Sets whether to include request headers from the current page.
     */
    def withIncludeHeaders(include: Boolean = true): EndpointRequestBuilder[PathInput, Input, Err, Output, Auth] =
      copy(includeHeaders = include)

    /**
     * Sets whether to only fetch if the signal is missing.
     */
    def withOnlyIfMissing(only: Boolean = true): EndpointRequestBuilder[PathInput, Input, Err, Output, Auth] =
      copy(onlyIfMissing = only)

    /**
     * Adds a custom header to the request.
     */
    def withHeader(key: String, value: String): EndpointRequestBuilder[PathInput, Input, Err, Output, Auth] =
      copy(headers = headers + (key -> value))

    /**
     * Adds multiple custom headers to the request.
     */
    def withHeaders(newHeaders: Map[String, String]): EndpointRequestBuilder[PathInput, Input, Err, Output, Auth] =
      copy(headers = headers ++ newHeaders)

    private def extractMethod(route: RoutePattern[_]): Method = {
      route.method
    }

    private def buildPath(route: RoutePattern[_], pathParams: PathInput): String = {
      if (pathParams == null) {
        // Build path without parameter substitution (will use placeholder format)
        "/" + route.pathCodec.segments
          .map { segment =>
            val segmentStr = segment.toString
            if (segmentStr.startsWith("Literal(")) {
              extractLiteralValue(segmentStr)
            } else {
              s"{${extractSegmentName(segmentStr)}}"
            }
          }
          .mkString("/")
      } else {
        // Build path with actual parameter values
        val formatted = endpoint.route.asInstanceOf[RoutePattern[PathInput]].format(pathParams)
        formatted match {
          case Right(path) => "/" + path.encode.dropWhile(_ == '/')
          case Left(_)     => "/" // fallback to root if formatting fails
        }
      }
    }

    private def buildPathWithSignals(route: RoutePattern[_], signalMapper: String => String): String = {
      "/" + route.pathCodec.segments
        .map { segment =>
          val segmentStr = segment.toString
          if (segmentStr.startsWith("Literal(")) {
            extractLiteralValue(segmentStr)
          } else {
            val name = extractSegmentName(segmentStr)
            s"$${${signalMapper(name)}}"
          }
        }
        .mkString("/")
    }

    private def extractLiteralValue(literalStr: String): String = {
      // Extract the string value from Literal(...)
      literalStr.stripPrefix("Literal(").stripSuffix(")").trim
    }

    private def extractSegmentName(segmentStr: String): String = {
      // Extract parameter name from segment description
      // This is a simplified approach - may need adjustment based on actual PathCodec structure
      val name = if (segmentStr.contains("(")) {
        segmentStr.substring(segmentStr.indexOf("(") + 1, segmentStr.lastIndexOf(")"))
      } else {
        segmentStr
      }
      // Remove quotes if present
      name.replaceAll("\"", "").replaceAll("'", "")
    }
  }

  implicit def endpointRequestToJs(request: EndpointRequest): Js =
    request.toActionExpression
}

