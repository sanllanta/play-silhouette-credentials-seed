package persistence

import org.joda.time.DateTime
import slick.driver.PostgresDriver.api._
import slick.lifted.Tag

import scala.concurrent.Future
import com.github.tototoshi.slick.PostgresJodaSupport._
import play.Logger

object MailTokenRepository {

  val myDB = DataBaseDefinitionImpl().getDataBase()
  val tokens = TableQuery[TokensTable]

  def findById(id: String): Future[Option[MailTokenUserPersistence]] = {
    myDB.run(tokens.filter(_.id === id).result.headOption)
  }

  def save(token: MailTokenUserPersistence): Future[String] = {
    Logger.info("GUARDANDO TOKEN => " + token)
    myDB.run(tokens returning tokens.map(_.id) += token)
  }

  def delete(id: String): Future[Int] = {
    myDB.run(tokens.filter(_.id === id).delete)
  }

}

class TokensTable(tag: Tag) extends Table[MailTokenUserPersistence](tag, "tokens") {
  def id = column[String]("id", O.PrimaryKey)
  def email = column[String]("email")
  def expirationTime = column[DateTime]("expirationTime")
  def isSignUp = column[Boolean]("isSignUp")

  def * = (id, email, expirationTime, isSignUp) <> (MailTokenUserPersistence.tupled, MailTokenUserPersistence.unapply)
}

case class MailTokenUserPersistence(id: String, email: String, expirationTime: DateTime, isSignUp: Boolean)