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

  def makeFormat(config: Path): (Path, String) => String = {
    import org.scalafmt.interfaces.Scalafmt
    val scalafmt = Scalafmt.create(this.getClass.getClassLoader).createSession(config)
    (file: Path, content: String) => scalafmt.format(file, content)
  }

  def writeFiles(files: Code.Files, basePath: Path, basePackage: String, scalafmtPath: Option[Path]): Iterable[Path] = {

    val formatCode = scalafmtPath.fold((_: Path, content: String) => content)(makeFormat)

    val rendered = renderedFiles(files, basePackage)
    rendered.map { case (path, rawContent) =>
      val content  = formatCode(Paths.get(path), rawContent)
      val filePath = Paths.get(basePath.toString, path)
      Files.createDirectories(filePath.getParent)
      Files.write(filePath, content.getBytes(StandardCharsets.UTF_8), CREATE, TRUNCATE_EXISTING)
      filePath
    }
  }

  def renderedFiles(files: Code.Files, basePackage: String): Map[String, String] =
    files.files.map { file =>
      val (_, rendered) = render(basePackage)(file)
      file.path.mkString("/") -> rendered
    }.toMap

  def render(basePackage: String)(structure: Code): (List[Code.Import], String) = structure match {
    case Code.Files(_) =>
      throw new Exception("Files should be rendered separately")

    case Code.File(_, path, imports, objects, caseClasses, enums) =>
      val (objImports, objContent)   = objects.map(render(basePackage)).unzip
      val (ccImports, ccContent)     = caseClasses.map(render(basePackage)).unzip
      val (enumImports, enumContent) = enums.map(render(basePackage)).unzip
      val allImports = (imports ++ objImports.flatten ++ ccImports.flatten ++ enumImports.flatten).distinct
      val content    =
        s"package $basePackage${if (path.exists(_.nonEmpty)) path.mkString(if (basePackage.isEmpty) "" else ".", ".", "")
          else ""}\n\n" +
          s"${allImports.map(render(basePackage)(_)._2).mkString("\n")}\n\n" +
          objContent.mkString("\n") +
          ccContent.mkString("\n") +
          enumContent.mkString("\n")
      Nil -> content

    case Code.Import.Absolute(path) =>
      Nil -> s"import $path"

    case Code.Import.FromBase(path) =>
      Nil -> s"import $basePackage.$path"

    case Code.Object(name, schema, endpoints, objects, caseClasses, enums) =>
      val baseImports                      = if (endpoints.nonEmpty) EndpointImports else Nil
      val (epImports, epContent)           = endpoints.map { case (k, v) =>
        val (kImports, kContent) = render(basePackage)(k)
        val (vImports, vContent) = render(basePackage)(v)
        (kImports ++ vImports, s"$kContent=$vContent")
      }.unzip
      val (objectsImports, objectsContent) = objects.map(render(basePackage)).unzip
      val (ccImports, ccContent)           = caseClasses.map(render(basePackage)).unzip
      val (enumImports, enumContent)       = enums.map(render(basePackage)).unzip
      val allImports                       =
        (baseImports ++ epImports.flatten ++ objectsImports.flatten ++ ccImports.flatten ++ enumImports.flatten).distinct
      val content                          =
        s"object $name {\n" +
          allImports.map(render(basePackage)(_)._2).mkString("", "\n", "\n") +
          epContent.mkString("\n") +
          (if (schema) s"\n\n implicit val codec: Schema[$name] = DeriveSchema.gen[$name]" else "") +
          "\n" + objectsContent.mkString("\n") +
          "\n" + ccContent.mkString("\n") +
          "\n" + enumContent.mkString("\n") +
          "\n}"
      Nil -> content

    case Code.CaseClass(name, fields, companionObject) =>
      val (imports, contents)    = fields.map(render(basePackage)).unzip
      val (coImports, coContent) =
        companionObject.map { co =>
          val (coImports, coContent) = render(basePackage)(co)
          (coImports, s"\n$coContent")
        }.getOrElse(Nil -> "")
      val content                =
        s"case class $name(\n" +
          contents.mkString(",\n").replace("val ", " ") +
          "\n)" + coContent
      (imports.flatten ++ coImports).distinct -> content

    case Code.Enum(name, cases, caseNames, discriminator, noDiscriminator, schema) =>
      val discriminatorAnnotation      =
        if (noDiscriminator) "@noDiscriminator\n" else ""
      val discriminatorNameAnnotation  =
        if (discriminator.isDefined) s"""@discriminatorName("${discriminator.get}")\n""" else ""
      val (casesImports, casesContent) =
        if (caseNames.nonEmpty) {
          val (imports, contents) = cases.map(render(basePackage)).unzip
          val content             =
            contents
              .zip(caseNames)
              .map { case (content, name) => s"""@caseName("$name")\n$content""" }
              .mkString("\n")
          imports -> content
        } else {
          val (imports, contents) = cases.map(render(basePackage)).unzip
          imports -> contents.mkString("\n")
        }

      val content =
        discriminatorAnnotation +
          discriminatorNameAnnotation +
          s"sealed trait $name\n" +
          s"object $name {\n" +
          (if (schema) s"\n\n implicit val codec: Schema[$name] = DeriveSchema.gen[$name]\n" else "") +
          casesContent +
          "\n}"
      casesImports.flatten.distinct -> content

    case col: Code.Collection =>
      col match {
        case Code.Collection.Seq(elementType) =>
          val (imports, tpe) = render(basePackage)(elementType)
          (Code.Import("zio.Chunk") :: imports) -> s"Chunk[$tpe]"
        case Code.Collection.Set(elementType) =>
          val (imports, tpe) = render(basePackage)(elementType)
          imports -> s"Set[$tpe]"
        case Code.Collection.Map(elementType) =>
          val (imports, tpe) = render(basePackage)(elementType)
          imports -> s"Map[String, $tpe]"
        case Code.Collection.Opt(elementType) =>
          val (imports, tpe) = render(basePackage)(elementType)
          imports -> s"Option[$tpe]"
      }

    case Code.Field(name, fieldType) =>
      val (imports, tpe) = render(basePackage)(fieldType)
      val content        = if (tpe.isEmpty) s"val $name" else s"val $name: $tpe"
      imports -> content

    case Code.Primitive.ScalaBoolean => Nil                                 -> "Boolean"
    case Code.Primitive.ScalaByte    => Nil                                 -> "Byte"
    case Code.Primitive.ScalaChar    => Nil                                 -> "Char"
    case Code.Primitive.ScalaDouble  => Nil                                 -> "Double"
    case Code.Primitive.ScalaFloat   => Nil                                 -> "Float"
    case Code.Primitive.ScalaInt     => Nil                                 -> "Int"
    case Code.Primitive.ScalaLong    => Nil                                 -> "Long"
    case Code.Primitive.ScalaShort   => Nil                                 -> "Short"
    case Code.Primitive.ScalaString  => Nil                                 -> "String"
    case Code.Primitive.ScalaUnit    => Nil                                 -> "Unit"
    case Code.Primitive.ScalaUUID    => List(Code.Import("java.util.UUID")) -> "UUID"
    case Code.ScalaType.Inferred     => Nil                                 -> ""

    case Code.EndpointCode(method, pathPatternCode, queryParamsCode, headersCode, inCode, outCodes, errorsCode) =>
      val (queryImports, queryContent) = queryParamsCode.map(renderQueryCode).unzip
      val allImports                   = queryImports.flatten.toList.distinct
      val content                      =
        s"""Endpoint(Method.$method / ${pathPatternCode.segments.map(renderSegment).mkString(" / ")})
           |  ${queryContent.mkString("\n")}
           |  ${headersCode.headers.map(renderHeader).mkString("\n")}
           |  ${renderInCode(inCode)}
           |  ${outCodes.map(renderOutCode).mkString("\n")}
           |  ${errorsCode.map(renderOutErrorCode).mkString("\n")}
           |""".stripMargin
      allImports -> content

    case Code.TypeRef(name) =>
      Nil -> name

    case scalaType =>
      throw new Exception(s"Unknown ScalaType: $scalaType")
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

  def renderQueryCode(queryCode: Code.QueryParamCode): (List[Code.Import], String) = queryCode match {
    case Code.QueryParamCode(name, queryType) =>
      val (imports, tpe) = queryType match {
        case Code.CodecType.Boolean => Nil                                 -> "Boolean"
        case Code.CodecType.Int     => Nil                                 -> "Int"
        case Code.CodecType.Long    => Nil                                 -> "Long"
        case Code.CodecType.String  => Nil                                 -> "String"
        case Code.CodecType.UUID    => List(Code.Import("java.util.UUID")) -> "UUID"
        case Code.CodecType.Literal => throw new Exception("Literal query params are not supported")
      }
      imports -> s""".query(QueryCodec.queryTo[$tpe]("$name"))"""
  }

  def renderInCode(inCode: Code.InCode): String = {
    val stream = if (inCode.streaming) "Stream" else ""
    inCode match {
      case Code.InCode(inType, Some(name), Some(doc), _) =>
        s""".in$stream[$inType](name = "$name", doc = md""\"$doc"\"")"""
      case Code.InCode(inType, Some(name), None, _)      =>
        s""".in$stream[$inType](name = "$name")"""
      case Code.InCode(inType, None, Some(doc), _)       =>
        s""".in$stream[$inType](doc = md""\"$doc"\"")"""
      case Code.InCode(inType, None, None, _)            =>
        s".in$stream[$inType]"
    }
  }

  def renderOutCode(outCode: Code.OutCode): String = {
    val stream = if (outCode.streaming) "Stream" else ""
    outCode match {
      case Code.OutCode(outType, status, _, Some(doc), _) =>
        s""".out$stream[$outType](status = Status.$status, doc = md""\"$doc"\"")"""
      case Code.OutCode(outType, status, _, None, _)      =>
        s""".out$stream[$outType](status = Status.$status)"""
    }
  }

  def renderOutErrorCode(errOutCode: Code.OutCode): String = {
    val stream = if (errOutCode.streaming) "Stream" else ""
    errOutCode match {
      case Code.OutCode(outType, status, _, Some(doc), _) =>
        s""".outError$stream[$outType](status = Status.$status, doc = md""\"$doc"\"")"""
      case Code.OutCode(outType, status, _, None, _)      =>
        s""".outError$stream[$outType](status = Status.$status)"""
    }
  }

}
