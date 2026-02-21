package services

import javax.inject.{Inject, Singleton}
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import java.util.UUID

case class PendingLink(
  token: String,
  googleId: String,
  email: String,
  accessToken: String,
  refreshToken: Option[String],
  expiresAt: Long,
  pictureUrl: Option[String],
  createdAt: Long
)

@Singleton
class PendingLinkStore @Inject() (implicit ec: ExecutionContext) {

  private val store: mutable.Map[String, PendingLink] = mutable.Map()
  private val ttlMs: Long                             = 5 * 60 * 1000 // 5 minutes

  def create(
    googleId: String,
    email: String,
    accessToken: String,
    refreshToken: Option[String],
    expiresAt: Long,
    pictureUrl: Option[String]
  ): String = {
    val token = UUID.randomUUID().toString
    val data =
      PendingLink(token, googleId, email, accessToken, refreshToken, expiresAt, pictureUrl, System.currentTimeMillis())
    store.put(token, data)
    token
  }

  def consume(token: String): Option[PendingLink] =
    store.get(token).flatMap { data =>
      val expired = System.currentTimeMillis() - data.createdAt > ttlMs
      store.remove(token)
      if (expired) None else Some(data)
    }
}
