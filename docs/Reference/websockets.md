**Adding WebSocket Support to your ZIO HTTP Application**

WebSocket support can be seamlessly integrated into your ZIO HTTP application using the same HTTP domain. Here's an example of how you can add WebSocket functionality:

```scala
import zio.http._
import zio._

val socket = Handler.webSocket { channel =>
  channel.receiveAll {
    case ChannelEvent.Read(WebSocketFrame.Text("foo")) =>
      channel.send(ChannelEvent.Read(WebSocketFrame.text("bar")))
    case _ =>
      ZIO.unit
  }
}

val http = Http.collectZIO[Request] {
  case Method.GET -> Root / "subscriptions" => socket.toResponse
}
```

In this example, we define a `socket` by using the `Handler.webSocket` method. This method takes a function that receives a `WebSocketChannel` and returns a `ZIO` effect. Inside this function, we can define the behavior of the WebSocket channel.

The `channel.receiveAll` method is used to handle incoming messages from the WebSocket client. In this case, we check if the received message is `"foo"` and respond with a message `"bar"`. Any other message is ignored.

The `http` value represents your ZIO HTTP application. We use the `Http.collectZIO` combinator to handle incoming HTTP requests. In this example, we match the request pattern `Method.GET -> Root / "subscriptions"` and return the `socket.toResponse`, which converts the WebSocket channel to an HTTP response.

**Channel and ChannelEvents**

A channel is created on both the server and client sides whenever a connection is established between them. It provides a low-level API to send and receive arbitrary messages.

When a HTTP connection is upgraded to WebSocket, a specialized channel is created that only allows WebSocket frames to be sent and received. The WebSocketChannel is a subtype of the generic Channel API and provides specific methods for WebSocket communication.

ChannelEvents represent immutable, type-safe events that occur on a channel. These events are wrapped in the `ChannelEvent` type. For WebSocket channels, the type alias `WebSocketChannelEvent` is used, which represents events containing WebSocketFrame messages.

**Using Http.collect**

The `Http.collect` combinator allows you to select the events you are interested in for your specific use case. In the example, we use `Http.collectZIO` to handle ZIO effects. We match the desired events, such as `ChannelEvent.Read`, to perform custom logic based on the received WebSocket frames.

Other lifecycle events, such as `ChannelRegistered` and `ChannelUnregistered`, can also be hooked into for different use cases.

By leveraging the ZIO HTTP domain, you can build WebSocket applications using the same powerful abstractions provided for regular HTTP handling.

Please note that the example provided focuses on integrating WebSocket functionality into your ZIO HTTP application. Make sure to adapt it to your specific requirements and implement the necessary logic for handling WebSocket events and messages.
