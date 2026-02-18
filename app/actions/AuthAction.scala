package actions

import play.api.mvc.WrappedRequest
import javax.inject.Inject
import play.api.mvc.BodyParsers
import play.api.Configuration
import play.api.mvc.ActionBuilderImpl
import scala.concurrent.{ExecutionContext, Future}
import utils.JwtUtil
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results

case class UserRequest[A](userId: String, username: String, request: Request[A]) extends WrappedRequest(request)

case class AuthenticatedRequest[A](userId: String, username: String, request: Request[A])
    extends WrappedRequest[A](request)

class AuthAction @Inject() (
  override val parser: BodyParsers.Default,
  config: Configuration
)(implicit ec: ExecutionContext)
    extends ActionBuilderImpl(parser) {

  private val jwtUtil = new JwtUtil(
    config.get[String]("jwt.secret"),
    config.get[Int]("jwt.expiry-minutes")
  )

  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
    request.headers.get("Authorization") match {
      case Some(authHeader) if authHeader.startsWith("Bearer ") =>
        val token = authHeader.substring("Bearer ".length)

        jwtUtil.validateToken(token) match {
          case scala.util.Success(claims) =>
            val userId      = jwtUtil.extractUserId(claims)
            val username    = jwtUtil.extractUsername(claims)
            val userRequest = UserRequest(userId, username, request)
            block(userRequest)
          case scala.util.Failure(_) =>
            Future.successful(Results.Unauthorized("Invalid or expired token"))
        }

      case _ =>
        Future.successful(Results.Unauthorized("Missing or invalid Authorization header"))
    }
}
