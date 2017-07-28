package converters

import models.{ MailTokenUser, User }
import persistence.{ MailTokenUserPersistence, UserPersistence }

object TokenConverter {
  def fromPersistence(m: MailTokenUserPersistence): MailTokenUser = {
    MailTokenUser(m.id, m.email, m.expirationTime, m.isSignUp)
  }

  def fromModel(m: MailTokenUser): MailTokenUserPersistence = {
    MailTokenUserPersistence(m.id, m.email, m.expirationTime, m.isSignUp)
  }
}