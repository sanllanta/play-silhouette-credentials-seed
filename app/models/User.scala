package models

import utils.silhouette.IdentitySilhouette
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import converters.UserConverter
import persistence.UserRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class User(
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
    services: List[String]
) extends IdentitySilhouette {
  def key = email
  def fullName: String = firstName + " " + lastName
}

object User {

  val services = Seq("serviceA", "serviceB", "master")

  val users = scala.collection.mutable.HashMap[Long, User](
    1L -> User(None, "master@myweb.com", true, (new BCryptPasswordHasher()).hash("123123").password, "Eddy", "Eddard", "Stark", List("master")),
    2L -> User(None, "a@myweb.com", true, (new BCryptPasswordHasher()).hash("123123").password, "Maggy", "Margaery", "Tyrell", List("serviceA")),
    3L -> User(None, "b@myweb.com", true, (new BCryptPasswordHasher()).hash("123123").password, "Petyr", "Petyr", "Baelish", List("serviceB")),
    4L -> User(None, "a_b@myweb.com", true, (new BCryptPasswordHasher()).hash("123123").password, "Tyry", "Tyrion", "Lannister", List("serviceA", "serviceB"))
  )

  //  users.values.foreach(save)

  //  def findByEmail(email: String): Future[Option[User]] = Future.successful(users.find(_._2.email == email).map(_._2))
  //
  //  def save(user: User): Future[User] = {
  //    // A rudimentary auto-increment feature...
  //    def nextId: Long = users.maxBy(_._1)._1 + 1
  //
  //    val theUser = if (user.id.isDefined) user else user.copy(id = Some(nextId))
  //    users += (theUser.id.get -> theUser)
  //    Future.successful(theUser)
  //  }
  //
  //  def remove(email: String): Future[Unit] = findByEmail(email).map(_.map(u => users.remove(u.id.get)))

  def findByEmail(email: String): Future[Option[User]] =
    UserRepository.findByEmail(email).map(_.map(UserConverter.fromPersistence))

  def save(user: User): Future[User] =
    UserRepository.save(UserConverter.fromModel(user)).map(_ => user)

  def remove(email: String): Future[Unit] =
    UserRepository.remove(email).map(_ => Unit)
}
