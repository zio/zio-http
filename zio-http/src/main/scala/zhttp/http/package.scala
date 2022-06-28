package zhttp

import io.netty.util.CharsetUtil
import zio.{Chunk, Queue, Trace, UIO, ZIO}

import java.nio.charset.Charset

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {
  type HttpApp[-R, +E]                       = Http[R, E, Request, Response]
  type UHttpApp                              = HttpApp[Any, Nothing]
  type RHttpApp[-R]                          = HttpApp[R, Throwable]
  type UHttp[-A, +B]                         = Http[Any, Nothing, A, B]
  type ResponseZIO[-R, +E]                   = ZIO[R, E, Response]
  type Header                                = (CharSequence, CharSequence)
  type UMiddleware[+AIn, -BIn, -AOut, +BOut] = Middleware[Any, Nothing, AIn, BIn, AOut, BOut]

  /**
   * Default HTTP Charset
   */
  val HTTP_CHARSET: Charset = CharsetUtil.UTF_8

  object HeaderNames  extends headers.HeaderNames
  object HeaderValues extends headers.HeaderValues

  implicit class QueueWrapper[A](queue: Queue[A]) {
    def mapM[B](f: A => UIO[B]): Queue[B] = {
      new Queue[B] { self =>
        override def awaitShutdown(implicit trace: Trace): UIO[Unit] = queue.awaitShutdown

        override def capacity: Int = queue.capacity

        override def isShutdown(implicit trace: Trace): UIO[Boolean] = queue.isShutdown

        override def offer(b: B)(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(b).flatMap(b => self.offer(b))

        override def offerAll[A1 <: B](as: Iterable[A1])(implicit trace: zio.Trace): UIO[Chunk[A1]] =
          ZIO.foreach(as)(b => ZIO.succeed(b)).flatMap(t => self.offerAll(t))

        override def shutdown(implicit trace: Trace): UIO[Unit] = queue.shutdown

        override def size(implicit trace: Trace): UIO[Int] = queue.size

        override def take(implicit trace: Trace): UIO[B] = queue.take.flatMap(a => f(a))

        override def takeAll(implicit trace: Trace): UIO[Chunk[B]] = queue.takeAll.flatMap(ZIO.foreach(_)(f))

        override def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[B]] =
          queue.takeUpTo(max).flatMap(ZIO.foreach(_)(f))
      }
    }
  }
}
