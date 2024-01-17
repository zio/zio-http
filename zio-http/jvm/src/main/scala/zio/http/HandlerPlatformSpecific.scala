package zio.http

import java.io.{File, FileNotFoundException}
import java.util.zip.ZipFile

import zio.{Trace, ZIO}

import zio.stream.ZStream

trait HandlerPlatformSpecific {
  self: Handler.type =>

  /**
   * Creates a handler from a resource path
   */
  def fromResource(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO
        .attemptBlocking(getClass.getClassLoader.getResource(path))
        .map { resource =>
          if (resource == null) Handler.fail(new FileNotFoundException(s"Resource $path not found"))
          else fromResourceWithURL(resource)
        }
    }.flatten

  private[zio] def fromResourceWithURL(
    url: java.net.URL,
  )(implicit trace: Trace): Handler[Any, Throwable, Any, Response] = {
    url.getProtocol match {
      case "file" => Handler.fromFile(new File(url.getPath))
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

        Handler.fromZIO {
          ZIO
            .acquireReleaseWith(openZip)(closeZip) { jar =>
              for {
                entry <- ZIO
                  .attemptBlocking(Option(jar.getEntry(resourcePath)))
                  .collect(fileNotFound) { case Some(e) => e }
                _     <- ZIO.when(entry.isDirectory)(ZIO.fail(isDirectory))
                contentLength = entry.getSize
                inZStream     = ZStream
                  .acquireReleaseWith(openZip)(closeZip)
                  .mapZIO(jar => ZIO.attemptBlocking(jar.getEntry(resourcePath) -> jar))
                  .flatMap { case (entry, jar) => ZStream.fromInputStream(jar.getInputStream(entry)) }
                response      = Response(body = Body.fromStream(inZStream, contentLength))
              } yield mediaType.fold(response) { t =>
                response
                  .addHeader(Header.ContentType(t))
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
