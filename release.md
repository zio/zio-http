## Highlights
### Breaking Changes
 #### Follow ZIO 2.0 naming conventions
  ```scala
 private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "random" => random.nextString(10).map(Response.text(_))
    case Method.GET -> !! / "utc"    => clock.currentDateTime.map(s => Response.text(s.toString))
  }
```

 #### Removed Type Params from Response
 - Added `wrapZIO` operator on Response
 - Renamed `socket` to `fromSocket` in Response
 - Added `toSocketApp` and `toResponse` on Socket
 ```scala
private val app =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => Response.text(s"Greetings ${name}!").wrapZIO
      case Method.GET -> !! / "subscriptions" => socketApp.toResponse
    }
```

 #### Removed @@ feature for cookies
```scala
private val app = Http.collect[Request] {
    case Method.GET -> !! / "cookie" =>
      Response.ok.addCookie(cookie.withPath(!! / "cookie").withHttpOnly)
}
```
 #### Builder pattern for SocketApp
```scala
private val fooBar = Socket.collect[WebSocketFrame] {
    case WebSocketFrame.Text("FOO") => ZStream.succeed(WebSocketFrame.text("BAR"))
  }

private val socketApp = {
    SocketApp(fooBar) // Called after each message being received on the channel
      // Called after the request is successfully upgraded to websocket
      .onOpen(open)
      // Called after the connection is closed
      .onClose(_ => console.putStrLn("Closed!").ignore)
  }
```

### New APIs
 #### Introduced collectHttp and added fromStream operator to Http
   ```scala
  val app = Http.collectHttp[Request] {
     // Read the file as ZStream
     // Uses the blocking version of ZStream.fromFile
     case Method.GET -> !! / "blocking" => Http.fromStream(ZStream.fromFile(Paths.get("README.md")))
 }
 ```
 #### Added provide method for SocketApp
 ```scala
 private val echo = Socket.collect[WebSocketFrame] { case WebSocketFrame.Text(text) =>
    ZStream.repeat(WebSocketFrame.text(s"Received: $text")).schedule(Schedule.spaced(1 second)).take(3)
  }
  val env = Has.apply(Clock.Service.live)

  private val socketApp: SocketApp[Any] = SocketApp(echo).provide(env)
```

## Changes

- Test/keepalive httpversion @sumawa (#800)
- Doc : update Readme.md with steps to start example server @ashprakasan (#748)
- Performance: Cookie decoder improvements @jgoday (#576)
- Update jwt-core to 9.0.3 @amitksingh1490 (#743)
- Update scalafmt-core to 3.3.1 @scala-steward (#741)
- Update jwt-core to 9.0.3 @scala-steward (#742)
- Feature: add `setHeaders` to `HeaderModifiers.scala` @tusharmath (#745)
- KeepAlive enabling and test cases @sumawa (#792)

## 🚀 Features

- Feature: Added `updateHeader` to `Patch` @ShrutiVerma97 (#779)
- Refactor: Remove type params from Response @tusharmath (#772)
- Refactor: Remove error type from SocketApp @tusharmath (#769)
- Feature: Return `Start` on server start @girdharshubham (#682)
- Feature: CSRF Middleware @smehta91 (#761)
- Refactor: Remove type params from HttpData @tusharmath (#766)
- Feature: Add `provide` operator on SocketApp @tusharmath (#763)
- Refactor: Use builder pattern for SocketApp @tusharmath (#762)
- Refactor: follow ZIO 2.0 naming conventions @tusharmath (#739)
- Revert "feat(CSRF): add middleware for CSRF handling" @tusharmath (#756)
- feat(CSRF): add middleware for CSRF handling @smehta91 (#542)
- Feature: Serve static files Asynchronously @ashprakasan (#706)

## Improvements

- Remove @@ feature for cookies @ShrutiVerma97 (#760)

## 🐛 Bug Fixes

- Fix: getSetCookieDecoded Method @ShrutiVerma97 (#777)
- Scala doc generation CI step @girdharshubham (#771)
-  Fix: Endpoint fix in `to` operator @sumawa (#749)
- Response Json headers @amitksingh1490 (#750)

## 🧰 Maintenance

- Refactor: Add Response Handler @gciuloaica (#727)
- Update sbt-scala3-migrate to 0.5.0 @amitksingh1490 (#785)
- Refactor: Remove type params from Response @tusharmath (#772)
- Refactor: Remove error type from SocketApp @tusharmath (#769)
- Maintenance: remove twirl settings and examples @girdharshubham (#774)
- Scala doc generation CI step @girdharshubham (#771)
- Refactor: Remove type params from HttpData @tusharmath (#766)
- Refactor: follow ZIO 2.0 naming conventions @tusharmath (#739)
- Update scalafmt-core to 3.3.1 @amitksingh1490 (#744)
