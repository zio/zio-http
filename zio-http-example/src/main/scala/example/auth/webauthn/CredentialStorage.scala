package example.auth.webauthn
import example.auth.webauthn.Types._
import zio._

trait CredentialStorage {
  def storeCredential(rpId: String, userHandle: BufferSource, credential: PublicKeyCredentialSource): Task[Unit]
  def getCredentialById(credentialId: BufferSource): Task[Option[PublicKeyCredentialSource]]
  def getCredentialsByRpAndUser(rpId: String, userHandle: BufferSource): Task[Seq[PublicKeyCredentialSource]]
  def getCredentialsByRp(rpId: String): Task[Seq[PublicKeyCredentialSource]]
  def deleteCredential(credentialId: BufferSource): Task[Boolean]
  def updateSignCount(credentialId: BufferSource, signCount: Long): Task[Unit]
}

class InMemoryCredentialStorage extends CredentialStorage {
  private var credentials: Map[String, PublicKeyCredentialSource] = Map.empty
  private var signCounts: Map[String, Long]                       = Map.empty

  private def keyFor(credentialId: BufferSource): String = Base64Url.encode(credentialId)

  def storeCredential(rpId: String, userHandle: BufferSource, credential: PublicKeyCredentialSource): Task[Unit] = {
    ZIO.succeed {
      val key = keyFor(credential.id)
      credentials = credentials + (key -> credential)
      signCounts = signCounts + (key   -> 0L)
    }
  }

  def getCredentialById(credentialId: BufferSource): Task[Option[PublicKeyCredentialSource]] = {
    ZIO.succeed(credentials.get(keyFor(credentialId)))
  }

  def getCredentialsByRpAndUser(rpId: String, userHandle: BufferSource): Task[Seq[PublicKeyCredentialSource]] = {
    ZIO.succeed {
      val matching = credentials.values.filter { cred =>
        cred.rpId == rpId && cred.userHandle.exists(_.sameElements(userHandle))
      }.toSeq
      matching
    }
  }

  def getCredentialsByRp(rpId: String): Task[Seq[PublicKeyCredentialSource]] = {
    ZIO.succeed {
      val matching = credentials.values.filter(_.rpId == rpId).toSeq
      matching
    }
  }

  def deleteCredential(credentialId: BufferSource): Task[Boolean] = {
    ZIO.succeed {
      val key     = keyFor(credentialId)
      val existed = credentials.contains(key)
      credentials = credentials - key
      signCounts = signCounts - key
      existed
    }
  }

  def updateSignCount(credentialId: BufferSource, signCount: Long): Task[Unit] = {
    ZIO.succeed {
      val key = keyFor(credentialId)
      signCounts = signCounts + (key -> signCount)
    }
  }

  def getSignCount(credentialId: BufferSource): Task[Long] = {
    ZIO.succeed(signCounts.getOrElse(keyFor(credentialId), 0L))
  }
}
