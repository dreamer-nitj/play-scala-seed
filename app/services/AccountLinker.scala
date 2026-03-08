package services

import dao.UserRepository
import play.api.libs.json.Json
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class LinkRequest(userId: Int, email: String, googleId: String)
case class LinkResponse(success: Boolean, message: String, userId: Option[Int] = None)

@Singleton
class AccountLinker @Inject() (userRepository: UserRepository)(implicit ec: ExecutionContext) {

  /** Link OAuth account to existing user (requires confirmation) Checks if email matches and prevents hijacking
    */
  def linkAccount(
    userId: Int,
    googleId: String,
    googleEmail: String,
    googleAccessToken: String,
    googleRefreshToken: Option[String],
    googleTokenExpiresAt: Long,
    pictureUrl: Option[String]
  ): Future[Try[LinkResponse]] =
    userRepository.findByEmail(googleEmail).flatMap { existingUser =>
      existingUser match {
        case Some(user) if user.id == userId =>
          // Email matches - safe to link
          userRepository
            .linkOAuthAccount(
              userId,
              googleId,
              googleAccessToken,
              googleRefreshToken,
              googleTokenExpiresAt,
              googleEmail,
              pictureUrl
            )
            .map(_ =>
              Success(
                LinkResponse(
                  success = true,
                  message = "OAuth account linked successfully",
                  userId = Some(userId)
                )
              )
            )
            .recover { case ex =>
              Failure(new Exception(s"Database error during linking: ${ex.getMessage}"))
            }

        case Some(other) =>
          // Email exists but belongs to different user - HIJACK ATTEMPT
          Future.successful(
            Failure(
              new Exception(
                s"Email ${googleEmail} is already linked to another account (ID: ${other.id}). This may be a security issue."
              )
            )
          )

        case None =>
          // Email doesn't exist - should not happen in link flow
          Future.successful(
            Failure(new Exception(s"User with ID $userId not found"))
          )
      }
    }

  /** Create new user from OAuth (auto-registration)
    */
  def createOAuthUser(
    googleId: String,
    googleEmail: String,
    googleName: Option[String],
    googleAccessToken: String,
    googleRefreshToken: Option[String],
    googleTokenExpiresAt: Long,
    pictureUrl: Option[String]
  ): Future[Try[LinkResponse]] =
    // Check if Google ID or email already registered
    for {
      existingByGoogleId <- userRepository.findByGoogleId(googleId)
      existingByEmail    <- userRepository.findByEmail(googleEmail)
      result <- (existingByGoogleId, existingByEmail) match {
        case (Some(_), _) =>
          Future.successful(Failure(new Exception("Google account already registered")))
        case (_, Some(_)) =>
          Future.successful(
            Failure(
              new Exception(
                s"Email $googleEmail already exists. Please log in and link your account."
              )
            )
          )
        case (None, None) =>
          // Safe to create
          val username = googleEmail.split("@")(0) // Extract username from email
          userRepository
            .createOAuthUser(
              username = username,
              email = googleEmail,
              googleId = googleId,
              googleAccessToken = googleAccessToken,
              googleRefreshToken = googleRefreshToken,
              googleTokenExpiresAt = googleTokenExpiresAt,
              pictureUrl = pictureUrl
            )
            .map(userId =>
              Success(
                LinkResponse(
                  success = true,
                  message = "OAuth user created successfully",
                  userId = Some(userId)
                )
              )
            ) recover { case ex =>
            Failure(new Exception(s"Failed to create user: ${ex.getMessage}"))
          }
      }
    } yield result
}
