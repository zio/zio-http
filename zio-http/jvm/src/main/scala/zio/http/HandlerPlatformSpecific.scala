package zio.http

import java.io.{File, FileNotFoundException}
import java.nio.charset.Charset
import java.util.zip.ZipFile

import zio.{Trace, ZIO}

import zio.stream.ZStream

/**
 * Platform-specific constructors of [[Handler]], mixed into its companion
 * object.
 */
trait HandlerPlatformSpecific {
  self: Handler.type =>

  /**
   * Creates a handler that serves the classpath resource at `path`, found with
   * the class loader of zio-http. A resource in a directory (a `file:` URL) is
   * served like `fromFile`, with `Range` support. A resource in a jar (a `jar:`
   * URL) is served without `Range` support: the response has
   * `Accept-Ranges: none`. The handler fails with a
   * `java.io.FileNotFoundException` when the resource does not exist.
   *
   * On Scala.js, this method has no `charset` parameter and throws an
   * `UnsupportedOperationException` when it is called.
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
                contentLength  = entry.getSize
                lastModifiedMs = entry.getTime
                etag           = Header.ETag.Weak(s"${lastModifiedMs.toHexString}-${contentLength.toHexString}")
                lastModified   = Header.LastModified(
                  java.time.ZonedDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(if (lastModifiedMs < 0) 0 else lastModifiedMs),
                    java.time.ZoneOffset.UTC,
                  ),
                )
                inZStream      = ZStream
                  .acquireReleaseWith(openZip)(closeZip)
                  .mapZIO(jar => ZIO.attemptBlocking(jar.getEntry(resourcePath) -> jar))
                  .flatMap { case (entry, jar) => ZStream.fromInputStream(jar.getInputStream(entry)) }
                response       = Response(body = Body.fromStream(inZStream, contentLength))
                  .addHeader(Header.AcceptRanges.None) // Range not supported for JAR resources
                  .addHeader(etag)
                  .addHeader(lastModified)
              } yield mediaType.fold(response) { t =>
                val charset0 = if (t.mainType == "text" || !t.binary) Some(charset) else None
                response
                  .addHeader(Header.ContentType(t, charset = charset0))
              }
            }
        }

      case proto =>
        Handler.fail(new IllegalArgumentException(s"Unsupported protocol: $proto"))
    }
  }

  /**
   * Creates a handler that succeeds with the URL of the classpath resource at
   * `path`, or fails with an `IllegalArgumentException` when the resource does
   * not exist.
   */
  def getResource(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, java.net.URL] =
    Handler
      .fromZIO(ZIO.attemptBlocking(getClass.getClassLoader.getResource(path)))
      .flatMap { resource =>
        if (resource == null) Handler.fail(new IllegalArgumentException(s"Resource $path not found"))
        else Handler.succeed(resource)
      }

  /**
   * Creates a handler that succeeds with a `java.io.File` made from the path of
   * the classpath resource at `path`. For a resource inside a jar, the returned
   * file does not exist on the file system.
   */
  def getResourceAsFile(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, File] =
    getResource(path).map(url => new File(url.getPath))

}
