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

package zio.http.netty

import zio.{Task, Trace, UIO, ZIO}

import zio.http.Channel

import io.netty.channel.{Channel => JChannel, ChannelFuture => JChannelFuture}

final case class NettyChannel[-A](
  private val channel: JChannel,
  private val convert: A => Any,
) {
  self =>

  private def foreach[S](await: Boolean)(run: JChannel => JChannelFuture)(implicit trace: Trace): Task[Unit] = {
    if (await) NettyFutureExecutor.executed(run(channel))
    else ZIO.attempt(run(channel): Unit)
  }

  def autoRead(flag: Boolean)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(channel.config.setAutoRead(flag): Unit)

  def awaitClose(implicit trace: Trace): UIO[Unit] = ZIO.async[Any, Nothing, Unit] { register =>
    channel.closeFuture().addListener((_: JChannelFuture) => register(ZIO.unit))
    ()
  }

  def close(await: Boolean = false)(implicit trace: Trace): Task[Unit] = foreach(await) { _.close() }

  def contramap[A1](f: A1 => A): NettyChannel[A1] = copy(convert = convert.compose(f))

  def flush(implicit trace: Trace): Task[Unit] = ZIO.attempt(channel.flush(): Unit)

  def id(implicit trace: Trace): String = channel.id().asLongText()

  def isAutoRead(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(channel.config.isAutoRead)

  def read(implicit trace: Trace): UIO[Unit] = ZIO.succeed(channel.read(): Unit)

  def write(msg: A, await: Boolean = false)(implicit trace: Trace): Task[Unit] = foreach(await) {
    _.write(convert(msg))
  }

  def writeAndFlush(msg: A, await: Boolean = true)(implicit trace: Trace): Task[Unit] = foreach(await) { x =>
    try {
      println("about to write and flush")
      val fut = x.writeAndFlush(convert(msg))
      println("write and flush done")
      fut
    } catch {
      case t: Throwable => t.printStackTrace(); throw t
    }
  }
}

object NettyChannel {
  def make[A](channel: JChannel): NettyChannel[A] = NettyChannel(channel, identity)
}
