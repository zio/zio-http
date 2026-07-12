package zio.http.h2

import java.io.{EOFException, IOException, InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import scala.annotation.experimental
import scala.util.control.NonFatal

import zio.blocks.chunk.Chunk
import zio.blocks.mux.{Mux, MuxError, MuxStream}

@experimental
final class H2Connection(
  input: InputStream,
  output: OutputStream,
  maxConcurrentStreams: Int = 100,
) {
  import H2Connection._
  import H2Frame._

  private val mux                      = Mux[Int, H2Frame, H2Frame](maxConcurrentStreams)
  private val closed                   = new AtomicBoolean(false)
  private val activeStreams            = new ConcurrentHashMap[Int, MuxStream[Int, H2Frame, H2Frame]]()
  private val writeLock                = new Object
  private var readBuffer: Chunk[Byte]  = Chunk.empty
  private var peerSettings: List[Setting] = Nil
  private var pendingHeaders: PendingHeaders = null
  @volatile private var settingsAcknowledged = false
  @volatile private var highestStreamId      = 0
  @volatile private var lastGoAwayStreamId   = Int.MaxValue
  @volatile private var connectionWindow     = H2Settings.DefaultInitialWindowSize.toInt

  def run(onStream: MuxStream[Int, H2Frame, H2Frame] => Unit): Unit = {
    val writer = Thread.ofVirtual().name("zio-http-h2-writer").start(runnable(writerLoop()))

    try {
      readConnectionPreface()
      writeFrame(Settings(ack = false, H2Settings.DefaultSettings), flush = true)

      readFrame() match {
        case Settings(false, settings) =>
          peerSettings = settings
          writeFrame(Settings(ack = true, Nil), flush = true)
        case other                     => throw protocolError("Expected client SETTINGS after preface, received: " + other)
      }

      while (!closed.get()) handleFrame(readFrame(), onStream)
    } catch {
      case _: EOFException  => ()
      case _: IOException   => ()
      case NonFatal(error)  =>
        shutdown(connectionCancelled("failure: " + error.getMessage))
        throw error
    } finally {
      shutdown(connectionCancelled("closed"))
      writer.interrupt()
      try writer.join()
      catch {
        case _: InterruptedException => Thread.currentThread().interrupt()
      }
    }
  }

  private def handleFrame(frame: H2Frame, onStream: MuxStream[Int, H2Frame, H2Frame] => Unit): Unit = {
    if (pendingHeaders != null) {
      frame match {
        case continuation: Continuation if continuation.streamId == pendingHeaders.streamId =>
          val next = pendingHeaders.append(continuation)
          if (continuation.endHeaders) {
            pendingHeaders = null
            deliverStreamFrame(next.toHeaders, onStream)
          } else pendingHeaders = next
        case _                                                                  =>
          throw protocolError("Expected CONTINUATION for stream " + pendingHeaders.streamId + ", received: " + frame)
      }
    } else {
      frame match {
        case headers: Headers if !headers.endHeaders => pendingHeaders = PendingHeaders(headers)
        case headers: Headers                        => deliverStreamFrame(headers, onStream)
        case _: Continuation                         => throw protocolError("Unexpected CONTINUATION frame without open header block")
        case other if other.streamId == 0           => handleConnectionFrame(other)
        case other                                  => deliverStreamFrame(other, onStream)
      }
    }
  }

  private def handleConnectionFrame(frame: H2Frame): Unit =
    frame match {
      case Settings(false, settings) =>
        peerSettings = settings
        writeFrame(Settings(ack = true, Nil), flush = true)
      case Settings(true, _)         => settingsAcknowledged = true
      case Ping(false, data)         => writeFrame(Ping(ack = true, data), flush = true)
      case Ping(true, _)             => ()
      case GoAway(lastStreamId, _, _) =>
        lastGoAwayStreamId = lastStreamId
        closed.set(true)
      case WindowUpdate(0, increment) =>
        connectionWindow = checkedIncrement(connectionWindow, increment)
      case _                           =>
        throw protocolError("Unexpected connection-level frame: " + frame)
    }

  private def deliverStreamFrame(frame: H2Frame, onStream: MuxStream[Int, H2Frame, H2Frame] => Unit): Unit = {
    val stream = frame match {
      case headers: Headers if isNewClientStream(headers.streamId) => openStream(headers.streamId, onStream)
      case _                                                       => existingStream(frame.streamId)
    }

    offerInbound(stream, frame)

    frame match {
      case _: RstStream           =>
        stream.close()
        activeStreams.remove(frame.streamId)
      case data: Data if data.endStream =>
        stream.signalRemoteClose()
        if (stream.isClosed) activeStreams.remove(frame.streamId)
      case headers: Headers if headers.endStream =>
        stream.signalRemoteClose()
        if (stream.isClosed) activeStreams.remove(frame.streamId)
      case _                      => ()
    }
  }

  private def openStream(
    streamId: Int,
    onStream: MuxStream[Int, H2Frame, H2Frame] => Unit,
  ): MuxStream[Int, H2Frame, H2Frame] = {
    if ((streamId & 1) == 0 || streamId <= highestStreamId)
      throw protocolError("Invalid client-initiated stream id: " + streamId)

    val stream = toStream(mux.open(streamId))
    highestStreamId = streamId
    activeStreams.put(streamId, stream)

    Thread.ofVirtual().name("zio-http-h2-stream-" + streamId).start(runnable {
      var completed = false
      try {
        onStream(stream)
        completed = true
      }
      finally {
        if (!completed) {
          if (!stream.isClosed) stream.close()
          activeStreams.remove(streamId)
        }
      }
    })

    stream
  }

  private def existingStream(streamId: Int): MuxStream[Int, H2Frame, H2Frame] =
    mux.get(streamId).getOrElse(throw protocolError("Unknown stream id: " + streamId))

  private def writerLoop(): Unit =
    try {
      while (!closed.get() || !activeStreams.isEmpty) {
        var wrote = false
        val it    = activeStreams.values().iterator()

        while (it.hasNext) {
          val stream = it.next()
          var drain  = true

          while (drain) {
            toOptionalFrame(stream.takeOutbound()) match {
              case Left(_: MuxError)      =>
                activeStreams.remove(stream.id)
                drain = false
              case Right(Some(frame))     =>
                writeFrame(frame, flush = false)
                wrote = true
                markLocalFrameWrite(stream, frame)
              case Right(None)            => drain = false
            }
          }

          if (stream.isClosed) activeStreams.remove(stream.id)
        }

        if (wrote) flushOutput()
        else parkWriter()
      }
    } catch {
      case _: IOException => shutdown(connectionCancelled("writer I/O failure"))
    }

  private def markLocalFrameWrite(stream: MuxStream[Int, H2Frame, H2Frame], frame: H2Frame): Unit = {
    frame match {
      case data: Data if data.endStream       => stream.halfClose()
      case headers: Headers if headers.endStream => stream.halfClose()
      case _: RstStream                       => stream.close()
      case _                                  => ()
    }
    if (stream.isClosed) activeStreams.remove(stream.id)
  }

  private def readConnectionPreface(): Unit = {
    val preface = new Array[Byte](ClientPreface.length)
    readFully(preface, 0, preface.length)
    if (!java.util.Arrays.equals(preface, ClientPreface))
      throw protocolError("Invalid HTTP/2 client preface")
  }

  private def readFrame(): H2Frame = {
    while (true) {
      FrameCodec.decode(readBuffer) match {
        case Right((frame, rest))         =>
          readBuffer = rest
          return frame
        case Left(H2Error.InsufficientData) => appendInput()
        case Left(error)                  => throw protocolError("Failed to decode frame: " + error)
      }
    }
    throw protocolError("Unreachable frame read state")
  }

  private def appendInput(): Unit = {
    val bytes = new Array[Byte](8192)
    val read  = input.read(bytes)
    if (read < 0) throw new EOFException("Connection closed while reading HTTP/2 frame")
    readBuffer = readBuffer ++ Chunk.fromArray(java.util.Arrays.copyOf(bytes, read))
  }

  private def readFully(bytes: Array[Byte], offset: Int, length: Int): Unit = {
    var total = 0
    while (total < length) {
      val read = input.read(bytes, offset + total, length - total)
      if (read < 0) throw new EOFException("Connection closed while reading HTTP/2 preface")
      total += read
    }
  }

  private def writeFrame(frame: H2Frame, flush: Boolean): Unit = {
    val bytes = FrameCodec.encode(frame).toArray
    writeLock.synchronized {
      output.write(bytes)
      if (flush) output.flush()
    }
  }

  private def flushOutput(): Unit =
    writeLock.synchronized(output.flush())

  private def offerInbound(stream: MuxStream[Int, H2Frame, H2Frame], frame: H2Frame): Unit =
    toUnit(stream.offerInbound(frame)).foreach(error => throw protocolError("Failed to deliver frame to stream " + stream.id + ": " + error))

  private def isNewClientStream(streamId: Int): Boolean =
    mux.get(streamId).isEmpty && streamId > 0

  private def shutdown(reason: MuxError): Unit =
    if (closed.compareAndSet(false, true)) {
      mux.closeAll(reason)
      closeQuietly(input)
      closeQuietly(output)
    }
    else {
      mux.closeAll(reason)
      closeQuietly(input)
      closeQuietly(output)
    }

  private def parkWriter(): Unit =
    try Thread.sleep(1L)
    catch {
      case _: InterruptedException => Thread.currentThread().interrupt()
    }
}

@experimental
private object H2Connection {
  private val ClientPreface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

  private final case class PendingHeaders(
    streamId: Int,
    headerBlock: Chunk[Byte],
    endStream: Boolean,
    priority: Option[Priority],
    padLength: Int,
  ) {
    def append(frame: H2Frame.Continuation): PendingHeaders =
      copy(headerBlock = headerBlock ++ frame.headerBlock)

    def toHeaders: H2Frame.Headers =
      H2Frame.Headers(streamId, headerBlock, endStream, endHeaders = true, priority, padLength)
  }

  private object PendingHeaders {
    def apply(frame: H2Frame.Headers): PendingHeaders =
      PendingHeaders(frame.streamId, frame.headerBlock, frame.endStream, frame.priority, frame.padLength)
  }

  private def runnable(body: => Unit): Runnable =
    new Runnable {
      def run(): Unit = body
    }

  private def protocolError(message: String): IOException =
    new IOException(message)

  private def checkedIncrement(current: Int, increment: Int): Int = {
    val next = current.toLong + increment.toLong
    if (next > Int.MaxValue.toLong) throw protocolError("HTTP/2 connection window exceeded 2^31-1")
    next.toInt
  }

  private def toStream(result: Any): MuxStream[Int, H2Frame, H2Frame] =
    result match {
      case stream: MuxStream[_, _, _] => stream.asInstanceOf[MuxStream[Int, H2Frame, H2Frame]]
      case Right(stream)              => stream.asInstanceOf[MuxStream[Int, H2Frame, H2Frame]]
      case Left(error: MuxError)      => throw protocolError("Mux open failed: " + error)
      case error: MuxError            => throw protocolError("Mux open failed: " + error)
      case other                      => throw protocolError("Unexpected mux open result: " + other)
    }

  private def toUnit(result: Any): Option[MuxError] =
    result match {
      case Right(_)             => None
      case Left(error: MuxError) => Some(error)
      case error: MuxError       => Some(error)
      case _                    => None
    }

  private def toOptionalFrame(result: Any): Either[MuxError, Option[H2Frame]] =
    result match {
      case Right(value)          => Right(value.asInstanceOf[Option[H2Frame]])
      case Left(error: MuxError) => Left(error)
      case error: MuxError       => Left(error)
      case value: Option[_]      => Right(value.asInstanceOf[Option[H2Frame]])
      case other                 => Left(MuxError.ProtocolError("Unexpected mux frame result: " + other))
    }

  private def closeQuietly(resource: AutoCloseable): Unit =
    if (resource != null) {
      try resource.close()
      catch {
        case NonFatal(_) => ()
      }
    }

  private def connectionCancelled(reason: String): MuxError =
    MuxError.Cancelled("connection", reason)
}
