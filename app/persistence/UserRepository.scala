package persistence

import slick.driver.PostgresDriver.api._
import slick.lifted.Tag
import scala.concurrent.Future

object UserRepository {

  val myDB = DataBaseDefinitionImpl().getDataBase()
  val users = TableQuery[UsersTable]

  def findByEmail(email: String): Future[Option[UserPersistence]] = {
    myDB.run(users.filter(_.email === email).result.headOption)
  }

  def save(user: UserPersistence): Future[Long] = {
    myDB.run(users returning users.map(_.id) += user)
  }

  def remove(email: String): Future[Int] = {
    myDB.run(users.filter(_.email === email).delete)
  }

}

class UsersTable(tag: Tag) extends Table[UserPersistence](tag, "users") {
  def id = column[Long]("id", O.AutoInc)
  def email = column[String]("email", O.PrimaryKey)
  def emailConfirmed = column[Boolean]("emailConfirmed")
  def password = column[String]("password")
  def nick = column[String]("nick")
  def firstName = column[String]("firstName")
  def lastName = column[String]("lastName")
  def services = column[String]("services")

  def * = (id.?, email, emailConfirmed, password, nick, firstName, lastName, services) <> (UserPersistence.tupled, UserPersistence.unapply)
}

case class UserPersistence(
  id: Option[Long],
  email: String,
  emailConfirmed: Boolean,
  password: String,
  nick: String,
  firstName: String,
  lastName: String,
  /*
  * A user can register some accounts from third-party services, then it will have access to different parts of the webpage. The 'master' privilege has full access.
  * Ex: ("master") -> full access to every point of the webpage.
  * Ex: ("serviceA") -> have access only to general and serviceA areas.
  * Ex: ("serviceA", "serviceB") -> have access only to general, serviceA and serviceB areas.
  */
  services: String //Services separated by ':'
)