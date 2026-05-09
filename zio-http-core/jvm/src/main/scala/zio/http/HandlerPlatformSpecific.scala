package zio.http

import java.io.{File, FileNotFoundException}
import java.nio.charset.Charset
import java.util.zip.ZipFile
import zio.http._
import zio.{Trace, ZIO}


trait HandlerPlatformSpecific {
  self: Handler.type =>

  /**
   * Creates a handler from a resource path. For file:// resources, Range
   * requests are fully supported. For jar:// resources, Range requests are not
   * supported (returns Accept-Ranges: none).
   */
  def fromResource(path: String, charset: Charset = Charsets.Utf8)(implicit
    trace: Trace,
  ): Handler[Any, Throwable, Request, Response] =
    Handler.fromZIO {
      ZIO
        .attemptBlocking(getClass.getClassLoader.getResource(path))
        .map { resource =>
          if (resource == null) Handler.fail(new FileNotFoundException(s"Resource $path not found"))
          else fromResourceWithURL(resource, charset)
        }
    }.flatten

  private[zio] def fromResourceWithURL(
    url: java.net.URL,
    charset: Charset,
  )(implicit trace: Trace): Handler[Any, Throwable, Request, Response] = {
    url.getProtocol match {
      case "file" => Handler.fromFile(new File(url.getPath), charset)
      case "jar"  =>
        val path         = new java.net.URI(url.getPath).getPath // remove "file:" prefix and normalize whitespace
        val bangIndex    = path.indexOf('!')
        val filePath     = path.substring(0, bangIndex)
        val resourcePath = path.substring(bangIndex + 2)
        val mediaType    = determineMediaType(resourcePath)
        val openZip      = ZIO.attemptBlockingIO(new ZipFile(filePath))
        val closeZip     = (jar: ZipFile) => ZIO.attemptBlocking(jar.close()).ignoreLogged

        def fileNotFound = new FileNotFoundException(s"Resource $resourcePath not found")

        def isDirectory = new IllegalArgumentException(s"Resource $resourcePath is a directory")

        // For JAR resources, we don't support Range requests due to complexity of seeking in compressed streams
        Handler.fromZIO {
          ZIO
            .acquireReleaseWith(openZip)(closeZip) { jar =>
              for {
                 entry <- ZIO
                   .attemptBlocking(Option(jar.getEntry(resourcePath)))
                   .collect(fileNotFound) { case Some(e) => e }
                 _     <- ZIO.when(entry.isDirectory)(ZIO.fail(isDirectory))
                 contentLen     = entry.getSize
                 lastModifiedMs = entry.getTime
                 etagValue      = s"""W/"${lastModifiedMs.toHexString}-${contentLen.toHexString}""""
                 lastModifiedValue = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                   java.time.ZonedDateTime.ofInstant(
                     java.time.Instant.ofEpochMilli(if (lastModifiedMs < 0) 0 else lastModifiedMs),
                     java.time.ZoneOffset.UTC,
                   ),
                 )
                 inBytes <- ZIO.attemptBlocking {
                   val is = jar.getInputStream(entry)
                   try {
                     val baos = new java.io.ByteArrayOutputStream(if (contentLen > 0) contentLen.toInt else 8192)
                     val buf  = new Array[Byte](8192)
                     var n    = is.read(buf)
                     while (n >= 0) { baos.write(buf, 0, n); n = is.read(buf) }
                     baos.toByteArray
                   } finally is.close()
                 }
                 response       = Response(status = Status.Ok, body = Body.fromArray(inBytes))
                   .addHeader("accept-ranges", "none") // Range not supported for JAR resources
                   .addHeader("etag", etagValue)
                   .addHeader("last-modified", lastModifiedValue)
              } yield mediaType.fold(response) { t =>
                val charsetStr = if (t.mainType == "text" || !t.binary) Some(charset.name()) else scala.None
                val ctValue    = charsetStr.fold(t.fullType)(cs => s"${t.fullType}; charset=$cs")
                response
                  .addHeader("content-type", ctValue)
              }
            }
        }

      case proto =>
        Handler.fail(new IllegalArgumentException(s"Unsupported protocol: $proto"))
    }
  }

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResource(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, java.net.URL] =
    Handler
      .fromZIO(ZIO.attemptBlocking(getClass.getClassLoader.getResource(path)))
      .flatMap { resource =>
        if (resource == null) Handler.fail(new IllegalArgumentException(s"Resource $path not found"))
        else Handler.succeed(resource)
      }

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResourceAsFile(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, File] =
    getResource(path).map(url => new File(url.getPath))

}
