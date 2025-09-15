package example.auth.webauthn2

import com.yubico.webauthn.data._
import com.yubico.webauthn.{CredentialRepository, RegisteredCredential}
import example.auth.webauthn2.models.StoredCredential

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

/**
 * In-memory implementation of Yubico's CredentialRepository
 * Supports both username-based and usernameless authentication flows
 */
class InMemoryCredentialRepository extends CredentialRepository {
  private val users = new ConcurrentHashMap[String, StoredCredential]()
  private val credentialsByHandle = new ConcurrentHashMap[ByteArray, StoredCredential]()

  def addCredential(credential: StoredCredential): Unit = {
    users.put(credential.username, credential)
    credentialsByHandle.put(credential.userHandle, credential).asInstanceOf[Unit]
  }

  def updateSignatureCount(username: String, signatureCount: Long): Unit = {
    Option(users.get(username)).foreach { cred =>
      val updated = cred.copy(signatureCount = signatureCount)
      users.put(username, updated)
      credentialsByHandle.put(updated.userHandle, updated)
    }
  }

  def getStoredCredential(username: String): Option[StoredCredential] =
    Option(users.get(username))

  override def getCredentialIdsForUsername(username: String): java.util.Set[PublicKeyCredentialDescriptor] =
    Option(users.get(username)) match {
      case Some(cred) =>
        Set(
          PublicKeyCredentialDescriptor
            .builder()
            .id(cred.credentialId)
            .build()
        ).asJava
      case None => Set.empty[PublicKeyCredentialDescriptor].asJava
    }

  override def getUserHandleForUsername(username: String): Optional[ByteArray] =
    Option(users.get(username)) match {
      case Some(cred) => Optional.of(cred.userHandle)
      case None => Optional.empty()
    }

  override def getUsernameForUserHandle(userHandle: ByteArray): Optional[String] =
    Option(credentialsByHandle.get(userHandle)) match {
      case Some(cred) => Optional.of(cred.username)
      case None => Optional.empty()
    }

  override def lookup(credentialId: ByteArray, userHandle: ByteArray): Optional[RegisteredCredential] =
    Option(credentialsByHandle.get(userHandle)) match {
      case Some(cred) if cred.credentialId.equals(credentialId) =>
        Optional.of(
          RegisteredCredential
            .builder()
            .credentialId(cred.credentialId)
            .userHandle(userHandle)
            .publicKeyCose(cred.publicKeyCose)
            .signatureCount(cred.signatureCount)
            .build()
        )
      case _ => Optional.empty()
    }

  override def lookupAll(credentialId: ByteArray): java.util.Set[RegisteredCredential] =
    users
      .values()
      .asScala
      .find(_.credentialId.equals(credentialId))
      .map { cred =>
        Set(
          RegisteredCredential
            .builder()
            .credentialId(cred.credentialId)
            .userHandle(cred.userHandle)
            .publicKeyCose(cred.publicKeyCose)
            .signatureCount(cred.signatureCount)
            .build()
        ).asJava
      }
      .getOrElse(Set.empty[RegisteredCredential].asJava)
}