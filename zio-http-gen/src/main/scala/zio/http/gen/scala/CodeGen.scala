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

      val allImports            = (imports ++ objImports.flatten ++ ccImports.flatten ++ enumImports.flatten).distinct
      val renderedSortedImports = {
        val javaImports  = List.newBuilder[String]
        val scalaImports = List.newBuilder[String]
        val otherImports = List.newBuilder[String]
        allImports.foreach { imprt =>
          val rendered = render(basePackage)(imprt)._2
          if (rendered.startsWith("import java.")) javaImports += rendered
          else if (rendered.startsWith("import scala.")) scalaImports += rendered
          else otherImports += rendered
        }
        otherImports.result().sorted ::: javaImports.result().sorted ::: scalaImports.result().sorted
      }
      val content               =
        s"package $basePackage${if (path.exists(_.nonEmpty)) path.mkString(if (basePackage.isEmpty) "" else ".", ".", "")
          else ""}" +
          renderedSortedImports.mkString("\n\n", "\n", "\n\n") +
          objContent.mkString("\n") +
          ccContent.mkString("\n") +
          enumContent.mkString("\n")
      Nil -> content

    case Code.Import.Absolute(path) =>
      Nil -> s"import $path"

    case Code.Import.FromBase(path) =>
      Nil -> s"import $basePackage.$path"

    case Code.Object(name, extensions, schema, endpoints, objects, caseClasses, enums) =>
      val baseImports                      = if (endpoints.nonEmpty) EndpointImports else Nil
      val (epImports, epContent)           = endpoints.toList.map { case (k, v) =>
        val (kImports, kContent) = render(basePackage)(k)
        val (vImports, vContent) = render(basePackage)(v)
        (kImports ++ vImports, s"$kContent=$vContent")
      }.unzip
      val (objectsImports, objectsContent) = objects.map(render(basePackage)).unzip
      val (ccImports, ccContent)           = caseClasses.map(render(basePackage)).unzip
      val (enumImports, enumContent)       = enums.map(render(basePackage)).unzip
      val allImports                       =
        (baseImports ++ epImports.flatten ++ objectsImports.flatten ++ ccImports.flatten ++ enumImports.flatten).distinct
      val content                          = {
        val sb   = new StringBuilder()
        sb ++= "object "
        sb ++= name
        var prex = " extends "
        extensions.foreach { ext =>
          sb ++= prex
          prex = " with "
          sb ++= ext
        }
        sb += '{'
        allImports.map(render(basePackage)(_)._2).foreach { imp =>
          sb += '\n'
          sb ++= imp
        }
        epContent.foreach { epc =>
          sb += '\n'
          sb ++= epc
        }
        sb += '\n'
        schema.foreach(_.codecLineWithStringBuilder(name, sb))
        objectsContent.foreach { obj =>
          sb += '\n'
          sb ++= obj
        }
        ccContent.foreach { cc =>
          sb += '\n'
          sb ++= cc
        }
        enumContent.foreach { en =>
          sb += '\n'
          sb ++= en
        }
        sb ++= "\n}"
        sb.result()
      }
      Nil -> content

    case Code.CaseClass(name, fields, companionObject, mixins) =>
      val (imports, contents)    = fields.map(render(basePackage)).unzip
      val (coImports, coContent) =
        companionObject.map { co =>
          val (coImports, coContent) = render(basePackage)(co)
          (coImports, s"\n$coContent")
        }.getOrElse(Nil -> "")
      val mixinsString           = mixins match {
        case Nil => ""
        case _   => mixins.mkString(" extends ", " with ", "")
      }
      val content                =
        s"case class $name(\n" +
          contents.mkString(",\n").replace("val ", " ") +
          "\n)" + mixinsString + coContent
      (imports.flatten ++ coImports).distinct -> content

    case Code.Enum(name, cases, caseNames, discriminator, noDiscriminator, schema, abstractMembers) =>
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

      val (traitBodyImports, traitBody) = {
        val traitBodyBuilder = new StringBuilder().append(' ')
        var pre              = '{'
        val imports          = abstractMembers.foldLeft(List.empty[Code.Import]) {
          case (importsAcc, Code.Field(name, fieldType, annotations)) =>
            val (imports, tpe) = render(basePackage)(fieldType)
            if (tpe.isEmpty) importsAcc
            else {
              traitBodyBuilder += pre
              pre = '\n'
              annotations.foreach { annotation =>
                traitBodyBuilder ++= annotation.value
                traitBodyBuilder += '\n'
              }
              traitBodyBuilder ++= "def "
              traitBodyBuilder ++= name
              traitBodyBuilder ++= ": "
              traitBodyBuilder ++= tpe

              annotations.foldRight(imports ::: importsAcc)(_.imports ::: _).distinct
            }
        }
        val body             =
          if (pre == '{') "\n"
          else traitBodyBuilder.append("\n}\n").result()

        imports -> body
      }

      val content =
        discriminatorAnnotation +
          discriminatorNameAnnotation +
          s"sealed trait $name" + traitBody +
          s"object $name {\n" +
          (if (schema) s"\n\n implicit val codec: Schema[$name] = DeriveSchema.gen[$name]\n" else "") +
          casesContent +
          "\n}"
      casesImports.foldRight(traitBodyImports)(_ ::: _).distinct -> content

    case col: Code.Collection =>
      col match {
        case Code.Collection.Seq(elementType, nonEmpty) =>
          val (imports, tpe) = render(basePackage)(elementType)
          if (nonEmpty) (Code.Import("zio.NonEmptyChunk") :: imports) -> s"NonEmptyChunk[$tpe]"
          else (Code.Import("zio.Chunk") :: imports)                  -> s"Chunk[$tpe]"
        case Code.Collection.Set(elementType, nonEmpty) =>
          val (imports, tpe) = render(basePackage)(elementType)
          if (nonEmpty) (Code.Import("zio.prelude.NonEmptySet") :: imports) -> s"NonEmptySet[$tpe]"
          else imports                                                      -> s"Set[$tpe]"
        case Code.Collection.Map(elementType, keysType) =>
          val (vImports, vType) = render(basePackage)(elementType)
          keysType.fold(vImports -> s"Map[String, $vType]") { keyType =>
            val (kImports, kType) = render(basePackage)(keyType)
            (kImports ::: vImports).distinct -> s"Map[$kType, $vType]"
          }
        case Code.Collection.Opt(elementType)           =>
          val (imports, tpe) = render(basePackage)(elementType)
          imports -> s"Option[$tpe]"
      }

    case Code.Field(name, fieldType, annotations) =>
      val (imports, tpe)                        = render(basePackage)(fieldType)
      val (annotationValues, annotationImports) = annotations.unzip(ann => ann.value -> ann.imports)
      val allImports                            = annotationImports.foldRight(imports)(_ ::: _).distinct
      val content                               = if (tpe.isEmpty) s"val $name" else s"val $name: $tpe"
      val multipleAnnotationsAboveContent       = if (annotationValues.size > 1) "\n" + content else content
      allImports -> annotationValues.mkString("", "\n", multipleAnnotationsAboveContent)

    case Code.Primitive.ScalaBoolean   => Nil                                      -> "Boolean"
    case Code.Primitive.ScalaByte      => Nil                                      -> "Byte"
    case Code.Primitive.ScalaChar      => Nil                                      -> "Char"
    case Code.Primitive.ScalaDouble    => Nil                                      -> "Double"
    case Code.Primitive.ScalaFloat     => Nil                                      -> "Float"
    case Code.Primitive.ScalaInt       => Nil                                      -> "Int"
    case Code.Primitive.ScalaLong      => Nil                                      -> "Long"
    case Code.Primitive.ScalaShort     => Nil                                      -> "Short"
    case Code.Primitive.ScalaString    => Nil                                      -> "String"
    case Code.Primitive.ScalaUnit      => Nil                                      -> "Unit"
    case Code.Primitive.ScalaUUID      => List(Code.Import("java.util.UUID"))      -> "UUID"
    case Code.Primitive.ScalaLocalDate => List(Code.Import("java.time.LocalDate")) -> "LocalDate"
    case Code.Primitive.ScalaInstant   => List(Code.Import("java.time.Instant"))   -> "Instant"
    case Code.Primitive.ScalaTime      => List(Code.Import("java.time.LocalTime")) -> "LocalTime"
    case Code.ScalaType.Inferred       => Nil                                      -> ""

    case Code.EndpointCode(
          method,
          pathPatternCode,
          queryParamsCode,
          headersCode,
          inCode,
          outCodes,
          errorsCode,
          authTypeCode,
        ) =>
      val (queryImports, queryContent)   = queryParamsCode.map(renderQueryCode).unzip
      val (headerImports, headerContent) = headersCode.headers.map(renderHeader).unzip
      val (segments, pathImports)        = pathPatternCode.segments.map(renderSegment).unzip
      val allImports                     = (pathImports ++ queryImports ++ headerImports).flatten.distinct
      val authCode                       = authTypeCode.map(renderAuthCode).getOrElse("")
      val content                        =
        s"""Endpoint(Method.$method / ${segments.mkString(" / ")})
           |  ${queryContent.mkString("\n")}
           |  ${headerContent.mkString("\n")}
           |  ${renderInCode(inCode)}
           |  ${outCodes.map(renderOutCode).mkString("\n")}
           |  ${errorsCode.map(renderOutErrorCode).mkString("\n")}
           |  $authCode
           |""".stripMargin
      allImports -> content

    case Code.TypeRef(name) =>
      Nil -> name

    case Code.ScalaType.JsonAST =>
      List(Code.Import("zio.json.ast.Json")) -> "Json"

    case scalaType =>
      throw new Exception(s"Unknown ScalaType: $scalaType")
  }

  def renderSegmentType(name: String, segmentType: Code.CodecType): (String, List[Code.Import]) =
    segmentType match {
      case Code.CodecType.Boolean                          => s"""bool("$name")"""      -> Nil
      case Code.CodecType.Int                              => s"""int("$name")"""       -> Nil
      case Code.CodecType.Long                             => s"""long("$name")"""      -> Nil
      case Code.CodecType.String                           => s"""string("$name")"""    -> Nil
      case Code.CodecType.UUID                             => s"""uuid("$name")"""      -> Nil
      case Code.CodecType.LocalDate                        => s"""date("$name")"""      -> Nil
      case Code.CodecType.LocalTime                        => s"""time("$name")"""      -> Nil
      case Code.CodecType.Instant                          => s"""date-time("$name")""" -> Nil
      case Code.CodecType.Duration                         => s"""duration("$name")"""  -> Nil
      case Code.CodecType.Literal                          => s""""$name""""            -> Nil
      case Code.CodecType.Aliased(underlying, newtypeName) =>
        val sb              = new StringBuilder()
        val (code, imports) = renderSegmentType(name, underlying)
        sb ++= code
        sb ++= ".transform("
        sb ++= newtypeName
        sb ++= ".wrap)("
        sb ++= newtypeName
        sb ++= ".unwrap)"
        sb.result() -> (Code.Import.FromBase("components." + newtypeName) :: imports)
      case Code.CodecType.SeqOf(_, _)                      =>
        throw new Exception("Collection path segments are not supported")
      case Code.CodecType.SetOf(_, _)                      =>
        throw new Exception("Collection path segments are not supported")
    }

  def renderSegment(segment: Code.PathSegmentCode): (String, List[Code.Import]) = segment match {
    case Code.PathSegmentCode(name, segmentType) =>
      renderSegmentType(name, segmentType)
  }

  def renderHeader(header: Code.HeaderCode): (List[Code.Import], String) = {
    val (imports, headerSelector) = header.name.toLowerCase match {
      case "accept"                           => Nil -> "HeaderCodec.accept"
      case "accept-encoding"                  => Nil -> "HeaderCodec.acceptEncoding"
      case "accept-language"                  => Nil -> "HeaderCodec.acceptLanguage"
      case "accept-ranges"                    => Nil -> "HeaderCodec.acceptRanges"
      case "accept-patch"                     => Nil -> "HeaderCodec.acceptPatch"
      case "access-control-allow-credentials" => Nil -> "HeaderCodec.accessControlAllowCredentials"
      case "access-control-allow-headers"     => Nil -> "HeaderCodec.accessControlAllowHeaders"
      case "access-control-allow-methods"     => Nil -> "HeaderCodec.accessControlAllowMethods"
      case "access-control-allow-origin"      => Nil -> "HeaderCodec.accessControlAllowOrigin"
      case "access-control-expose-headers"    => Nil -> "HeaderCodec.accessControlExposeHeaders"
      case "access-control-max-age"           => Nil -> "HeaderCodec.accessControlMaxAge"
      case "access-control-request-headers"   => Nil -> "HeaderCodec.accessControlRequestHeaders"
      case "access-control-request-method"    => Nil -> "HeaderCodec.accessControlRequestMethod"
      case "age"                              => Nil -> "HeaderCodec.age"
      case "allow"                            => Nil -> "HeaderCodec.allow"
      case "authorization"                    => Nil -> "HeaderCodec.authorization"
      case "cache-control"                    => Nil -> "HeaderCodec.cacheControl"
      case "clear-site-data"                  => Nil -> "HeaderCodec.clearSiteData"
      case "connection"                       => Nil -> "HeaderCodec.connection"
      case "content-base"                     => Nil -> "HeaderCodec.contentBase"
      case "content-encoding"                 => Nil -> "HeaderCodec.contentEncoding"
      case "content-language"                 => Nil -> "HeaderCodec.contentLanguage"
      case "content-length"                   => Nil -> "HeaderCodec.contentLength"
      case "content-location"                 => Nil -> "HeaderCodec.contentLocation"
      case "content-transfer-encoding"        => Nil -> "HeaderCodec.contentTransferEncoding"
      case "content-disposition"              => Nil -> "HeaderCodec.contentDisposition"
      case "content-md5"                      => Nil -> "HeaderCodec.contentMd5"
      case "content-range"                    => Nil -> "HeaderCodec.contentRange"
      case "content-security-policy"          => Nil -> "HeaderCodec.contentSecurityPolicy"
      case "content-type"                     => Nil -> "HeaderCodec.contentType"
      case "cookie"                           => Nil -> "HeaderCodec.cookie"
      case "date"                             => Nil -> "HeaderCodec.date"
      case "dnt"                              => Nil -> "HeaderCodec.dnt"
      case "etag"                             => Nil -> "HeaderCodec.etag"
      case "expect"                           => Nil -> "HeaderCodec.expect"
      case "expires"                          => Nil -> "HeaderCodec.expires"
      case "forwarded"                        => Nil -> "HeaderCodec.forwarded"
      case "from"                             => Nil -> "HeaderCodec.from"
      case "host"                             => Nil -> "HeaderCodec.host"
      case "if-match"                         => Nil -> "HeaderCodec.ifMatch"
      case "if-modified-since"                => Nil -> "HeaderCodec.ifModifiedSince"
      case "if-none-match"                    => Nil -> "HeaderCodec.ifNoneMatch"
      case "if-range"                         => Nil -> "HeaderCodec.ifRange"
      case "if-unmodified-since"              => Nil -> "HeaderCodec.ifUnmodifiedSince"
      case "last-modified"                    => Nil -> "HeaderCodec.lastModified"
      case "link"                             => Nil -> "HeaderCodec.link"
      case "location"                         => Nil -> "HeaderCodec.location"
      case "max-forwards"                     => Nil -> "HeaderCodec.maxForwards"
      case "origin"                           => Nil -> "HeaderCodec.origin"
      case "pragma"                           => Nil -> "HeaderCodec.pragma"
      case "proxy-authenticate"               => Nil -> "HeaderCodec.proxyAuthenticate"
      case "proxy-authorization"              => Nil -> "HeaderCodec.proxyAuthorization"
      case "range"                            => Nil -> "HeaderCodec.range"
      case "referer"                          => Nil -> "HeaderCodec.referer"
      case "retry-after"                      => Nil -> "HeaderCodec.retryAfter"
      case "sec-websocket-location"           => Nil -> "HeaderCodec.secWebSocketLocation"
      case "sec-websocket-origin"             => Nil -> "HeaderCodec.secWebSocketOrigin"
      case "sec-websocket-protocol"           => Nil -> "HeaderCodec.secWebSocketProtocol"
      case "sec-websocket-version"            => Nil -> "HeaderCodec.secWebSocketVersion"
      case "sec-websocket-key"                => Nil -> "HeaderCodec.secWebSocketKey"
      case "sec-websocket-accept"             => Nil -> "HeaderCodec.secWebSocketAccept"
      case "sec-websocket-extensions"         => Nil -> "HeaderCodec.secWebSocketExtensions"
      case "server"                           => Nil -> "HeaderCodec.server"
      case "set-cookie"                       => Nil -> "HeaderCodec.setCookie"
      case "te"                               => Nil -> "HeaderCodec.te"
      case "trailer"                          => Nil -> "HeaderCodec.trailer"
      case "transfer-encoding"                => Nil -> "HeaderCodec.transferEncoding"
      case "upgrade"                          => Nil -> "HeaderCodec.upgrade"
      case "upgrade-insecure-requests"        => Nil -> "HeaderCodec.upgradeInsecureRequests"
      case "user-agent"                       => Nil -> "HeaderCodec.userAgent"
      case "vary"                             => Nil -> "HeaderCodec.vary"
      case "via"                              => Nil -> "HeaderCodec.via"
      case "warning"                          => Nil -> "HeaderCodec.warning"
      case "web-socket-location"              => Nil -> "HeaderCodec.webSocketLocation"
      case "web-socket-origin"                => Nil -> "HeaderCodec.webSocketOrigin"
      case "web-socket-protocol"              => Nil -> "HeaderCodec.webSocketProtocol"
      case "www-authenticate"                 => Nil -> "HeaderCodec.wwwAuthenticate"
      case "x-frame-options"                  => Nil -> "HeaderCodec.xFrameOptions"
      case "x-requested-with"                 => Nil -> "HeaderCodec.xRequestedWith"
      case name                               =>
        val (imports, tpe) = renderQueryCodecType(header.headerType, name)
        imports -> s"""HeaderCodec.name[$tpe]("$name")"""
    }
    val optionalSuffix            = if (header.required) "" else ".optional"
    imports -> s""".header($headerSelector$optionalSuffix)"""
  }

  def renderQueryCode(queryCode: Code.QueryParamCode): (List[Code.Import], String) = queryCode match {
    case Code.QueryParamCode(name, queryType) =>
      val (imports, tpe) = renderQueryCodecType(queryType, name)
      imports -> s""".query(HttpCodec.query[$tpe]("$name"))"""
  }

  private def renderQueryCodecType(queryType: Code.CodecType, name: String): (List[Code.Import], String) =
    queryType match {
      case Code.CodecType.Boolean   => Nil                                      -> "Boolean"
      case Code.CodecType.Int       => Nil                                      -> "Int"
      case Code.CodecType.Long      => Nil                                      -> "Long"
      case Code.CodecType.String    => Nil                                      -> "String"
      case Code.CodecType.UUID      => List(Code.Import("java.util.UUID"))      -> "UUID"
      case Code.CodecType.LocalDate => List(Code.Import("java.time.LocalDate")) -> "LocalDate"
      case Code.CodecType.LocalTime => List(Code.Import("java.time.LocalTime")) -> "LocalTime"
      case Code.CodecType.Instant   => List(Code.Import("java.time.Instant"))   -> "Instant"
      case Code.CodecType.Duration  => List(Code.Import("java.time.Duration"))  -> "Duration"
      case Code.CodecType.Literal   => throw new Exception("Literal query params are not supported")
      case Code.CodecType.Aliased(underlying, newtypeName) =>
        val (imports, _) = renderQueryCodecType(underlying, name)
        (Code.Import.FromBase(s"components.$newtypeName") :: imports) -> (newtypeName + ".Type")
      case Code.CodecType.SeqOf(elementType, nonEmpty)     =>
        val (imports, elemTpe) = renderQueryCodecType(elementType, name)
        val chunkImport        = Code.Import("zio.Chunk")
        val collectionType     = if (nonEmpty) s"NonEmptyChunk[$elemTpe]" else s"Chunk[$elemTpe]"
        val allImports         =
          if (nonEmpty) Code.Import("zio.NonEmptyChunk") :: chunkImport :: imports
          else chunkImport :: imports
        allImports -> collectionType
      case Code.CodecType.SetOf(elementType, nonEmpty)     =>
        val (imports, elemTpe) = renderQueryCodecType(elementType, name)
        val chunkImport        = Code.Import("zio.Chunk")
        val collectionType     = if (nonEmpty) s"NonEmptyChunk[$elemTpe]" else s"Chunk[$elemTpe]"
        val allImports         =
          if (nonEmpty) Code.Import("zio.NonEmptyChunk") :: chunkImport :: imports
          else chunkImport :: imports
        allImports -> collectionType
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

  def renderAuthCode(authCode: Code.AuthTypeCode): String = {
    authCode match {
      case Code.AuthTypeCode("Bearer") =>
        s""".auth(AuthType.Bearer)"""
      case Code.AuthTypeCode("Basic")  =>
        s""".auth(AuthType.Basic)"""
      case Code.AuthTypeCode("Digest") =>
        s""".auth(AuthType.Digest)"""
      case _                           =>
        s""
    }
  }
}
