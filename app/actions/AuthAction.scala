package actions

import play.api.mvc.WrappedRequest
import javax.inject.Inject
import javax.inject.Singleton
import play.api.libs.json.Json
import play.api.mvc.BodyParsers
import play.api.Configuration
import play.api.mvc.ActionBuilderImpl
import scala.concurrent.{ExecutionContext, Future}
import utils.JwtUtil
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.mvc.ActionBuilder
import play.api.mvc.ActionRefiner
import play.api.mvc.AnyContent

case class UserRequest[A](userId: String, username: String, request: Request[A]) extends WrappedRequest(request)

case class AuthenticatedRequest[A](userId: String, username: String, request: Request[A])
    extends WrappedRequest[A](request)

@Singleton
class AuthAction @Inject() (
  val parser: BodyParsers.Default,
  config: Configuration
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[AuthenticatedRequest, AnyContent]
    with ActionRefiner[Request, AuthenticatedRequest] {

  private val jwtUtil = new JwtUtil(
    config.get[String]("jwt.secret"),
    config.get[Int]("jwt.expiry-minutes")
  )

  override protected def refine[A](request: Request[A]): Future[Either[Result, AuthenticatedRequest[A]]] =
    request.headers.get("Authorization") match {
      case Some(authHeader) if authHeader.startsWith("Bearer ") =>
        val token = authHeader.substring(7) // Remove "Bearer " prefix

        jwtUtil.validateToken(token) match {
          case scala.util.Success(claims) =>
            val userId      = jwtUtil.extractUserId(claims)
            val username    = jwtUtil.extractUsername(claims)
            val authRequest = AuthenticatedRequest(userId, username, request)
            Future.successful(Right(authRequest))

          case scala.util.Failure(ex) =>
            Future.successful(
              Left(
                Results.Unauthorized(
                  Json.obj(
                    "error"   -> "Invalid or expired token",
                    "message" -> ex.getMessage
                  )
                )
              )
            )
        }

      case _ =>
        Future.successful(
          Left(
            Results.Unauthorized(Json.obj("error" -> "Missing or invalid Authorization header"))
          )
        )
    }
}
