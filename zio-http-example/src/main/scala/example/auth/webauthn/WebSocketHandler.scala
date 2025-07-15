package example.auth.webauthn
import zio.http._
import zio._
import zio.http.ChannelEvent.Read
import zio.json._

// ============================================================================
// WebSocket Handler for Mobile Devices
// ============================================================================

object WebSocketHandler {

  def mobileDeviceHandler(deviceManager: MobileDeviceManager): WebSocketApp[Any] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text(text)) =>
          val effect = for {
            json    <- ZIO
              .fromEither(text.fromJson[MobileDeviceInfo])
              .mapError(e => s"Invalid JSON: $e")
            _       <- deviceManager.registerDevice(json)
            devices <- deviceManager.getConnectedDevices()
            response = devices.toJson
            _ <- channel.send(Read(WebSocketFrame.Text(response)))
          } yield ()

          effect.catchAll { error =>
            channel.send(Read(WebSocketFrame.Text(s"""{"error": "$error"}""")))
          }

        case Read(WebSocketFrame.Close(status, reason)) =>
          Console.printLine(s"WebSocket closed: $status - $reason").ignore

        case _ =>
          ZIO.unit
      }
    }
}
