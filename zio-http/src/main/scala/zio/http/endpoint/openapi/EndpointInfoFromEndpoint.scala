package zio.http.endpoint.openapi

import zio.{Chunk, http}

import zio.schema.{Schema, StandardType}

import zio.http.Method
import zio.http.codec.HttpCodecType.RequestType
import zio.http.codec._
import zio.http.endpoint.openapi.StringUtils.camelCase
import zio.http.endpoint.{Endpoint, EndpointMiddleware}

object EndpointInfoFromEndpoint {
  def fromEndpoint[PathInput, Input, Error, Output, Middleware <: EndpointMiddleware](
    endpoint: Endpoint[PathInput, Input, Error, Output, Middleware],
  ): EndpointInfo = {
    val input: HttpCodec[RequestType, Input] = endpoint.input
    val path: Chunk[PathItem]                = getPath(input)
    val method    = getMethod(input).getOrElse(throw new Exception(s"Could not find method for $endpoint"))
    val output    = getOutputs(endpoint.output)
    val responses = output.map { schemaType =>
      ResponseInfo(
        200,
        "OK",
        Some(
          ContentInfo(
            "application/json",
            schemaType,
          ),
        ),
      )
    }

    // Construct operationId
    val pathSegments = path.map {
      case PathItem.Static(value) => value
      case PathItem.Param(_)      => ""
    }
    val operationId  = camelCase((method.name +: pathSegments).mkString(" "))

    val pathParameters =
      path.collect { case PathItem.Param(parameterInfo) => parameterInfo }

    EndpointInfo(
      path = path,
      operationId = operationId,
      method = method,
      summary = None,
      parameters = pathParameters,
      responses = responses,
      requestBody = None,
    )
  }

  def getPath(codec: HttpCodec[_, _]): Chunk[PathItem] =
    codec match {
      case atom: HttpCodec.Atom[_, _]                    =>
        atom match {
          case HttpCodec.Path(pathCodec, _) =>
            getPath(pathCodec)
          case _                            => Chunk.empty
        }
      case HttpCodec.WithDoc(in, doc)                    => getPath(in)
      case HttpCodec.WithExamples(in, examples)          => getPath(in)
      case HttpCodec.TransformOrFail(api, f, g)          => getPath(api)
      case HttpCodec.Empty                               => Chunk.empty
      case HttpCodec.Halt                                => Chunk.empty
      case HttpCodec.Combine(left, right, inputCombiner) => getPath(left) ++ getPath(right)
      case HttpCodec.Fallback(left, right)               => getPath(left) ++ getPath(right)
    }

  def getPath(pathCodec: PathCodec[_]): Chunk[PathItem] =
    pathCodec match {
      case PathCodec.Segment(segment, doc)              =>
        segment match {
          case SegmentCodec.Empty(doc)          => Chunk.empty
          case SegmentCodec.Literal(value, doc) => Chunk(PathItem.Static(value))
          case SegmentCodec.BoolSeg(name, doc)  => Chunk(fromSegment(name, doc, ApiSchemaType.TBoolean))
          case SegmentCodec.IntSeg(name, doc)   => Chunk(fromSegment(name, doc, ApiSchemaType.TInt))
          case SegmentCodec.LongSeg(name, doc)  => Chunk(fromSegment(name, doc, ApiSchemaType.TLong))
          case SegmentCodec.Text(name, doc)     => Chunk(fromSegment(name, doc, ApiSchemaType.TString))
          case SegmentCodec.UUID(name, doc)     => Chunk(fromSegment(name, doc, ApiSchemaType.TString))
          case SegmentCodec.Trailing(doc)       => Chunk.empty
        }
      case PathCodec.Concat(left, right, combiner, doc) =>
        getPath(left) ++ getPath(right)
    }

  private def fromSegment(name: String, doc: Doc, tpe: ApiSchemaType) = {
    val description = if (doc.toCommonMark.trim.isEmpty) None else Some(doc.toCommonMark)
    PathItem.Param(
      ParameterInfo(
        name = name,
        in = ParameterLocation.Path,
        required = true,
        schema = tpe,
        description = description,
      ),
    )
  }

  def getMethod(
    httpCodec: HttpCodec[_, _],
  ): Option[Method] = {
    httpCodec match {
      case atom: HttpCodec.Atom[_, _]                    =>
        atom match {
          case HttpCodec.Method(codec, _) =>
            codec.asInstanceOf[http.codec.SimpleCodec[_, _]] match {
              case SimpleCodec.Specified(value: Method) => Some(value)
              case SimpleCodec.Unspecified()            => None
            }
          case _                          => None
        }
      case HttpCodec.WithDoc(in, doc)                    => getMethod(in)
      case HttpCodec.WithExamples(in, examples)          => getMethod(in)
      case HttpCodec.TransformOrFail(api, f, g)          => getMethod(api)
      case HttpCodec.Empty                               => None
      case HttpCodec.Halt                                => None
      case HttpCodec.Combine(left, right, inputCombiner) => getMethod(left) orElse getMethod(right)
      case HttpCodec.Fallback(left, right)               => getMethod(left) orElse getMethod(right)
    }
  }

  def getOutputs(httpCodec: HttpCodec[_, _]): Chunk[ApiSchemaType] =
    httpCodec match {
      case atom: HttpCodec.Atom[_, _]           =>
        atom match {
          case HttpCodec.Content(schema, mediaType, name, index)       =>
            Chunk(apiSchemaTypeFromSchema(schema))
          case HttpCodec.ContentStream(schema, mediaType, name, index) =>
            Chunk(apiSchemaTypeFromSchema(schema))
          case _                                                       => Chunk.empty
        }
      case HttpCodec.WithDoc(in, doc)           => getOutputs(in)
      case HttpCodec.WithExamples(in, examples) => getOutputs(in)
      case HttpCodec.TransformOrFail(api, _, _) => getOutputs(api)
      case HttpCodec.Empty                      => Chunk.empty
      case HttpCodec.Halt                       => Chunk.empty
      case HttpCodec.Combine(left, right, _)    => getOutputs(left) ++ getOutputs(right)
      case HttpCodec.Fallback(left, right)      => getOutputs(left) ++ getOutputs(right)
    }

  def apiSchemaTypeFromSchema(schema: Schema[_]): ApiSchemaType =
    schema match {
      case enum: Schema.Enum[_]                                  =>
        ???
      case record: Schema.Record[_]                              =>
        val params: Map[String, ApiSchemaType] = record.fields.map { field =>
          field.name -> apiSchemaTypeFromSchema(field.schema)
        }.toMap
        ApiSchemaType.Object(Chunk(record.id.name), params)
      case collection: Schema.Collection[_, _]                   =>
        collection match {
          case Schema.Sequence(elementSchema, fromChunk, toChunk, annotations, identity) =>
            ApiSchemaType.Array(apiSchemaTypeFromSchema(elementSchema))
          case Schema.Map(keySchema, valueSchema, annotations)                           =>
            ???
          case Schema.Set(elementSchema, annotations)                                    =>
            ApiSchemaType.Array(apiSchemaTypeFromSchema(elementSchema))
        }
      case Schema.Transform(schema, f, g, annotations, identity) =>
        apiSchemaTypeFromSchema(schema)
      case Schema.Primitive(standardType, _)                     =>
        standardType match {
          case StandardType.UnitType           => ApiSchemaType.TNull
          case StandardType.StringType         => ApiSchemaType.TString
          case StandardType.BoolType           => ApiSchemaType.TBoolean
          case StandardType.ByteType           => ApiSchemaType.TInt
          case StandardType.ShortType          => ApiSchemaType.TInt
          case StandardType.IntType            => ApiSchemaType.TInt
          case StandardType.LongType           => ApiSchemaType.TLong
          case StandardType.FloatType          => ApiSchemaType.TFloat
          case StandardType.DoubleType         => ApiSchemaType.TDouble
          case StandardType.BinaryType         => ApiSchemaType.TString
          case StandardType.CharType           => ApiSchemaType.TInt
          case StandardType.UUIDType           => ApiSchemaType.TString
          case StandardType.BigDecimalType     => ApiSchemaType.TDouble
          case StandardType.BigIntegerType     => ApiSchemaType.TLong
          case StandardType.DayOfWeekType      => ApiSchemaType.TString
          case StandardType.MonthType          => ApiSchemaType.TString
          case StandardType.MonthDayType       => ApiSchemaType.TString
          case StandardType.PeriodType         => ApiSchemaType.TString
          case StandardType.YearType           => ApiSchemaType.TString
          case StandardType.YearMonthType      => ApiSchemaType.TString
          case StandardType.ZoneIdType         => ApiSchemaType.TString
          case StandardType.ZoneOffsetType     => ApiSchemaType.TString
          case StandardType.DurationType       => ApiSchemaType.TString
          case StandardType.InstantType        => ApiSchemaType.TString
          case StandardType.LocalDateType      => ApiSchemaType.TString
          case StandardType.LocalTimeType      => ApiSchemaType.TString
          case StandardType.LocalDateTimeType  => ApiSchemaType.TString
          case StandardType.OffsetTimeType     => ApiSchemaType.TString
          case StandardType.OffsetDateTimeType => ApiSchemaType.TString
          case StandardType.ZonedDateTimeType  => ApiSchemaType.TString
        }
      case Schema.Optional(schema, annotations)                  =>
        ApiSchemaType.Optional(apiSchemaTypeFromSchema(schema))
      case Schema.Fail(message, annotations)                     => ???
      case Schema.Tuple2(left, right, annotations)               => ???
      case Schema.Either(left, right, annotations)               => ???
      case Schema.Lazy(schema0)                                  =>
        apiSchemaTypeFromSchema(schema0())
      case Schema.Dynamic(annotations)                           => ???
    }
}
