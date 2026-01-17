package zio.http.gen.smithy

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes._
import software.amazon.smithy.model.traits._
import zio.http.gen.scala.Code
import zio.http.Method
import zio.http.Status
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object SmithyEndpointGen {
  def fromSmithy(model: Model): Code.Files = {
    val serviceShapes = model.shapes(classOf[ServiceShape]).iterator().asScala.toList

    val files = serviceShapes.flatMap { service =>
      // For each service, we generate endpoints
      // We might need a config to decide package name, etc.
      // For now, let's derive it from namespace

      val namespace = service.getId.getNamespace
      val serviceName = service.getId.getName

      val operations = service.getOperations.asScala.map(id => model.getShape(id).get.asOperationShape().get)

      val endpoints = operations.map { op =>
        val opName = op.getId.getName
        val httpTrait = op.getTrait(classOf[HttpTrait]).toScala

        val method = httpTrait.map(t => Method.fromString(t.getMethod)).getOrElse(Method.GET) // Default or error?
        val uriPattern = httpTrait.map(_.getUri.toString).getOrElse("/")

        val segments = parsePath(uriPattern, op, model)

        // Input Shape
        val inputShapeId = op.getInputShape
        val inputShape = model.getShape(inputShapeId).toScala
        val (inCode, inImports) = inputShape.map(s => shapeToInCode(s, model)).getOrElse(Code.InCode("Unit") -> Nil)

        // Output Shape
        val outputShapeId = op.getOutputShape
        val outputShape = model.getShape(outputShapeId).toScala
        val (outCodes, outImports) = outputShape.map(s => shapeToOutCode(s, model)).getOrElse(List(Code.OutCode("Unit", Status.Ok)) -> Nil)

        // Query Parameters
        val queryParams = inputShape.flatMap(_.asStructureShape().toScala).map { structure =>
          structure.getAllMembers.asScala.values.flatMap { member =>
            member.getTrait(classOf[HttpQueryTrait]).toScala.map { trait =>
               val queryName = trait.getValue
               val codecType = shapeToCodecType(model.getShape(member.getTarget).get)
               // TODO: handle lists, optional
               Code.QueryParamCode(queryName, codecType)
            }
          }.toSet
        }.getOrElse(Set.empty)

        // Headers
        val headers = inputShape.flatMap(_.asStructureShape().toScala).map { structure =>
           structure.getAllMembers.asScala.values.flatMap { member =>
             member.getTrait(classOf[HttpHeaderTrait]).toScala.map { trait =>
                Code.HeaderCode(trait.getValue)
             }
           }.toList
        }.map(Code.HeadersCode(_)).getOrElse(Code.HeadersCode.empty)

        // Fix InCode to exclude members bound to path/query/headers
        // For now, simpler to just use the full input structure if it's a body
        // But if @httpPayload is present, use that.
        // If not, and no members are bound to http traits, use whole structure?
        // Smithy rule: implicit body if no @httpPayload and no bindings?
        // For now, keeping InCode simple.

        val endpointCode = Code.EndpointCode(
          method = method,
          pathPatternCode = Code.PathPatternCode(segments),
          queryParamsCode = queryParams,
          headersCode = headers,
          inCode = inCode,
          outCodes = outCodes,
          errorsCode = Nil, // TODO: Handle errors
          authTypeCode = None
        )

        Code.Field(opName) -> endpointCode
      }.toMap

      val serviceObj = Code.Object(
        name = serviceName,
        extensions = Nil,
        schema = None,
        endpoints = endpoints,
        objects = Nil,
        caseClasses = Nil,
        enums = Nil
      )

      val serviceFile = Code.File(
        path = namespace.split('.').toList :+ s"$serviceName.scala",
        pkgPath = namespace.split('.').toList,
        imports = (Code.Import("zio.http._") :: Code.Import("zio.http.endpoint._") :: Code.Import("zio.schema._") :: Nil).distinct,
        objects = List(serviceObj),
        caseClasses = Nil,
        enums = Nil
      )

      val dataModels = generateDataModels(service, model)

      serviceFile :: dataModels
    }


    Code.Files(files)
  }

  private def parsePath(pattern: String, op: OperationShape, model: Model): List[Code.PathSegmentCode] = {
    // Smithy path pattern example: /foo/{bar}/{baz}
    // We split by / and check if segment is wrapped in {}
    val segments = pattern.stripPrefix("/").split("/").filter(_.nonEmpty).toList
    segments.map { segment =>
      if (segment.startsWith("{") && segment.endsWith("}")) {
        val paramName = segment.substring(1, segment.length - 1)
        val labelMember = findLabelMember(op.getInputShape, paramName, model)
        val paramType = labelMember.map(m => shapeToCodecType(model.getShape(m.getTarget).get)).getOrElse(Code.CodecType.String)
        Code.PathSegmentCode(paramName, paramType)
      } else {
        Code.PathSegmentCode(segment)
      }
    }
  }

  private def findLabelMember(inputShapeId: ShapeId, paramName: String, model: Model): Option[MemberShape] = {
    model.getShape(inputShapeId).flatMap(_.asStructureShape().toScala).flatMap { structure =>
       structure.getAllMembers.asScala.values.find { member =>
         member.getMemberName == paramName && member.hasTrait(classOf[HttpLabelTrait])
       }
    }.toScala
  }

  private def shapeToCodecType(shape: Shape): Code.CodecType = {
     shape.getType match {
       case ShapeType.STRING => Code.CodecType.String
       case ShapeType.INTEGER => Code.CodecType.Int
       case ShapeType.LONG => Code.CodecType.Long
       case ShapeType.BOOLEAN => Code.CodecType.Boolean
       // Use String for others for now or fallback
       case _ => Code.CodecType.String
     }
  }

  private def shapeToInCode(shape: Shape, model: Model): (Code.InCode, List[Code.Import]) = {
    // Ideally we generate a case class for the structure and refer to it.
    // For now returning Unit if empty or simple type
    if (shape.getId.getName == "Unit") Code.InCode("Unit") -> Nil
    else Code.InCode(shape.getId.getName) -> Nil // Assuming generated shape name matches
  }


  private def generateDataModels(service: ServiceShape, model: Model): List[Code.File] = {
    val walker = new software.amazon.smithy.model.neighbor.Walker(model)
    val connectedShapes = walker.walkShapes(service).asScala.toSet
    val structures = connectedShapes.collect { case s: StructureShape if s.getId.getNamespace == service.getId.getNamespace => s }

    structures.map { structure =>
      val name = structure.getId.getName
      val fields = structure.getAllMembers.asScala.map { case (memberName, memberShape) =>
          val fieldType = shapeToScalaType(model.getShape(memberShape.getTarget).get, model)
          Code.Field(memberName, fieldType)
      }.toList

      Code.File(
        path = structure.getId.getNamespace.split('.').toList :+ s"$name.scala",
        pkgPath = structure.getId.getNamespace.split('.').toList,
        imports = (Code.Import("zio.schema._") :: Code.Import("zio.json._") :: Nil),
        objects = Nil,
        caseClasses = List(Code.CaseClass(name, fields, Some(Code.Object.schemaCompanion(name)), Nil)),
        enums = Nil
      )
    }.toList
  }

  private def shapeToScalaType(shape: Shape, model: Model): Code.ScalaType = {
    shape.getType match {
      case ShapeType.STRING => Code.Primitive.ScalaString
      case ShapeType.INTEGER => Code.Primitive.ScalaInt
      case ShapeType.LONG => Code.Primitive.ScalaLong
      case ShapeType.BOOLEAN => Code.Primitive.ScalaBoolean
      case ShapeType.STRUCTURE => Code.TypeRef(shape.getId.getName)
      case ShapeType.LIST =>
        val list = shape.asListShape().get
        Code.Collection.Seq(shapeToScalaType(model.getShape(list.getMember.getTarget).get, model), nonEmpty = false)
      case _ => Code.Primitive.ScalaString // Fallback
    }
  }
