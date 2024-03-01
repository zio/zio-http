---
id: websockets
title: "Websockets"
---

Reference guide for the WebSocket functionality

WebSocket Server APIs and Classes

- `SocketApp[R]`: Represents a WebSocket application that handles incoming messages and defines the behavior of the server.
  - `Handler.webSocket`: Constructs a WebSocket handler that takes a channel and defines the logic to process incoming messages.

- `WebSocketChannel[R]`: Represents a WebSocket channel that allows reading from and writing to the WebSocket connection.
    
    - `channel.receiveAll`: Handles various incoming WebSocket frames and defines different responses based on the received message.
        
    - `channel.send(Read(WebSocketFrame.text))`: Sends a response back to the client by writing a text frame to the WebSocket channel.

- `WebSocketFrame`: Represents a WebSocket frame.
  
  - `WebSocketFrame.Text(message)`: Represents a text frame with the specified message.
        
  - `WebSocketFrame.Close(status, reason)`: Represents a frame for closing the connection with the specified status and reason.

- `ChannelEvent`: Represents different events that can occur on a WebSocket channel.
    
    - `ChannelEvent.Read(frame)`: Indicates the reading of a WebSocket frame.
        
    - `ChannelEvent.UserEventTriggered(event)`: Indicates a user-triggered event.
        
    - `ChannelEvent.ExceptionCaught(cause)`: Indicates an exception that occurred during WebSocket communication.

### WebSocket Server Usage

1. Create a `SocketApp[R]` instance using `Handler.webSocket` to define the WebSocket application logic.

2. Within the `SocketApp`, use `WebSocketChannel.receiveAll` to handle incoming WebSocket frames and define appropriate responses.

3. Use `WebSocketChannel.send` to write WebSocket frames back to the client.

4. Construct an HTTP application (`Http[Any, Nothing, Request, Response]`) that serves WebSocket connections.

5. Use `Http.collectZIO` to specify routes and associate them with appropriate WebSocket applications.


### WebSocket Client APIs and Classes

1. `makeSocketApp(p: Promise[Nothing, Throwable])`: Constructs a WebSocket application that connects to a specific URL and handles events.
   - `Handler.webSocket`: Constructs a WebSocket handler that takes a channel and defines the logic to process incoming messages.

2. `WebSocketChannel[R]`: Represents a WebSocket channel that allows reading from and writing to the WebSocket connection.
    - `channel.send(Read(WebSocketFrame.text))`: Sends a WebSocket frame to the server by writing it to the channel.

### WebSocket Client Usage

- Create a `makeSocketApp` function that constructs a `SocketApp[R]` instance for the WebSocket client.

- Within the `SocketApp`, use `WebSocketChannel.receiveAll` to handle incoming WebSocket frames and define appropriate responses.

- Use `WebSocketChannel.send` to write WebSocket frames to the server.

- Use `connect(url)` to initiate a WebSocket connection to the specified URL.

- Optionally, use promises or other mechanisms to handle WebSocket errors and implement reconnecting behavior.