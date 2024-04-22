---
id: server-sent-events-in-endpoints
title: "Server Sent Events in Endpoints Example"
sidebar_label: "Server Sent Events in Endpoints"
---

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/ServerSentEventEndpoint.scala")
```

**Explanation**

* **Server-Sent Events (SSE) Fundamentals:**
  - SSE is a simple but effective protocol for getting continuous updates from a server. The client keeps a persistent HTTP connection open, and the server can send events as they occur.
  - Each SSE event has a clear structure:
    - `event:` (optional) the event type.
    - `data:` the payload.
    - `id:` (optional) an identifier for the event.
    - Multiple lines are separated by double newlines (`\n\n`).

* **Endpoint Setup**
  -  You're defining the contract of your endpoint:
     - It expects `GET` requests to `/sse`.
     - It doesn't take "normal" input like request parameters.
     - Errors aren't part of the interface since SSE operates on an open stream.
     - The output is a `ZStream[R, Throwable, ServerSentEvent]` - a stream of Server-Sent events.

* **Stream Generation**
  -  ZIO Streams are fantastic for this use case! Here's how your stream works:
     - `ZStream.repeatWithSchedule(...)`: Creates a stream that keeps emitting values based on a schedule you define.
     - `Schedule.spaced(1.second)`: Your schedule is "Emit something every 1 second".
     - `ZIO.succeed(new Date().getTime)`: The thing emitted each second will be the current timestamp.
     - `.map(ServerSentEvent.heartbeat)`: Turns each timestamp into a well-formatted Server-Sent Event (using the "heartbeat" event type here).

* **Endpoint Implementation**
  -  Easy! You have the endpoint contract and the stream; ZIO HTTP takes care of the rest.

* **Server Setup & Execution**
  -  Standard ZIO HTTP approach for starting a server with your application.

**How a Client Interacts**

1.  The client makes a `GET` request to `/sse` on your server.
2.  The server responds with:
    -  `Content-Type: text/event-stream` header.
    -  Keeps the connection alive.
3.  ~Every second, the server sends an event like this:
    ```
    event: heartbeat
    data:  1650713375304  \n\n 
    ```
4.  The client has JavaScript code (often using the `EventSource` API) that listens for these events and can react to the new timestamps.