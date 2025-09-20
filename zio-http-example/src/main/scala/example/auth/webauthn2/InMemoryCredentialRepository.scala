package example.auth.webauthn2

import zio._
import com.yubico.webauthn._
import com.yubico.webauthn.data._
import example.auth.webauthn2.models.UserCredential

import java.util
import java.util.{Base64, Optional}
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption

/**
 * In-memory implementation of Yubico's CredentialRepository Uses a Multi-Key
 * Map pattern to support both username-based and usernameless authentication
 * flows
 */
class InMemoryCredentialRepository(userService: UserService) extends CredentialRepository {

  // Unified key system for different lookup patterns
  sealed trait CredentialKey
  case class UsernameKey(username: String)                                extends CredentialKey
  case class UserHandleKey(userHandle: ByteArray)                         extends CredentialKey
  case class CredentialIdKey(credentialId: ByteArray)                     extends CredentialKey
  case class CompositeKey(credentialId: ByteArray, userHandle: ByteArray) extends CredentialKey

  // Single unified storage - all keys point to credential instances
  private val credentialStore = TrieMap.empty[CredentialKey, UserCredential]

  def addCredential(credential: UserCredential): Unit = {
    // Store with multiple keys pointing to the same credential object
    credentialStore.put(UsernameKey(credential.username), credential)
    credentialStore.put(UserHandleKey(credential.userHandle), credential)
    credentialStore.put(CredentialIdKey(credential.credentialId), credential)
    credentialStore.put(CompositeKey(credential.credentialId, credential.userHandle), credential).asInstanceOf[Unit]
  }

  def addCredential(
    credentialId: ByteArray,
    publicKeyCose: ByteArray,
    signatureCount: Long,
    username: String,
    userHandle: ByteArray, // Added to support discoverable passkeys
  ): Unit = {

    val credential = UserCredential(credentialId, publicKeyCose, signatureCount, username, userHandle)

    // Store with multiple keys pointing to the same credential object
    credentialStore.put(UsernameKey(credential.username), credential)
    credentialStore.put(UserHandleKey(credential.userHandle), credential)
    credentialStore.put(CredentialIdKey(credential.credentialId), credential)
    credentialStore
      .put(
        CompositeKey(credential.credentialId, credential.userHandle),
        credential,
      )
      .asInstanceOf[Unit]
  }

  override def getCredentialIdsForUsername(username: String): util.Set[PublicKeyCredentialDescriptor] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run {
        userService
          .getUser(username)
          .map(_.credentials)
          .map { creds =>
            creds.map { cred =>
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
        ZIO.attempt {
          credentialStore
            .get(CredentialIdKey(credentialId))
            .map { cred =>
              Set(toRegisteredCredential(cred))
            }
            .getOrElse(Set.empty[RegisteredCredential])
        }
      }.getOrThrow()
    }.asJava

  // Helper method to convert StoredCredential to RegisteredCredential
  private def toRegisteredCredential(cred: UserCredential): RegisteredCredential =
    RegisteredCredential
      .builder()
      .credentialId(cred.credentialId)
      .userHandle(cred.userHandle)
      .publicKeyCose(cred.publicKeyCose)
      .signatureCount(cred.signatureCount)
      .build()
}
