package converters

import models.User
import persistence.UserPersistence

object UserConverter {
  def fromPersistence(u: UserPersistence): User = {
    User(u.id, u.email, u.emailConfirmed, u.password, u.nick, u.firstName, u.lastName, u.services.split(":").toList)
  }

  def fromModel(u: User): UserPersistence = {
    UserPersistence(u.id, u.email, u.emailConfirmed, u.password, u.nick, u.firstName, u.lastName, u.services.mkString(":"))
  }
}