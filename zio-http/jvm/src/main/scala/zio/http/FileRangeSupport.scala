package zio.http

import zio._
import zio.stream.ZStream
import java.io.{File, RandomAccessFile}
import java.nio.file.{Files, Path}

/**
 * HTTP Range request support for file serving (RFC 7233)
 */
object FileRangeSupport {
  
  def serveFile(
    file: File,
    request: Request,
    mediaType: Option[MediaType] = None
  ): Response = {
    serveFilePath(file.toPath, request, mediaType)
  }
  
  def serveFilePath(
    path: Path,
    request: Request,
    mediaType: Option[MediaType]
  ): Response = {
    if (!Files.exists(path) || !Files.isRegularFile(path)) {
      Response.notFound
    } else {
      val fileLength = Files.size(path)
      
      request.header(Header.Range) match {
        case Some(rangeHeader) =>
          handleRangeRequest(path, fileLength, rangeHeader, mediaType)
        case None =>
          serveCompleteFile(path, fileLength, mediaType)
      }
    }
  }
  
  private def serveCompleteFile(
    path: Path,
    fileLength: Long,
    mediaType: Option[MediaType]
  ): Response = {
    val stream = ZStream.fromFile(path.toFile)
    val contentType = mediaType.getOrElse(detectMediaType(path))
    
    Response(
      status = Status.Ok,
      headers = Headers(
        Header.ContentType(contentType),
        Header.ContentLength(fileLength),
        Header.AcceptRanges.Bytes
      ),
      body = Body.fromStream(stream)
    )
  }
  
  private def handleRangeRequest(
    path: Path,
    fileLength: Long,
    rangeHeader: Header.Range,
    mediaType: Option[MediaType]
  ): Response = {
    rangeHeader match {
      case Header.Range.Single(unit, start, endOpt) if unit == "bytes" =>
        val end = endOpt.getOrElse(fileLength - 1)
        
        if (start >= fileLength || start > end) {
          rangeNotSatisfiable(fileLength)
        } else {
          val actualEnd = math.min(end, fileLength - 1)
          serveSingleRange(path, fileLength, start, actualEnd, mediaType)
        }
        
      case Header.Range.Suffix(unit, length) if unit == "bytes" =>
        val start = math.max(0L, fileLength - length)
        val end = fileLength - 1
        serveSingleRange(path, fileLength, start, end, mediaType)
        
      case Header.Range.Prefix(unit, length) if unit == "bytes" =>
        if (length == 0 || length > fileLength) {
          rangeNotSatisfiable(fileLength)
        } else {
          serveSingleRange(path, fileLength, 0, length - 1, mediaType)
        }
        
      case Header.Range.Multiple(unit, ranges) if unit == "bytes" =>
        val validRanges = ranges.flatMap { case (start, endOpt) =>
          if (start >= fileLength) None
          else {
            val end = endOpt.map(e => math.min(e, fileLength - 1)).getOrElse(fileLength - 1)
            if (start <= end) Some((start, end)) else None
          }
        }
        
        if (validRanges.isEmpty) {
          rangeNotSatisfiable(fileLength)
        } else if (validRanges.size == 1) {
          val (start, end) = validRanges.head
          serveSingleRange(path, fileLength, start, end, mediaType)
        } else {
          serveMultipleRanges(path, fileLength, validRanges, mediaType)
        }
        
      case _ =>
        serveCompleteFile(path, fileLength, mediaType)
    }
  }
  
  private def serveSingleRange(
    path: Path,
    fileLength: Long,
    start: Long,
    end: Long,
    mediaType: Option[MediaType]
  ): Response = {
    val contentLength = end - start + 1
    val stream = readFileRange(path, start, contentLength)
    val contentType = mediaType.getOrElse(detectMediaType(path))
    
    Response(
      status = Status.PartialContent,
      headers = Headers(
        Header.ContentType(contentType),
        Header.ContentLength(contentLength),
        Header.ContentRange.EndTotal("bytes", start.toInt, end.toInt, fileLength.toInt),
        Header.AcceptRanges.Bytes
      ),
      body = Body.fromStream(stream)
    )
  }
  
  private def serveMultipleRanges(
    path: Path,
    fileLength: Long,
    ranges: List[(Long, Long)],
    mediaType: Option[MediaType]
  ): Response = {
    val boundary = generateBoundary()
    val contentType = mediaType.getOrElse(detectMediaType(path))
    
    val parts = ranges.map { case (start, end) =>
      val rangeLength = end - start + 1
      val rangeStream = readFileRange(path, start, rangeLength)
      
      val partHeaders = 
        s"--$boundary\r\n" +
        s"Content-Type: ${contentType.fullType}\r\n" +
        s"Content-Range: bytes $start-$end/$fileLength\r\n\r\n"
      
      ZStream.fromIterable(partHeaders.getBytes("UTF-8")) ++
        rangeStream ++
        ZStream.fromIterable("\r\n".getBytes("UTF-8"))
    }
    
    val closingBoundary = ZStream.fromIterable(s"--$boundary--\r\n".getBytes("UTF-8"))
    val combinedStream = parts.foldLeft(ZStream.empty: ZStream[Any, Throwable, Byte])(_ ++ _) ++ closingBoundary
    
    Response(
      status = Status.PartialContent,
      headers = Headers(
        Header.Custom("Content-Type", s"multipart/byteranges; boundary=$boundary"),
        Header.AcceptRanges.Bytes
      ),
      body = Body.fromStream(combinedStream)
    )
  }
  
  private def rangeNotSatisfiable(fileLength: Long): Response = {
    Response(
      status = Status.RangeNotSatisfiable,
      headers = Headers(
        Header.ContentRange.RangeTotal("bytes", fileLength.toInt),
        Header.AcceptRanges.Bytes
      )
    )
  }
  
  private def readFileRange(path: Path, start: Long, length: Long): ZStream[Any, Throwable, Byte] = {
    ZStream.unwrap {
      ZIO.attemptBlocking {
        val raf = new RandomAccessFile(path.toFile, "r")
        raf.seek(start)
        
        ZStream.fromInputStream(new java.io.InputStream {
          private var remaining = length
          
          override def read(): Int = {
            if (remaining <= 0) -1
            else {
              remaining -= 1
              raf.read()
            }
          }
          
          override def read(b: Array[Byte], off: Int, len: Int): Int = {
            if (remaining <= 0) -1
            else {
              val toRead = math.min(len, remaining.toInt)
              val bytesRead = raf.read(b, off, toRead)
              if (bytesRead > 0) remaining -= bytesRead
              bytesRead
            }
          }
          
          override def close(): Unit = raf.close()
        })
      }
    }.flatten
  }
  
  private def generateBoundary(): String =
    s"ZIO_HTTP_BOUNDARY_${System.currentTimeMillis()}_${scala.util.Random.nextInt(100000)}"
  
  private def detectMediaType(path: Path): MediaType = {
    val fileName = path.getFileName.toString
    val extension = fileName.split("\\.").lastOption.getOrElse("")
    MediaType.forFileExtension(extension).getOrElse(MediaType.application.`octet-stream`)
  }
}
