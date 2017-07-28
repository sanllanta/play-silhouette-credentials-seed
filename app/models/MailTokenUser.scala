package models

import utils.silhouette.MailToken
import org.joda.time.DateTime
import java.util.UUID

import converters.TokenConverter
import persistence.MailTokenRepository
import play.Logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class MailTokenUser(id: String, email: String, expirationTime: DateTime, isSignUp: Boolean) extends MailToken

object MailTokenUser {
  def apply(email: String, isSignUp: Boolean): MailTokenUser =
    MailTokenUser(UUID.randomUUID().toString, email, (new DateTime()).plusHours(24), isSignUp)

  val tokens = scala.collection.mutable.HashMap[String, MailTokenUser]()

  def findById(id: String): Future[Option[MailTokenUser]] = {
    MailTokenRepository.findById(id).map(_.map(TokenConverter.fromPersistence))
  }

  def save(token: MailTokenUser): Future[MailTokenUser] = {
    Logger.info("GUARDANDO TOKEN => " + token)
    MailTokenRepository.save(TokenConverter.fromModel(token)).map(_ => token)
  }

  def delete(id: String): Future[Unit] = {
    MailTokenRepository.delete(id).map(_ => Unit)
  }
}