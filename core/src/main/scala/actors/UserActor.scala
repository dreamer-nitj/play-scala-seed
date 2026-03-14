package actors

import dao.UserRepository
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorLogging
import scala.concurrent.ExecutionContext

object UserActor {
  case object GetAllUsers
  case class UserResponse(users: Seq[models.User])
}

class UserActor(userRepository: UserRepository)(implicit ec: ExecutionContext) extends Actor with ActorLogging {
  import UserActor._

  def receive: Receive = { case GetAllUsers =>
    val originalSender = sender()
    userRepository.getAllUsers
      .map { users =>
        originalSender ! UserResponse(users)
      }
      .recover { case ex: Exception =>
        log.error("Error fetching users: " + ex.getMessage)
        originalSender ! org.apache.pekko.actor.Status.Failure(ex)
      }
  }
}
