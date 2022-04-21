package zhttp.http

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, DefaultLastHttpContent}
import zhttp.http.HttpData.{ByteBufConfig, UnsafeContent, UnsafeReadableChannel}
import zio.duration.durationInt
import zio.random.Random
import zio.stream.ZStream
import zio.test.Assertion.{anything, equalTo, isLeft, isSubtype}
import zio.test.TestAspect.{failing, timeout}
import zio.test._

import java.io.File
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

object HttpDataSpec extends DefaultRunnableSpec {

  final case class TestUnsafeReadableChannel(promise: Promise[Unit]) extends UnsafeReadableChannel {
    override def read(): Unit = promise.success(())
  }

  final case class QueueBasedUnsafeReadableChannel(queue: mutable.Queue[Promise[Unit]]) extends UnsafeReadableChannel {
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
            testM("finish on last message.") {
              val unsafeChannel = new UnsafeReadableChannel {
                override def read(): Unit = ()
              }

              val probe  = HttpData.UnsafeAsync { cb =>
                val producer = cb(unsafeChannel)

                producer(new UnsafeContent(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER)))
              }
              val result = probe.toByteBufStream(ByteBufConfig(4)).runCount
              assertM(result)(equalTo(1L))
            },
            testM("wait for last message") {
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
            testM("process all messages.") {
              checkM(unsafeAsyncContent) { case (probe, probeLength) =>
                val result = probe.toByteBufStream(ByteBufConfig(4)).runCount
                assertM(result)(equalTo(probeLength))
              }
            },
          ),
        )
      }
    } @@ timeout(10 seconds)

  private val lastHttpContent = new UnsafeContent(new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER))
  private def unsafeContent: Gen[Random with Sized, List[UnsafeContent]] =
    for {
      content <- Gen.listOf(Gen.alphaNumericString)
    } yield content.map(c => new UnsafeContent(new DefaultHttpContent(Unpooled.copiedBuffer(c.toArray.map(_.toByte)))))

  private def unsafeAsyncContent: Gen[Random with Sized, (HttpData.UnsafeAsync, Long)] = {
    unsafeContent.map { content =>
      (
        HttpData.UnsafeAsync { cb =>
          val queue         = mutable.Queue.empty[Promise[Unit]]
          val unsafeChannel = QueueBasedUnsafeReadableChannel(queue)
          val producer      = cb(unsafeChannel)

          if (content.isEmpty) producer(lastHttpContent)
          else {
            producer(content.head)
            (content.drop(1) :+ lastHttpContent).foreach { c =>
              val p = Promise[Unit]()
              p.future.onComplete(_ => producer(c))
              queue.enqueue(p)
            }
          }
        },
        content.size.toLong + 1,
      )

    }
  }
}
