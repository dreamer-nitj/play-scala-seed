package dao

import scala.concurrent.ExecutionContext
import slick.jdbc.JdbcProfile
// import slick.lifted.TableQuery
import models.Users
import scala.concurrent.Future
import models.User
import play.api.db.slick.DatabaseConfigProvider
import javax.inject.Inject

class UserRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig.profile.api._

  private val users = TableQuery[models.Users]

  // Fetch all users
  def getAllUsers: Future[Seq[User]] = dbConfig.db.run(users.result)

  // Fetch user by id
  def getUserById(id: Int): Future[Option[User]] = dbConfig.db.run(users.filter(_.id === id).result.headOption)

  // Fetch user by email
  def getUserByEmail(email: String): Future[Option[User]] =
    dbConfig.db.run(users.filter(_.email === email).result.headOption)

  // Insert a new user
  def insertUser(user: User): Future[Int] = dbConfig.db.run(users += user)

  // Update a user
  def updateUser(user: User): Future[Int] = dbConfig.db.run(users.filter(_.id === user.id).update(user))

  // Delete a user
  def deleteUser(id: Int): Future[Int] = dbConfig.db.run(users.filter(_.id === id).delete)

  class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    def id                   = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def username             = column[String]("username")
    def password             = column[Option[String]]("password")
    def email                = column[Option[String]]("email")
    def emailVerified        = column[Boolean]("email_verified")
    def googleId             = column[Option[String]]("google_id")
    def googleAccessToken    = column[Option[String]]("google_access_token")
    def googleRefreshToken   = column[Option[String]]("google_refresh_token")
    def googleTokenExpiresAt = column[Option[Long]]("google_token_expires_at")
    def pictureUrl           = column[Option[String]]("picture_url")
    def oauthLinkedAt        = column[Option[Instant]]("oauth_linked_at")

    def * = (
      id,
      username,
      password,
      email,
      emailVerified,
      googleId,
      googleAccessToken,
      googleRefreshToken,
      googleTokenExpiresAt,
      pictureUrl,
      oauthLinkedAt
    ) <> ((User.apply _).tupled, User.unapply)
  }

  /** Find user by username
    */
  def findByUsername(username: String): Future[Option[User]] =
    dbConfig.db.run(Users.filter(_.username === username).result.headOption)

  /** Find user by email
    */
  def findByEmail(email: String): Future[Option[User]] =
    dbConfig.db.run(Users.filter(_.email === email).result.headOption)

  /** Find user by Google ID
    */
  def findByGoogleId(googleId: String): Future[Option[User]] =
    dbConfig.db.run(Users.filter(_.googleId === googleId).result.headOption)

  /** Link existing account to Google OAuth
    */
  def linkOAuthAccount(
    userId: Int,
    googleId: String,
    googleAccessToken: String,
    googleRefreshToken: Option[String],
    googleTokenExpiresAt: Long,
    email: String,
    pictureUrl: Option[String]
  ): Future[Int] =
    dbConfig.db.run(
      Users
        .filter(_.id === userId)
        .map(u =>
          (
            u.googleId,
            u.googleAccessToken,
            u.googleRefreshToken,
            u.googleTokenExpiresAt,
            u.email,
            u.emailVerified,
            u.pictureUrl,
            u.oauthLinkedAt
          )
        )
        .update(
          (
            Some(googleId),
            Some(googleAccessToken),
            googleRefreshToken,
            Some(googleTokenExpiresAt),
            Some(email),
            true,
            pictureUrl,
            Some(Instant.now())
          )
        )
    )

  /** Create new OAuth user
    */
  def createOAuthUser(
    username: String,
    email: String,
    googleId: String,
    googleAccessToken: String,
    googleRefreshToken: Option[String],
    googleTokenExpiresAt: Long,
    pictureUrl: Option[String]
  ): Future[Int] =
    dbConfig.db.run(
      Users += User(
        id = 0,
        username = username,
        password = None,
        email = Some(email),
        emailVerified = true,
        googleId = Some(googleId),
        googleAccessToken = Some(googleAccessToken),
        googleRefreshToken = googleRefreshToken,
        googleTokenExpiresAt = Some(googleTokenExpiresAt),
        pictureUrl = pictureUrl,
        oauthLinkedAt = Some(Instant.now())
      )
    )

  /** Update Google refresh token
    */
  def updateGoogleToken(userId: Int, accessToken: String, expiresAt: Long): Future[Int] =
    dbConfig.db.run(
      Users
        .filter(_.id === userId)
        .map(u => (u.googleAccessToken, u.googleTokenExpiresAt))
        .update((Some(accessToken), Some(expiresAt)))
    )

  def findUsersWithExpiringGoogleTokens(expiringBefore: Long): Future[Seq[User]] =
    dbConfig.db.run(
      Users
        .filter(u => u.googleRefreshToken.isDefined && u.googleTokenExpiresAt.isDefined)
        .filter(_.googleTokenExpiresAt <= expiringBefore)
        .result
    )

  def clearGoogleTokens(userId: Int): Future[Int] =
    dbConfig.db.run(
      Users
        .filter(_.id === userId)
        .map(u => (u.googleAccessToken, u.googleRefreshToken, u.googleTokenExpiresAt))
        .update((None, None, None))
    )
}
