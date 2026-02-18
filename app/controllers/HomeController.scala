package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import dao.UserRepository
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import actors.UserActor
import scala.concurrent.duration._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.Timeout
import org.apache.pekko.pattern.ask
import org.apache.pekko.actor.Props
import actions.AuthAction
import actions.AuthenticatedRequest

/** This controller creates an `Action` to handle HTTP requests to the application's home page.
  */
@Singleton
class HomeController @Inject() (
  val controllerComponents: ControllerComponents,
  userRepository: UserRepository,
  authAction: AuthAction,
  implicit val actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends BaseController {
  implicit val timeout: Timeout = 5.seconds
  private val userActor         = actorSystem.actorOf(Props(new UserActor(userRepository)))

  /** Create an Action to render an HTML page.
    *
    * The configuration in the `routes` file means that this method will be called when the application receives a `GET`
    * request with a path of `/`.
    */
  def index() = Action { implicit request: Request[AnyContent] =>
    authenticateBasicAuth(request) match {
      case Some((username, password)) if isValidCredentials(username, password) =>
        Ok(views.html.index())
      case _ =>
        Unauthorized.withHeaders("WWW-Authenticate" -> "Basic realm=\"Secured Area\"")
    }
  }

  private def authenticateBasicAuth[A](request: Request[A]): Option[(String, String)] =
    request.headers.get("Authorization").flatMap { authHeader =>
      if (authHeader.startsWith("Basic ")) {
        val base64Credentials = authHeader.substring("Basic ".length)
        val credentials       = new String(java.util.Base64.getDecoder.decode(base64Credentials)).split(":", 2)
        if (credentials.length == 2) {
          Some((credentials(0), credentials(1)))
        } else {
          None
        }
      } else {
        None
      }
    }

  def index2(request: Request[AnyContent]) = Action {
    Ok(views.html.index())
  }

  // protected endpoint that requires JWT
  def getUsers() = authAction.async { implicit request: Request[AnyContent] =>
    (userActor ? UserActor.GetAllUsers)
      .mapTo[UserActor.UserResponse]
      .map { response =>
        Ok(response.users.map(_.name).mkString(",") + "\n")
      }
      .recover { case ex: Exception =>
        InternalServerError("Error fetching users: " + ex.getMessage)
      }
  }

  // add a new user to the database. The user details can be passed in the request body as JSON.
  def addUser() = authAction.async(parse.json) { implicit request: Request[play.api.libs.json.JsValue] =>
    val name  = (request.body \ "name").as[String]
    val email = (request.body \ "email").as[String]
    val user  = models.User(0, name, email) // id will be auto-generated
    userRepository
      .insertUser(user)
      .map { _ =>
        Ok("User added successfully")
      }
      .recover {
        // Handle any exceptions that may occur during database operations and return an appropriate error response instead of crashing the application.
        case ex: Exception => InternalServerError("Error adding user: " + ex.getMessage)
      }
  }

  // update an existing user in the database. The user details can be passed in the request body as JSON.
  def updateUser() = authAction.async(parse.json) { implicit request: Request[play.api.libs.json.JsValue] =>
    // Proceed with updating the user
    // Extract the user details from the request body as UpdateUserRequest using validate and asOpt methods to handle potential parsing errors gracefully.
    request.body.validate[models.UserUpdateRequest].asOpt match {
      case Some(userUpdate) =>
        userRepository
          .getUserByEmail(userUpdate.email)
          .flatMap {
            case Some(existingUser) =>
              val updatedUser = models.User(existingUser.id, userUpdate.name, userUpdate.email)
              userRepository
                .updateUser(updatedUser)
                .map { _ =>
                  Ok("User updated successfully")
                }
                .recover { case ex: Exception =>
                  InternalServerError("Error updating user: " + ex.getMessage)
                }
            case _ =>
              Future.successful(BadRequest("Invalid user data"))
          }
          .recover { case ex: Exception =>
            InternalServerError("Error fetching user by email: " + ex.getMessage)
          }
      case None =>
        Future.successful(BadRequest("Invalid request body"))
    }
  }
}
