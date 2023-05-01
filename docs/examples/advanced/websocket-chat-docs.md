| id               | title                    | sidebar_label      |
| ---------------- | ------------------------ | ------------------ |
| websocket-server | WebSocket Server Example | zio-websocket-chat |

<br>

# **Zio WebSocket Chat Documentation**

This example code implements a WebSocket server that echoes messages sent to it by clients. It's built on top of zio-http, which provides an easy-to-use API for building HTTP and WebSocket servers in ZIO.
<br>
<br>

## **How it works**

The **WebSocketEcho** object extends **ZIOAppDefault**, which provides a convenient way to run a ZIO application with minimal boilerplate. The _portConfig_ value reads the environment variable "port" and maps it to an integer. If the mapping fails, it defaults to the port 9009.

```scala
object WebSocketEcho extends ZIOAppDefault {
    private val portConfig = System.env("port").map(_.flatMap(_.toIntOption).getOrElse(9009))
    ...

```

<br>
<br>
<hr>

### **chat** function

- The _chat_ function takes a reference to a map of WebSocket channels and a name string, and returns an _Http_ route that handles WebSocket connections for the given name.

```scala
private def chat(socketsRef: Ref[Map[String, Channel[WebSocketFrame]]], name: String) =
    ...

```

<br>

- The _broadcast_ function sends a message to all connected websockets _except the channel where it came from_. It does this by getting a reference to the current _sockets_, filtering out the from channel, and then using _ZIO.foreachParDiscard_ to write the message to all remaining channels. The function returns a _ZIO_ effect that can be run to execute the broadcast.

```scala
private def broadcast(from: Channel[WebSocketFrame], msg: String) =
  for
    sockets <- socketsRef.get
    _       <- ZIO.foreachParDiscard(sockets.filterNot(_._1 == from.id).values) { c =>
                 c.writeAndFlush(WebSocketFrame.text(msg))
               }
  yield ()

```

<br>

- This is a ZIO Http route that handles WebSocket events. It pattern matches WebSocketChannelEvents and handles them appropriately. On HandshakeComplete, it broadcasts a message that a new client has joined, sends a welcome message, and updates the socket references. On ChannelRead, it broadcasts the message sent by the client. On ChannelUnregistered, it removes the socket reference and broadcasts that the client has left.

```scala
Http.collectZIO[WebSocketChannelEvent] {
  case ChannelEvent(ch, UserEventTriggered(event))             =>
    event match
      case HandshakeTimeout  =>
        ZIO.logInfo("Connection failed!")
      case HandshakeComplete =>
        broadcast(ch, s"$name joined") *>
          ch.writeAndFlush(WebSocketFrame.text(s"Hello $name")) *>
          socketsRef.update(ss => ss + (ch.id -> ch))
  case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(msg))) =>
    broadcast(ch, s"$name says: $msg")
  case ChannelEvent(ch, ChannelUnregistered)                   =>
    socketsRef.update(ss => ss - ch.id) *>
      broadcast(ch, s"$name left")
}

```

  <br>
  <br>
  <hr>

### **chatRoom** function

This function creates an HTTP endpoint that listens to a WebSocket connection. It takes a _Ref_ of _Map_ containing _Channel_'s of _WebSocketFrame_. It uses the _Http.collectZIO_ combinator to match incoming requests, and then calls the _chat_ function with the _Ref_ and the _name_ parameter. Finally, it converts the _chat_ function to a _SocketApp_ and wraps it in an _Http_ response.

```scala
private def chatRoom(socketsRef: Ref[Map[String, Channel[WebSocketFrame]]]): Http[Any, Nothing, Request, Response] =
  Http.collectZIO[Request] { case Method.GET -> !! / "chat" / name =>
    chat(socketsRef, name).toSocketApp.toResponse
  }

```

<br>
<br>
<hr>

### **run** function

This code defines the _run_ function that starts the HTTP server on a specified _port_. It initializes a reference to the WebSocket sockets using _Ref.make()_, and then starts the _Server_ using the _chatRoom()_ function and the _socketsRef_. The _run_ function returns _ExitCode.success_.

```scala
override val run: ZIO[Any, Throwable, ExitCode] = for
  port       <- portConfig
  _          <- Console.printLine(s"Starting server on http://localhost:$port")
  socketsRef <- Ref.make(Map[String, Channel[WebSocketFrame]]())
  _          <- Server.start(port, chatRoom(socketsRef))
yield ExitCode.success

```

<br>
<br>
<hr>

## **CODE**

<details>
<br>

```scala
import zio.*
import zio.Console.printLine
import zhttp.http.*
import zhttp.service.*
import zhttp.service.ChannelEvent.UserEvent.{ HandshakeComplete, HandshakeTimeout }
import zhttp.service.ChannelEvent.{ ChannelRead, ChannelUnregistered, UserEventTriggered }
import zhttp.socket.*
import zio.*
import zio.stream.ZStream

object WebSocketEcho extends ZIOAppDefault {

private val portConfig = System.env("port").map(_.flatMap(_.toIntOption).getOrElse(9009))

private def chat(socketsRef: Ref[Map[String, Channel[WebSocketFrame]]], name: String) =
def broadcast(from: Channel[WebSocketFrame], msg: String) =
for
sockets <- socketsRef.get
_ <- ZIO.foreachParDiscard(sockets.filterNot(_.\_1 == from.id).values) { c =>
c.writeAndFlush(WebSocketFrame.text(msg))
}
yield ()

    Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, UserEventTriggered(event))             =>
        event match
          case HandshakeTimeout  =>
            ZIO.logInfo("Connection failed!")
          case HandshakeComplete =>
            broadcast(ch, s"$name joined") *>
              ch.writeAndFlush(WebSocketFrame.text(s"Hello $name")) *>
              socketsRef.update(ss => ss + (ch.id -> ch))
      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(msg))) =>
        broadcast(ch, s"$name says: $msg")
      case ChannelEvent(ch, ChannelUnregistered)                   =>
        socketsRef.update(ss => ss - ch.id) *>
          broadcast(ch, s"$name left")
    }

private def chatRoom(socketsRef: Ref[Map[String, Channel[WebSocketFrame]]]): Http[Any, Nothing, Request, Response] =
Http.collectZIO[Request] { case Method.GET -> !! / "chat" / name =>
chat(socketsRef, name).toSocketApp.toResponse
}

override val run: ZIO[Any, Throwable, ExitCode] = for
port <- portConfig
_ <- Console.printLine(s"Starting server on http://localhost:$port")
socketsRef <- Ref.make(Map[String, Channel[WebSocketFrame]]())
_ <- Server.start(port, chatRoom(socketsRef))
yield ExitCode.success

}

```

  <summary>Click to expand the code snippet to view the entire code.</summary>
</details>
