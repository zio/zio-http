package example.auth.webauthn2

import com.yubico.webauthn._
import com.yubico.webauthn.data._
import example.auth.webauthn2.models.StoredCredential
import java.util
import java.util.Optional
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption

/**
 * In-memory implementation of Yubico's CredentialRepository
 * Uses a Multi-Key Map pattern to support both username-based and usernameless authentication flows
 */
class InMemoryCredentialRepository extends CredentialRepository {

  // Unified key system for different lookup patterns
  sealed trait CredentialKey
  case class UsernameKey(username: String) extends CredentialKey
  case class UserHandleKey(userHandle: ByteArray) extends CredentialKey
  case class CredentialIdKey(credentialId: ByteArray) extends CredentialKey
  case class CompositeKey(credentialId: ByteArray, userHandle: ByteArray) extends CredentialKey

  // Single unified storage - all keys point to credential instances
  private val credentialStore = TrieMap.empty[CredentialKey, StoredCredential]

  def addCredential(credential: StoredCredential): Unit = {
    // Store with multiple keys pointing to the same credential object
    credentialStore.put(UsernameKey(credential.username), credential)
    credentialStore.put(UserHandleKey(credential.userHandle), credential)
    credentialStore.put(CredentialIdKey(credential.credentialId), credential)
    credentialStore.put(
      CompositeKey(credential.credentialId, credential.userHandle),
      credential
    ).asInstanceOf[Unit]
  }

  def updateSignatureCount(username: String, signatureCount: Long): Unit = {
    credentialStore.get(UsernameKey(username)).foreach { oldCred =>
      val updatedCred = oldCred.copy(signatureCount = signatureCount)

      // Remove old entries
      credentialStore.remove(UsernameKey(oldCred.username))
      credentialStore.remove(UserHandleKey(oldCred.userHandle))
      credentialStore.remove(CredentialIdKey(oldCred.credentialId))
      credentialStore.remove(CompositeKey(oldCred.credentialId, oldCred.userHandle))

      // Add updated credential with all keys
      addCredential(updatedCred)
    }
  }

  def getStoredCredential(username: String): Option[StoredCredential] =
    credentialStore.get(UsernameKey(username))

  override def getCredentialIdsForUsername(username: String): util.Set[PublicKeyCredentialDescriptor] =
    credentialStore
      .get(UsernameKey(username))
      .map { cred =>
        PublicKeyCredentialDescriptor
          .builder()
          .id(cred.credentialId)
          .build()
      }
      .toSet
      .asJava

  override def getUserHandleForUsername(username: String): Optional[ByteArray] =
    credentialStore
      .get(UsernameKey(username))
      .map(_.userHandle)
      .toJava

  override def getUsernameForUserHandle(userHandle: ByteArray): Optional[String] =
    credentialStore
      .get(UserHandleKey(userHandle))
      .map(_.username)
      .toJava

  override def lookup(credentialId: ByteArray, userHandle: ByteArray): Optional[RegisteredCredential] =
    credentialStore
      .get(CompositeKey(credentialId, userHandle))
      .map(toRegisteredCredential)
      .toJava

  override def lookupAll(credentialId: ByteArray): util.Set[RegisteredCredential] =
    credentialStore
      .get(CredentialIdKey(credentialId))
      .map { cred =>
        Set(toRegisteredCredential(cred))
      }
      .getOrElse(Set.empty[RegisteredCredential])
      .asJava

  // Helper method to convert StoredCredential to RegisteredCredential
  private def toRegisteredCredential(cred: StoredCredential): RegisteredCredential =
    RegisteredCredential
      .builder()
      .credentialId(cred.credentialId)
      .userHandle(cred.userHandle)
      .publicKeyCose(cred.publicKeyCose)
      .signatureCount(cred.signatureCount)
      .build()
}
