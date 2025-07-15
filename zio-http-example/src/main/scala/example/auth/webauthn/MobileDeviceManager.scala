package example.auth.webauthn
import zio._

import java.util.Base64
import scala.util.Random

trait MobileDeviceManager {
  def registerDevice(deviceInfo: MobileDeviceInfo): UIO[Unit]
  def getConnectedDevices(): UIO[List[MobileDeviceInfo]]
  def initiateKeyExchange(deviceId: String, challenge: String): IO[String, String]
  def verifyKeyExchange(request: MobileKeyExchangeRequest): IO[String, MobileKeyExchangeResponse]
}

case class MobileDeviceManagerLive(
  connectedDevices: Ref[Map[String, MobileDeviceInfo]],
  activeExchanges: Ref[Map[String, (String, Long)]], // deviceId -> (challenge, timestamp)
) extends MobileDeviceManager {

  def registerDevice(deviceInfo: MobileDeviceInfo): UIO[Unit] =
    connectedDevices.update(
      _ + (deviceInfo.deviceId -> deviceInfo.copy(lastSeen = java.lang.System.currentTimeMillis())),
    )

  def getConnectedDevices(): UIO[List[MobileDeviceInfo]] =
    connectedDevices.get.map(_.values.toList)

  def initiateKeyExchange(deviceId: String, challenge: String): IO[String, String] =
    for {
      devices <- connectedDevices.get
      _       <- ZIO.fail("Device not found").unless(devices.contains(deviceId))
      timestamp = java.lang.System.currentTimeMillis()
      _ <- activeExchanges.update(_ + (deviceId -> (challenge, timestamp)))
    } yield challenge

  def verifyKeyExchange(request: MobileKeyExchangeRequest): IO[String, MobileKeyExchangeResponse] =
    for {
      exchanges                   <- activeExchanges.get
      storedChallengeAndtimestamp <- ZIO
        .fromOption(exchanges.get(request.deviceId))
        .orElseFail("No active key exchange found")
      (storedChallenge, timestamp) = storedChallengeAndtimestamp
      _ <- ZIO
        .fail("Key exchange expired")
        .when(java.lang.System.currentTimeMillis() - timestamp > 300000) // 5 minutes
      _ <- ZIO
        .fail("Challenge mismatch")
        .unless(storedChallenge == request.challenge)
      // In real implementation, verify signature with device's public key
      sessionKey = generateSessionKey()
      _ <- activeExchanges.update(_ - request.deviceId)
    } yield MobileKeyExchangeResponse(success = true, Some(sessionKey), "Key exchange successful")

  private def generateSessionKey(): String =
    Base64.getEncoder.encodeToString(Random.nextBytes(32))
}

object MobileDeviceManager {
  val live: ULayer[MobileDeviceManager] = ZLayer {
    for {
      devices   <- Ref.make(Map.empty[String, MobileDeviceInfo])
      exchanges <- Ref.make(Map.empty[String, (String, Long)])
    } yield MobileDeviceManagerLive(devices, exchanges)
  }
}
