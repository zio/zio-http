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

class InMemoryCredentialStorage private (
  credentialsRef: Ref[Map[String, PublicKeyCredentialSource]],
  signCountsRef: Ref[Map[String, Long]],
) extends CredentialStorage {

  private def keyFor(credentialId: BufferSource): String = Base64Url.encode(credentialId)

  def storeCredential(rpId: String, userHandle: BufferSource, credential: PublicKeyCredentialSource): Task[Unit] = {
    val key = keyFor(credential.id)
    for {
      _ <- credentialsRef.update(_ + (key -> credential))
      _ <- signCountsRef.update(_ + (key -> 0L))
    } yield ()
  }

  def getCredentialById(credentialId: BufferSource): Task[Option[PublicKeyCredentialSource]] = {
    val key = keyFor(credentialId)
    credentialsRef.get.map(_.get(key))
  }

  def getCredentialsByRpAndUser(rpId: String, userHandle: BufferSource): Task[Seq[PublicKeyCredentialSource]] = {
    credentialsRef.get.map { credentials =>
      credentials.values.filter { cred =>
        cred.rpId == rpId && cred.userHandle.exists(_.sameElements(userHandle))
      }.toSeq
    }
  }

  def getCredentialsByRp(rpId: String): Task[Seq[PublicKeyCredentialSource]] = {
    credentialsRef.get.map { credentials =>
      credentials.values.filter(_.rpId == rpId).toSeq
    }
  }

  def deleteCredential(credentialId: BufferSource): Task[Boolean] = {
    val key = keyFor(credentialId)
    for {
      existedResult <- credentialsRef.modify { credentials =>
        val existed = credentials.contains(key)
        val updated = credentials - key
        (existed, updated)
      }
      _             <- signCountsRef.update(_ - key)
    } yield existedResult
  }

  def updateSignCount(credentialId: BufferSource, signCount: Long): Task[Unit] = {
    val key = keyFor(credentialId)
    signCountsRef.update(_ + (key -> signCount))
  }

  def getSignCount(credentialId: BufferSource): Task[Long] = {
    val key = keyFor(credentialId)
    signCountsRef.get.map(_.getOrElse(key, 0L))
  }

  // Additional utility methods for testing/debugging
  def getAllCredentials: Task[Map[String, PublicKeyCredentialSource]] =
    credentialsRef.get

  def getAllSignCounts: Task[Map[String, Long]] =
    signCountsRef.get

  def clear: Task[Unit] =
    for {
      _ <- credentialsRef.set(Map.empty)
      _ <- signCountsRef.set(Map.empty)
    } yield ()

  // Helper methods for common operations
  def storeCredentialWithCount(
    rpId: String,
    userHandle: BufferSource,
    credential: PublicKeyCredentialSource,
    initialSignCount: Long = 0L,
  ): Task[Unit] =
    for {
      _ <- storeCredential(rpId, userHandle, credential)
      _ <- updateSignCount(credential.id, initialSignCount)
    } yield ()

  def getCredentialWithCount(credentialId: BufferSource): Task[Option[(PublicKeyCredentialSource, Long)]] =
    for {
      credentialOpt <- getCredentialById(credentialId)
      count         <- getSignCount(credentialId)
    } yield credentialOpt.map(_ -> count)
}

object InMemoryCredentialStorage {

  /**
   * Creates a new InMemoryCredentialStorage with empty initial state
   */
  def make: Task[InMemoryCredentialStorage] =
    for {
      credentialsRef <- Ref.make(Map.empty[String, PublicKeyCredentialSource])
      signCountsRef  <- Ref.make(Map.empty[String, Long])
    } yield new InMemoryCredentialStorage(credentialsRef, signCountsRef)

  /**
   * Creates a new InMemoryCredentialStorage with provided initial state
   */
  def makeWith(
    initialCredentials: Map[String, PublicKeyCredentialSource] = Map.empty,
    initialSignCounts: Map[String, Long] = Map.empty,
  ): Task[InMemoryCredentialStorage] =
    for {
      credentialsRef <- Ref.make(initialCredentials)
      signCountsRef  <- Ref.make(initialSignCounts)
    } yield new InMemoryCredentialStorage(credentialsRef, signCountsRef)

  /**
   * Creates a ZIO Layer for the credential storage
   */
  val layer: ZLayer[Any, Nothing, InMemoryCredentialStorage] =
    ZLayer.fromZIO(make.orDie)

  // Implicit class for additional operations (Scala 2.13 way)
  implicit class CredentialStorageOps(storage: InMemoryCredentialStorage) {
    def storeCredentialWithInitialCount(
      rpId: String,
      userHandle: BufferSource,
      credential: PublicKeyCredentialSource,
      initialSignCount: Long,
    ): Task[Unit] =
      storage.storeCredentialWithCount(rpId, userHandle, credential, initialSignCount)
  }
}

