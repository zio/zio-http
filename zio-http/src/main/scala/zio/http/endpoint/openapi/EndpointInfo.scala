package zio.http.endpoint.openapi

import zio.Chunk
import zio.http.endpoint.openapi.StringUtils.camelCase
import zio.http.{Method, Status}

final case class EndpointInfo(
  path: Chunk[PathItem],
  operationId: String,
  method: Method,
  summary: Option[String],
  parameters: Chunk[ParameterInfo],
  responses: Chunk[ResponseInfo],
) {

  /**
   * This method transforms the EndpointInfo object into a string representation
   * of a [[zio.http.endpoint.Endpoint]].
   *
   * The output will look something like this:
   * {{{
   * val name =
   *   Endpoint(GET / "path" / param)
   *     .query(...)
   *     .header(...)
   *     .outError[E](...)
   *     .out[A](...)
   * }}}
   */
  def toEndpointDefinition: String = {
    val endpointIdentifier = camelCase(operationId)
    val pathString         = path.map(_.toPathCodec).mkString(" / ")
    val baseDefinition     = s"""Endpoint(${method.name} / $pathString)"""

    val withQueryParams = parameters.collect {
      case param if param.in == ParameterLocation.Query =>
        s"""QueryCodec.queryAs[${param.schema.renderType}]("${param.name}")"""
    }
      .foldLeft(baseDefinition) { case (acc, queryString) =>
        s"$acc.query($queryString)"
      }

    val withHeaders = parameters.collect {
      case param if param.in == ParameterLocation.Header =>
        s"""HeaderCodec.name[${param.schema.renderType}]("${param.name}")"""
    }
      .foldLeft(withQueryParams) { case (acc, headerString) =>
        s"$acc.header($headerString)"
      }

    val withOut = responses.flatMap { response =>
      response.content.map { content =>
        val status    = codeToStatus(response.code)
        val outMethod = if (status.isError) "outError" else "out"
        s""".$outMethod[${content.schema.renderType}](Status.$status)"""
      }
    }
      .foldLeft(withHeaders) { case (acc, outString) =>
        s"$acc$outString"
      }

    s"val $endpointIdentifier = $withOut"
  }

  private def codeToStatus(code: Int): Status =
    Status.fromInt(code).getOrElse(Status.Custom(code))

}

object EndpointInfo {
  def fromPathItemObject(pathString: String, pathItemObject: PathItemObject): Chunk[EndpointInfo] =
    Chunk(
      pathItemObject.get.map(Method.GET -> _),
      pathItemObject.put.map(Method.PUT -> _),
      pathItemObject.post.map(Method.POST -> _),
      pathItemObject.delete.map(Method.DELETE -> _),
      pathItemObject.options.map(Method.OPTIONS -> _),
      pathItemObject.head.map(Method.HEAD -> _),
      pathItemObject.patch.map(Method.PATCH -> _),
      pathItemObject.trace.map(Method.TRACE -> _),
    ).flatten.map { case (method, operationObject) =>
      fromOperationObject(pathString, method, operationObject)
    }

  def fromOperationObject(pathString: String, method: Method, operationObject: OperationObject): EndpointInfo = {
    val params      = operationObject.parameters.getOrElse(Chunk.empty).map { param =>
      ParameterInfo.fromParameterObject(param)
    }
    val path        = PathItem.parse(pathString, params.map(p => p.name -> p).toMap)
    val operationId = operationObject.operationId.getOrElse(defaultOperationId(method, path))
    val responses   = Chunk.from(operationObject.responses.map { case (code, response) =>
      ResponseInfo(
        code = code.toIntOption.getOrElse(500),
        description = response.description,
        content = response.content.getOrElse(Map.empty).headOption.map { case (mediaType, content) =>
          ContentInfo(
            mediaType = mediaType,
            schema = EndpointGenerator.parseSchemaType(content.schema),
          )
        },
      )
    })

    EndpointInfo(
      path = path,
      operationId = operationId,
      method = method,
      summary = operationObject.summary,
      parameters = params,
      responses = responses,
    )
  }

  private def defaultOperationId(method: Method, path: Chunk[PathItem]) =
    method.name.toLowerCase.capitalize +
      path.collect { case PathItem.Static(value) => value }
        .map(_.capitalize)
        .mkString("")
}

final case class ResponseInfo(
  code: Int,
  description: String,
  content: Option[ContentInfo],
)

final case class ContentInfo(
  mediaType: String,
  schema: ApiSchemaType,
)

sealed trait PathItem extends Product with Serializable {
  def toPathCodec: String = this match {
    case PathItem.Static(value)        => s"\"$value\""
    case PathItem.Param(parameterInfo) =>
      val name        = parameterInfo.name
      val constructor = parameterInfo.schema.unwrapOptional match {
        case ApiSchemaType.TString  => s"PathCodec.string(\"$name\")"
        case ApiSchemaType.TInt     => s"PathCodec.int(\"$name\")"
        case ApiSchemaType.TLong    => s"PathCodec.long(\"$name\")"
        case ApiSchemaType.TBoolean => s"PathCodec.boolean(\"$name\")"
        case _ => throw new Exception(s"Unsupported path param type for $name: ${parameterInfo.schema}")
      }
      if (parameterInfo.required || parameterInfo.schema.isOptional)
        constructor
      else
        s"$constructor.optional"

  }

}

object PathItem {
  final case class Static(value: String)               extends PathItem
  final case class Param(parameterInfo: ParameterInfo) extends PathItem

  def parse(pathString: String, paramInfos: Map[String, ParameterInfo]): Chunk[PathItem] = {
    val pathItems = Chunk.fromArray(pathString.stripPrefix("/").split("/"))
    pathItems.map {
      case s"{${paramName}}" =>
        Param(
          paramInfos.getOrElse(
            paramName,
            throw new Exception(s"Could not find param info for param $paramName"),
          ),
        )

      case s => Static(s)
    }
  }
}

final case class ParameterInfo(
  name: String,
  in: ParameterLocation,
  required: Boolean,
  schema: ApiSchemaType,
  description: Option[String],
)

object ParameterInfo {
  def fromParameterObject(parameterObject: ParameterObject): ParameterInfo = {
    val schema = EndpointGenerator.parseSchemaType(parameterObject.schema)
    ParameterInfo(
      parameterObject.name,
      parameterObject.in,
      parameterObject.required.getOrElse(false),
      schema,
      parameterObject.description,
    )
  }
}
