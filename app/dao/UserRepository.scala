package dao

import scala.concurrent.ExecutionContext
import slick.jdbc.JdbcProfile
// import slick.lifted.TableQuery
import models.Users
import scala.concurrent.Future
import models.User
import play.api.db.slick.DatabaseConfigProvider
import javax.inject.Inject

class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
    private val dbConfig = dbConfigProvider.get[JdbcProfile]
    import dbConfig.profile.api._

    private val users = TableQuery[models.Users]

    // Fetch all users
    def getAllUsers: Future[Seq[User]] = dbConfig.db.run(users.result)

    // Fetch user by id
    def getUserById(id: Int): Future[Option[User]] = dbConfig.db.run(users.filter(_.id === id).result.headOption)

    // Fetch user by email
    def getUserByEmail(email: String): Future[Option[User]] = dbConfig.db.run(users.filter(_.email === email).result.headOption)

    // Insert a new user
    def insertUser(user: User): Future[Int] = dbConfig.db.run(users += user)

    // Update a user
    def updateUser(user: User): Future[Int] = dbConfig.db.run(users.filter(_.id === user.id).update(user))

    // Delete a user
    def deleteUser(id: Int): Future[Int] = dbConfig.db.run(users.filter(_.id === id).delete)
}