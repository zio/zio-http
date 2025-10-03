package example.auth.webauthn.core

import java.util
import java.util.Optional

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption

import zio._

import com.yubico.webauthn._
import com.yubico.webauthn.data._
import example.auth.webauthn.model.UserCredential

/**
 * In-memory implementation of Yubico's CredentialRepository
 */
class InMemoryCredentialRepository(userService: UserService) extends CredentialRepository {
  override def getCredentialIdsForUsername(username: String): util.Set[PublicKeyCredentialDescriptor] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService
          .getUser(username)
          .map(_.credentials)
          .orElseSucceed(Set.empty)
          .map {
            _.map { cred =>
              PublicKeyCredentialDescriptor
                .builder()
                .id(cred.credentialId)
                .build()
            }
          }
          .map(_.toSet)
          .map(_.asJava)
      }.getOrThrow()
    }

  override def getUserHandleForUsername(username: String): Optional[ByteArray] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService
          .getUser(username)
          .map { user =>
            new ByteArray(user.userHandle.getBytes())
          }
          .option
      }.getOrThrow()
    }.toJava

  override def getUsernameForUserHandle(userHandle: ByteArray): Optional[String] = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService.getUserByHandle(new String(userHandle.getBytes)).map(_.username).option
      }.getOrThrow()
    }.toJava
  }

  override def lookup(credentialId: ByteArray, userHandle: ByteArray): Optional[RegisteredCredential] = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService
          .getUserByHandle(new String(userHandle.getBytes))
          .flatMap { user =>
            val credOpt = user.credentials.find(_.credentialId == credentialId)
            credOpt match {
              case Some(cred) => ZIO.succeed(cred)
              case None       => ZIO.fail(new Exception("Credential not found"))
            }
          }
          .map(toRegisteredCredential)
          .option
      }.getOrThrow()
    }.toJava
  }

  override def lookupAll(credentialId: ByteArray): util.Set[RegisteredCredential] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService.getCredentialById(new String(credentialId.getBytes))
      }.getOrThrow().map(toRegisteredCredential).asJava
    }

  private def toRegisteredCredential(cred: UserCredential): RegisteredCredential =
    RegisteredCredential
      .builder()
      .credentialId(cred.credentialId)
      .userHandle(cred.userHandle)
      .publicKeyCose(cred.publicKeyCose)
      .signatureCount(cred.signatureCount)
      .build()
}
