/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Task, Trace, UIO, ZIO}

import zio.http.netty.NettyFutureExecutor

import io.netty.channel.{Channel => JChannel, ChannelFuture => JChannelFuture}

final case class ChannelNetty[-A](
  private val channel: JChannel,
  private val convert: A => Any,
) extends Channel[A] {
  self =>

  private def foreach[S](await: Boolean)(run: JChannel => JChannelFuture)(implicit trace: Trace): Task[Unit] = {
    if (await) NettyFutureExecutor.executed(run(channel))
    else ZIO.attempt(run(channel): Unit)
  }

  override def autoRead(flag: Boolean)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(channel.config.setAutoRead(flag): Unit)

  override def awaitClose(implicit trace: Trace): UIO[Unit] = ZIO.async[Any, Nothing, Unit] { register =>
    channel.closeFuture().addListener((_: JChannelFuture) => register(ZIO.unit))
    ()
  }

  override def close(await: Boolean = false)(implicit trace: Trace): Task[Unit] = foreach(await) { _.close() }

  override def contramap[A1](f: A1 => A): ChannelNetty[A1] = copy(convert = convert.compose(f))

  override def flush(implicit trace: Trace): Task[Unit] = ZIO.attempt(channel.flush(): Unit)

  override def id(implicit trace: Trace): String = channel.id().asLongText()

  override def isAutoRead(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(channel.config.isAutoRead)

  override def read(implicit trace: Trace): UIO[Unit] = ZIO.succeed(channel.read(): Unit)

  override def write(msg: A, await: Boolean = false)(implicit trace: Trace): Task[Unit] = foreach(await) {
    _.write(convert(msg))
  }

  override def writeAndFlush(msg: A, await: Boolean = false)(implicit trace: Trace): Task[Unit] = foreach(await) {
    _.writeAndFlush(convert(msg))
  }
}

object ChannelNetty {
  def make[A](channel: JChannel): ChannelNetty[A] = ChannelNetty(channel, identity)
}
