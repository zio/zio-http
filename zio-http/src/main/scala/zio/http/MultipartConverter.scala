package zio.http

import io.netty.handler.codec.http.multipart._
import zio.{RIO, Scope, ZIO}
import zio.http.model.Multipart
import zio.http.model.Multipart.{Attribute, FileUpload}
import zio.stream.ZStream

import java.nio.file.Paths

object MultipartConverter {

  def convert(data: InterfaceHttpData): RIO[Scope, Multipart] = data match {

    case attribute: io.netty.handler.codec.http.multipart.Attribute =>
      val value: RIO[Scope, String] = attribute match {
        case a: DiskAttribute   => ZIO.attempt(a.getValue)
        case a: MemoryAttribute => ZIO.succeed(a.getValue)
        case a: MixedAttribute  =>
          // TODO should I just use this instead of pattern matching?
          if (a.isInMemory)
            ZIO.succeed(a.getValue)
          else
            ZIO.attempt(a.getValue)
      }

      value.map(v =>
        Attribute(
          attribute.getName,
          attribute.length,
          attribute.definedLength,
          attribute.getCharset.name,
          v,
        ),
      )

    case fileUpload: io.netty.handler.codec.http.multipart.FileUpload =>
      val value: ZStream[Any, Throwable, Byte] = fileUpload match {
        case u: DiskFileUpload   =>
          ZStream.fromPath(u.getFile.toPath)
        case u: MemoryFileUpload =>
          ZStream.fromIterable(u.get)
        case u: MixedFileUpload  =>
          // TODO should I just use this instead of pattern matching?
          if (u.isInMemory)
            ZStream.fromIterable(u.get)
          else {
            println(s"FFF ${u.getFile.toPath}")
            ZStream.fromPath(u.getFile.toPath)
          }
      }
      ZIO.succeed(
        FileUpload(
          fileUpload.getName,
          fileUpload.length,
          fileUpload.definedLength,
          fileUpload.getCharset.name,
          fileUpload.getFilename,
          fileUpload.getContentType,
          fileUpload.getContentTransferEncoding,
          value,
        ),
      )
  }

}
