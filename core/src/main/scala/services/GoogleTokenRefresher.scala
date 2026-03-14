package services

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.Logger
import org.apache.pekko.actor.ActorSystem
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import dao.UserRepository

@Singleton
class GoogleTokenRefresher @Inject() (
  config: Configuration,
  actorSystem: ActorSystem,
  oauth2Service: OAuth2Service,
  userRepository: UserRepository
)(implicit ec: ExecutionContext) {

  private val logger   = Logger(getClass)
  private val interval = config.get[Int]("oauth.refresh-interval-seconds").seconds
  private val leeway   = config.get[Int]("oauth.refresh-leeway-seconds")

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = 10.seconds, interval = interval) { () =>
    refreshExpiringTokens()
  }

  private def refreshExpiringTokens(): Unit = {
    val now = System.currentTimeMillis() / 1000
    userRepository.findUsersWithExpiringGoogleTokens(now + leeway).foreach { users =>
      users.foreach { user =>
        user.googleRefreshToken.foreach { refreshToken =>
          oauth2Service.refreshAccessToken(refreshToken).map {
            case Success(tokenResp) =>
              val expiresAt = (System.currentTimeMillis() / 1000) + tokenResp.expiresIn
              userRepository.updateGoogleToken(user.id, tokenResp.accessToken, expiresAt)
            case Failure(ex) =>
              // likely revoked/invalid refresh token
              userRepository.clearGoogleTokens(user.id)
              logger.warn(s"Google refresh failed for user ${user.id}: ${ex.getMessage}")
          }
        }
      }
    }
  }
}
