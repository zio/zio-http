package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, DefaultLastHttpContent}
import zhttp.http.HttpData.{ByteBufConfig, UnsafeContent, UnsafeReadableChannel}
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion.{anything, equalTo, isLeft, isSubtype}
import zio.test.TestAspect.{failing, timeout}
import zio.test.{DefaultRunnableSpec, Gen, assertM, checkAllM}

import java.io.File
import scala.collection.mutable.Queue
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

object HttpDataSpec extends DefaultRunnableSpec {

  final case class TestUnsafeReadableChannel(promise: Promise[Unit]) extends UnsafeReadableChannel {
    override def read(): Unit = promise.success(())
  }

  final case class TestUnsafeReadableChannel2(queue: Queue[Promise[Unit]]) extends UnsafeReadableChannel {
    override def read(): Unit = {
      if (queue.nonEmpty) {
        val p = queue.dequeue()
        p.success(())
      }
    }
  }

  override def spec =
    suite("HttpDataSpec") {

      val testFile = new File(getClass.getResource("/TestFile.txt").getPath)
      suite("outgoing") {
        suite("encode")(
          suite("fromStream") {
            testM("success") {
              checkAllM(Gen.anyString) { payload =>
                val stringBuffer    = payload.getBytes(HTTP_CHARSET)
                val responseContent = ZStream.fromIterable(stringBuffer)
                val res             = HttpData.fromStream(responseContent).toByteBuf.map(_.toString(HTTP_CHARSET))
                assertM(res)(equalTo(payload))
              }
            }
          },
          suite("fromFile")(
            testM("failure") {
              val res = HttpData.fromFile(throw new Error("Failure")).toByteBuf.either
              assertM(res)(isLeft(isSubtype[Error](anything)))
            },
            testM("success") {
              lazy val file = testFile
              val res       = HttpData.fromFile(file).toByteBuf.map(_.toString(HTTP_CHARSET))
              assertM(res)(equalTo("abc\nfoo"))
            },
            testM("success small chunk") {
              lazy val file = testFile
              val res       = HttpData.fromFile(file).toByteBuf(ByteBufConfig(3)).map(_.toString(HTTP_CHARSET))
              assertM(res)(equalTo("abc\nfoo"))
            },
          ),
          suite("UnsafeAsync")(
            testM("sending last msg should finish the processing with 1 msg.") {
              val unsafeChannel = new UnsafeReadableChannel {
                override def read(): Unit = println("called read.")
              }

              val probe  = HttpData.UnsafeAsync { cb =>
                val producer = cb(unsafeChannel)

                producer(new UnsafeContent(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER)))
              }
              val result = probe.toByteBufStream(ByteBufConfig(4)).runCount
              assertM(result)(equalTo(1L))
            },
            testM("Sending a chunk without last msg the stream will not complete") {
              val unsafeChannel = new UnsafeReadableChannel {
                override def read(): Unit = ()
              }

              val probe  = HttpData.UnsafeAsync { cb =>
                val producer = cb(unsafeChannel)
                producer(
                  new UnsafeContent(new DefaultHttpContent(Unpooled.copiedBuffer("abc".toArray.map(_.toByte)))),
                )
              }
              val result = probe.toByteBufStream(ByteBufConfig(4)).runCount
              assertM(result)(equalTo(1L))
            } @@ timeout(1 seconds) @@ failing,
            testM("sending 2 msg should finish the processing with same number of messages.") {

              val p             = Promise[Unit]()
              val unsafeChannel = TestUnsafeReadableChannel(p)

              val probe  = HttpData.UnsafeAsync { cb =>
                val producer = cb(unsafeChannel)
                producer(
                  new UnsafeContent(new DefaultHttpContent(Unpooled.copiedBuffer("abc".toArray.map(_.toByte)))),
                )
                p.future.onComplete(_ => producer(new UnsafeContent(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))))

              }
              val result = probe.toByteBufStream(ByteBufConfig(4)).runCount
              assertM(result)(equalTo(2L))
            },
            testM("sending multiple msg should finish the processing with same number of messages.") {

              val p1            = Promise[Unit]()
              val p2            = Promise[Unit]()
              val queue         = Queue(p1, p2)
              val unsafeChannel = TestUnsafeReadableChannel2(queue)

              val probe  = HttpData.UnsafeAsync { cb =>
                val producer = cb(unsafeChannel)
                producer(
                  new UnsafeContent(new DefaultHttpContent(Unpooled.copiedBuffer("abc".toArray.map(_.toByte)))),
                )

                p1.future.onComplete(_ =>
                  producer(
                    new UnsafeContent(new DefaultHttpContent(Unpooled.copiedBuffer("def".toArray.map(_.toByte)))),
                  ),
                )
                p2.future.onComplete(_ =>
                  producer(new UnsafeContent(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))),
                )

              }
              val result = probe.toByteBufStream(ByteBufConfig(4)).runCount
              assertM(result)(equalTo(3L))
            },
          ),
        )
      }
    } @@ timeout(10 seconds)
}
