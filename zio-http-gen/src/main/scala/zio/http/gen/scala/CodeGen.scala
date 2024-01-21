package zio.http.gen.scala

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file._

object CodeGen {

  private val EndpointImports =
    List(
      Code.Import("zio.http._"),
      Code.Import("zio.http.endpoint._"),
      Code.Import("zio.http.codec._"),
    )

  def format(config: Path)(file: Path, content: String): String = {
    import org.scalafmt.interfaces.Scalafmt

    val scalafmt: Scalafmt = Scalafmt.create(this.getClass.getClassLoader)
    scalafmt.format(config, file, content)
  }

  def writeFiles(files: Code.Files, basePath: Path, basePackage: String, scalafmtPath: Option[Path]): Unit = {

    val formatCode = scalafmtPath.map(format(_: Path) _).getOrElse((_: Path, content: String) => content)

    val rendered = renderedFiles(files, basePackage)
    rendered.map { case (path, content) => path -> formatCode(Paths.get(path), content) }.foreach {
      case (path, content) =>
        val filePath = Paths.get(basePath.toString, path)
        Files.createDirectories(filePath.getParent)
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8), CREATE, TRUNCATE_EXISTING)
    }
  }

  def renderedFiles(files: Code.Files, basePackage: String): Map[String, String] =
    files.files.map { file =>
      val rendered = render(basePackage)(file)
      file.path.mkString("/") -> rendered
    }.toMap

  def render(basePackage: String)(structure: Code): String = structure match {
    case Code.Files(_) =>
      throw new Exception("Files should be rendered separately")

    case Code.File(_, path, imports, objects, caseClasses, enums) =>
      s"package $basePackage${if (path.exists(_.nonEmpty)) path.mkString(if (basePackage.isEmpty) "" else ".", ".", "")
        else ""}\n\n" +
        s"${imports.map(render(basePackage)).mkString("\n")}\n\n" +
        objects.map(render(basePackage)).mkString("\n") +
        caseClasses.map(render(basePackage)).mkString("\n") +
        enums.map(render(basePackage)).mkString("\n")

    case Code.Import.Absolute(path) =>
      s"import $path"

    case Code.Import.FromBase(path) =>
      s"import $basePackage.$path"

    case Code.Object(name, schema, endpoints, objects, caseClasses, enums) =>
      s"object $name {\n" +
        (if (endpoints.nonEmpty)
           (EndpointImports ++ (if (endpointWithChunk(endpoints)) List(Code.Import("zio.http._")) else Nil))
             .map(render(basePackage))
             .mkString("", "\n", "\n")
         else "") +
        endpoints.map { case (k, v) => s"${render(basePackage)(k)}=${render(basePackage)(v)}" }
          .mkString("\n") +
        (if (schema) s"\n\n implicit val codec: Schema[$name] = DeriveSchema.gen[$name]" else "") +
        "\n" + objects.map(render(basePackage)).mkString("\n") +
        "\n" + caseClasses.map(render(basePackage)).mkString("\n") +
        "\n" + enums.map(render(basePackage)).mkString("\n") +
        "\n}"

    case Code.CaseClass(name, fields, companionObject) =>
      s"case class $name(\n" +
        fields.map(render(basePackage)).mkString(",\n").replace("val ", " ") +
        "\n)" + companionObject.map(render(basePackage)).map("\n" + _).getOrElse("")

    case Code.Enum(name, cases, caseNames, discriminator, noDiscriminator, schema) =>
      val discriminatorAnnotation     =
        if (noDiscriminator) "@noDiscriminator\n" else ""
      val discriminatorNameAnnotation =
        if (discriminator.isDefined) s"""@discriminatorName("${discriminator.get}")\n""" else ""
      discriminatorAnnotation +
        discriminatorNameAnnotation +
        s"sealed trait $name\n" +
        s"object $name {\n" +
        (if (schema) s"\n\n implicit val codec: Schema[$name] = DeriveSchema.gen[$name]\n" else "") + {
          if (caseNames.nonEmpty) {
            cases
              .map(render(basePackage))
              .zipWithIndex
              .map { case (c, i) => s"""@caseName("${caseNames(i)}")\n$c""" }
              .mkString("\n")
          } else {
            cases.map(render(basePackage)).mkString("\n")
          }
        } +
        "\n}"

    case col: Code.Collection =>
      col match {
        case Code.Collection.Seq(elementType) =>
          s"Chunk[${render(basePackage)(elementType)}]"
        case Code.Collection.Set(elementType) =>
          s"Set[${render(basePackage)(elementType)}]"
        case Code.Collection.Map(elementType) =>
          s"Map[String, ${render(basePackage)(elementType)}]"
        case Code.Collection.Opt(elementType) =>
          s"Option[${render(basePackage)(elementType)}]"
      }

    case Code.Field(name, fieldType) =>
      val tpe = render(basePackage)(fieldType)
      if (tpe.isEmpty) s"val $name" else s"val $name: $tpe"

    case Code.Primitive.ScalaInt     => "Int"
    case Code.Primitive.ScalaLong    => "Long"
    case Code.Primitive.ScalaDouble  => "Double"
    case Code.Primitive.ScalaFloat   => "Float"
    case Code.Primitive.ScalaChar    => "Char"
    case Code.Primitive.ScalaByte    => "Byte"
    case Code.Primitive.ScalaShort   => "Short"
    case Code.Primitive.ScalaBoolean => "Boolean"
    case Code.Primitive.ScalaUnit    => "Unit"
    case Code.Primitive.ScalaString  => "String"
    case Code.ScalaType.Inferred     => ""

    case Code.EndpointCode(method, pathPatternCode, queryParamsCode, headersCode, inCode, outCodes, errorsCode) =>
      s"""Endpoint(Method.$method / ${pathPatternCode.segments.map(renderSegment).mkString(" / ")})
         |  ${queryParamsCode.map(renderQueryCode).mkString("\n")}
         |  ${headersCode.headers.map(renderHeader).mkString("\n")}
         |  ${renderInCode(inCode)}
         |  ${outCodes.map(renderOutCode).mkString("\n")}
         |  ${errorsCode.map(renderOutErrorCode).mkString("\n")}
         |""".stripMargin

    case Code.TypeRef(name) =>
      name

    case scalaType =>
      throw new Exception(s"Unknown ScalaType: $scalaType")
  }

  private def endpointWithChunk(endpoints: Map[Code.Field, Code.EndpointCode]) =
    endpoints.exists { case (_, code) =>
      code.inCode.inType.contains("Chunk[") ||
      (code.outCodes ++ code.errorsCode).exists(_.outType.contains("Chunk["))
    }

  def renderSegment(segment: Code.PathSegmentCode): String = segment match {
    case Code.PathSegmentCode(name, segmentType) =>
      segmentType match {
        case Code.CodecType.Boolean => s"""bool("$name")"""
        case Code.CodecType.Int     => s"""int("$name")"""
        case Code.CodecType.Long    => s"""long("$name")"""
        case Code.CodecType.String  => s"""string("$name")"""
        case Code.CodecType.UUID    => s"""uuid("$name")"""
        case Code.CodecType.Literal => s""""$name""""
      }

  }

  // currently, we do not support schemas
  def renderHeader(header: Code.HeaderCode): String = {
    val headerSelector = header.name.toLowerCase match {
      case "accept"                           => "HeaderCodec.accept"
      case "accept-encoding"                  => "HeaderCodec.acceptEncoding"
      case "accept-language"                  => "HeaderCodec.acceptLanguage"
      case "accept-ranges"                    => "HeaderCodec.acceptRanges"
      case "accept-patch"                     => "HeaderCodec.acceptPatch"
      case "access-control-allow-credentials" => "HeaderCodec.accessControlAllowCredentials"
      case "access-control-allow-headers"     => "HeaderCodec.accessControlAllowHeaders"
      case "access-control-allow-methods"     => "HeaderCodec.accessControlAllowMethods"
      case "access-control-allow-origin"      => "HeaderCodec.accessControlAllowOrigin"
      case "access-control-expose-headers"    => "HeaderCodec.accessControlExposeHeaders"
      case "access-control-max-age"           => "HeaderCodec.accessControlMaxAge"
      case "access-control-request-headers"   => "HeaderCodec.accessControlRequestHeaders"
      case "access-control-request-method"    => "HeaderCodec.accessControlRequestMethod"
      case "age"                              => "HeaderCodec.age"
      case "allow"                            => "HeaderCodec.allow"
      case "authorization"                    => "HeaderCodec.authorization"
      case "cache-control"                    => "HeaderCodec.cacheControl"
      case "clear-site-data"                  => "HeaderCodec.clearSiteData"
      case "connection"                       => "HeaderCodec.connection"
      case "content-base"                     => "HeaderCodec.contentBase"
      case "content-encoding"                 => "HeaderCodec.contentEncoding"
      case "content-language"                 => "HeaderCodec.contentLanguage"
      case "content-length"                   => "HeaderCodec.contentLength"
      case "content-location"                 => "HeaderCodec.contentLocation"
      case "content-transfer-encoding"        => "HeaderCodec.contentTransferEncoding"
      case "content-disposition"              => "HeaderCodec.contentDisposition"
      case "content-md5"                      => "HeaderCodec.contentMd5"
      case "content-range"                    => "HeaderCodec.contentRange"
      case "content-security-policy"          => "HeaderCodec.contentSecurityPolicy"
      case "content-type"                     => "HeaderCodec.contentType"
      case "cookie"                           => "HeaderCodec.cookie"
      case "date"                             => "HeaderCodec.date"
      case "dnt"                              => "HeaderCodec.dnt"
      case "etag"                             => "HeaderCodec.etag"
      case "expect"                           => "HeaderCodec.expect"
      case "expires"                          => "HeaderCodec.expires"
      case "forwarded"                        => "HeaderCodec.forwarded"
      case "from"                             => "HeaderCodec.from"
      case "host"                             => "HeaderCodec.host"
      case "if-match"                         => "HeaderCodec.ifMatch"
      case "if-modified-since"                => "HeaderCodec.ifModifiedSince"
      case "if-none-match"                    => "HeaderCodec.ifNoneMatch"
      case "if-range"                         => "HeaderCodec.ifRange"
      case "if-unmodified-since"              => "HeaderCodec.ifUnmodifiedSince"
      case "last-modified"                    => "HeaderCodec.lastModified"
      case "link"                             => "HeaderCodec.link"
      case "location"                         => "HeaderCodec.location"
      case "max-forwards"                     => "HeaderCodec.maxForwards"
      case "origin"                           => "HeaderCodec.origin"
      case "pragma"                           => "HeaderCodec.pragma"
      case "proxy-authenticate"               => "HeaderCodec.proxyAuthenticate"
      case "proxy-authorization"              => "HeaderCodec.proxyAuthorization"
      case "range"                            => "HeaderCodec.range"
      case "referer"                          => "HeaderCodec.referer"
      case "retry-after"                      => "HeaderCodec.retryAfter"
      case "sec-websocket-location"           => "HeaderCodec.secWebSocketLocation"
      case "sec-websocket-origin"             => "HeaderCodec.secWebSocketOrigin"
      case "sec-websocket-protocol"           => "HeaderCodec.secWebSocketProtocol"
      case "sec-websocket-version"            => "HeaderCodec.secWebSocketVersion"
      case "sec-websocket-key"                => "HeaderCodec.secWebSocketKey"
      case "sec-websocket-accept"             => "HeaderCodec.secWebSocketAccept"
      case "sec-websocket-extensions"         => "HeaderCodec.secWebSocketExtensions"
      case "server"                           => "HeaderCodec.server"
      case "set-cookie"                       => "HeaderCodec.setCookie"
      case "te"                               => "HeaderCodec.te"
      case "trailer"                          => "HeaderCodec.trailer"
      case "transfer-encoding"                => "HeaderCodec.transferEncoding"
      case "upgrade"                          => "HeaderCodec.upgrade"
      case "upgrade-insecure-requests"        => "HeaderCodec.upgradeInsecureRequests"
      case "user-agent"                       => "HeaderCodec.userAgent"
      case "vary"                             => "HeaderCodec.vary"
      case "via"                              => "HeaderCodec.via"
      case "warning"                          => "HeaderCodec.warning"
      case "web-socket-location"              => "HeaderCodec.webSocketLocation"
      case "web-socket-origin"                => "HeaderCodec.webSocketOrigin"
      case "web-socket-protocol"              => "HeaderCodec.webSocketProtocol"
      case "www-authenticate"                 => "HeaderCodec.wwwAuthenticate"
      case "x-frame-options"                  => "HeaderCodec.xFrameOptions"
      case "x-requested-with"                 => "HeaderCodec.xRequestedWith"
      case name                               => s"HeaderCodec.name[String]($name)"
    }
    s""".header($headerSelector)"""
  }

  def renderQueryCode(queryCode: Code.QueryParamCode): String = queryCode match {
    case Code.QueryParamCode(name, queryType) =>
      val tpe = queryType match {
        case Code.CodecType.Boolean => "Boolean"
        case Code.CodecType.Int     => "Int"
        case Code.CodecType.Long    => "Long"
        case Code.CodecType.String  => "String"
        case Code.CodecType.UUID    => "UUID"
        case Code.CodecType.Literal => throw new Exception("Literal query params are not supported")
      }
      s""".query(QueryCodec.queryTo[$tpe]("$name"))"""
  }

  def renderInCode(inCode: Code.InCode): String = inCode match {
    case Code.InCode(inType, Some(name), Some(doc)) =>
      s""".in[$inType](name = "$name", doc = md""\"$doc"\"")"""
    case Code.InCode(inType, Some(name), None)      =>
      s""".in[$inType](name = "$name")"""
    case Code.InCode(inType, None, Some(doc))       =>
      s""".in[$inType](doc = md""\"$doc"\"")"""
    case Code.InCode(inType, None, None)            =>
      s".in[$inType]"
  }

  def renderOutCode(outCode: Code.OutCode): String = outCode match {
    case Code.OutCode(outType, status, _, Some(doc)) =>
      s""".out[$outType](status = Status.$status, doc = md""\"$doc"\"")"""
    case Code.OutCode(outType, status, _, None)      =>
      s""".out[$outType](status = Status.$status)"""
  }

  def renderOutErrorCode(errOutCode: Code.OutCode): String = errOutCode match {
    case Code.OutCode(outType, status, _, Some(doc)) =>
      s""".outError[$outType](status = Status.$status, doc = md""\"$doc"\"")"""
    case Code.OutCode(outType, status, _, None)      =>
      s""".outError[$outType](status = Status.$status)"""
  }

}
