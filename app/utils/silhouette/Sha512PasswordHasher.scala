package utils.silhouette

import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.roundeights.hasher.Implicits._

import scala.language.postfixOps

class Sha512PasswordHasher extends BCryptPasswordHasher {

  def iterate512_10Times(plainPassword: String): String = {
    var pwd = plainPassword.sha512
    for (i <- 1 until 10) {
      pwd = pwd.hex.sha512
    }
    pwd
  }

  override def hash(plainPassword: String) = {
    val pwd = iterate512_10Times(plainPassword)
    PasswordInfo(
      hasher = id,
      password = pwd,
      salt = None
    )
  }

  override def matches(passwordInfo: PasswordInfo, suppliedPassword: String) = {
    iterate512_10Times(suppliedPassword).equals(passwordInfo.password)
  }

  override def isDeprecated(passwordInfo: PasswordInfo): Option[Boolean] = {
    Some(true)
  }

}
