package controllers

import models._
import utils.silhouette._
import utils.silhouette.Implicits._
import com.mohiva.play.silhouette.api.{ LoginEvent, LoginInfo, LogoutEvent, SignUpEvent, Silhouette }
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{ Clock, Credentials, PasswordHasherRegistry }
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.impl.exceptions.{ IdentityNotFoundException, InvalidPasswordException }
import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.{ Messages, MessagesApi }
import utils.Mailer

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import net.ceedubs.ficus.Ficus._
import javax.inject.{ Inject, Singleton }

import views.html.{ auth => viewsAuth }
import javax.script.ScriptEngineManager

import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }

import scala.util.matching.Regex

@Singleton
class Auth @Inject() (
    val silhouette: Silhouette[MyEnv],
    val messagesApi: MessagesApi,
    userService: UserService,
    authInfoRepository: AuthInfoRepository,
    credentialsProvider: CredentialsProvider,
    tokenService: MailTokenService[MailTokenUser],
    passwordHasherRegistry: PasswordHasherRegistry,
    mailer: Mailer,
    conf: Configuration,
    clock: Clock
) extends AuthController {

  // UTILITIES

  val passwordValidation = nonEmptyText(minLength = 6)
  def notFoundDefault(implicit request: RequestHeader) = Future.successful(NotFound(views.html.errors.notFound(request)))

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////	
  // SIGN UP

  val owaspMaxLength = 32
  val owaspMinLength = 10
  val sequencesOfThreeOrMore = """.*(\w)\1{2,}.*""".r
  val lowerCaseCharacter = """.*[a-z].*""".r
  val upperCaseCharacter = """.*[A-Z].*""".r
  val numberCharacter = """.*[0-9].*""".r
  val specialCharacter = """.*[^A-Za-z0-9].*""".r
  val usernameRegex = """^[a-zA-Z0-9.-]+$""".r

  def owaspValid: Constraint[String] = Constraint[String]("constraint.owasp-strenght") { o =>

    val thereAreSequences = sequencesOfThreeOrMore.pattern.matcher(o).matches()
    val thereIsNoLowerCase = !lowerCaseCharacter.pattern.matcher(o).matches()
    val thereIsNoUpperCase = !upperCaseCharacter.pattern.matcher(o).matches()
    val thereIsNoNumber = !numberCharacter.pattern.matcher(o).matches()
    val thereIsNoSpecialCharacter = !specialCharacter.pattern.matcher(o).matches()

    if (o == null)
      Invalid(ValidationError("error.passwordEmpty"))
    else if (o.size < owaspMinLength)
      Invalid(ValidationError(s"The password must be at least $owaspMinLength characters long.", o.size))
    else if (o.size > owaspMaxLength)
      Invalid(ValidationError(s"The password must be fewer than $owaspMaxLength characters long.", o.size))
    else if (thereAreSequences)
      Invalid(ValidationError("The password may not contain sequences of three or more repeated characters.", o))
    else if (thereIsNoLowerCase)
      Invalid(ValidationError("The password must contain at least one lowercase letter.", o))
    else if (thereIsNoUpperCase)
      Invalid(ValidationError("The password must contain at least one uppercase letter.", o))
    else if (thereIsNoNumber)
      Invalid(ValidationError("The password must contain at least one number.", o))
    else if (thereIsNoSpecialCharacter)
      Invalid(ValidationError("The password must contain at least one special character.", o))
    else
      Valid
  }

  def usernameValid: Constraint[String] = Constraint[String]("constraint.owasp-strenght") { o =>

    val usernameValid = usernameRegex.pattern.matcher(o).matches()

    if (o == null)
      Invalid(ValidationError("error.usernameEmpty"))
    else if (!usernameValid)
      Invalid(ValidationError("The username must only have digits, letters, and the character -", o))
    else
      Valid
  }

  val signUpForm = Form(
    mapping(
      "id" -> ignored(None: Option[Long]),
      "email" -> email.verifying(maxLength(250)),
      "emailConfirmed" -> ignored(false),
      "password" -> text.verifying(owaspValid),
      "nick" -> text.verifying(usernameValid),
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "services" -> list(nonEmptyText),
      "salt" -> ignored("": String)
    )(User.apply)(User.unapply)
  )

  /**
   * Starts the sign up mechanism. It shows a form that the user have to fill in and submit.
   */
  def startSignUp = UserAwareAction { implicit request =>
    request.identity match {
      case Some(_) => Redirect(routes.Application.index)
      case None => Ok(viewsAuth.signUp(signUpForm))
    }
  }

  /**
   * Handles the form filled by the user. The user and its password are saved and it sends him an email with a link to confirm his email address.
   */
  def handleStartSignUp = UnsecuredAction.async { implicit request =>
    signUpForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(viewsAuth.signUp(formWithErrors))),
      user => {
        val loginInfo: LoginInfo = user.email
        userService.retrieve(loginInfo).flatMap {
          case Some(_) => Future.successful(BadRequest(viewsAuth.signUp(signUpForm.withError("email", Messages("auth.user.notunique")))))
          case None => {
            val token = MailTokenUser(user.email, isSignUp = true)
            for {
              savedUser <- { Logger.logger.info("INTENTANDO GUARDAR USUARIO -> " + user); userService.save(user) }
              _ <- { Logger.logger.info("INTENTANDO GUARDAR CONTRASEÑA? -> " + loginInfo); authInfoRepository.add(loginInfo, passwordHasherRegistry.current.hash(user.password)) }
              _ <- { Logger.logger.info("INTENTANDO GUARDAR TOKEN? -> " + token); tokenService.create(token) }
            } yield {
              mailer.welcome(savedUser, link = routes.Auth.signUp(token.id).absoluteURL())
              Ok(viewsAuth.almostSignedUp(savedUser))
            }
          }
        }
      }
    )
  }

  /**
   * Confirms the user's email address based on the token and authenticates him.
   */
  def signUp(tokenId: String) = UnsecuredAction.async { implicit request =>
    tokenService.retrieve(tokenId).flatMap {
      case Some(token) if (token.isSignUp && !token.isExpired) => {
        userService.retrieve(token.email).flatMap {
          case Some(user) => {
            env.authenticatorService.create(user.email).flatMap { authenticator =>
              if (!user.emailConfirmed) {
                userService.save(user.copy(emailConfirmed = true)).map { newUser =>
                  env.eventBus.publish(SignUpEvent(newUser, request))
                }
              }
              for {
                cookie <- env.authenticatorService.init(authenticator)
                result <- env.authenticatorService.embed(cookie, Ok(viewsAuth.signedUp(user)))
              } yield {
                tokenService.consume(tokenId)
                env.eventBus.publish(LoginEvent(user, request))
                result
              }
            }
          }
          case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
        }
      }
      case Some(token) => {
        tokenService.consume(tokenId)
        notFoundDefault
      }
      case None => notFoundDefault
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // SIGN IN

  val signInForm = Form(tuple(
    "identifier" -> email,
    "password" -> nonEmptyText,
    "rememberMe" -> boolean
  ))

  /**
   * Starts the sign in mechanism. It shows the login form.
   */
  def signIn = UserAwareAction { implicit request =>
    request.identity match {
      case Some(user) => Redirect(routes.Application.index)
      case None => Ok(viewsAuth.signIn(signInForm))
    }
  }

  /**
   * Authenticates the user based on his email and password
   */
  def authenticate = UnsecuredAction.async { implicit request =>
    signInForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(viewsAuth.signIn(formWithErrors)))
      },
      formData => {
        val (identifier, password, rememberMe) = formData
        val entryUri = request.session.get("ENTRY_URI")
        val targetUri: String = entryUri.getOrElse(routes.Application.index.toString)
        credentialsProvider.authenticate(Credentials(identifier, password)).flatMap { loginInfo =>
          userService.retrieve(loginInfo).flatMap {
            case Some(user) =>
              for {
                authenticator <- env.authenticatorService.create(loginInfo).map(authenticatorWithRememberMe(_, rememberMe))
                cookie <- env.authenticatorService.init(authenticator)
                result <- env.authenticatorService.embed(cookie, Redirect(targetUri).withSession(request.session - "ENTRY_URI"))
              } yield {
                env.eventBus.publish(LoginEvent(user, request))
                result
              }
            case None =>
              Future.failed(new IdentityNotFoundException("Couldn't find user"))
          }
        }.recover {
          case e: ProviderException => {
            e.printStackTrace()
            Redirect(routes.Auth.signIn).flashing("error" -> Messages("auth.credentials.incorrect"))
          }
        }
      }
    )
  }

  private def authenticatorWithRememberMe(authenticator: CookieAuthenticator, rememberMe: Boolean) = {
    if (rememberMe) {
      authenticator.copy(
        expirationDateTime = clock.now + rememberMeParams._1,
        idleTimeout = rememberMeParams._2,
        cookieMaxAge = rememberMeParams._3
      )
    } else
      authenticator
  }
  private lazy val rememberMeParams: (FiniteDuration, Option[FiniteDuration], Option[FiniteDuration]) = {
    val cfg = conf.getConfig("silhouette.authenticator.rememberMe").get.underlying
    (
      cfg.as[FiniteDuration]("authenticatorExpiry"),
      cfg.getAs[FiniteDuration]("authenticatorIdleTimeout"),
      cfg.getAs[FiniteDuration]("cookieMaxAge")
    )
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // SIGN OUT

  /**
   * Signs out the user
   */
  def signOut = SecuredAction.async { implicit request =>
    env.eventBus.publish(LogoutEvent(request.identity, request))
    env.authenticatorService.discard(request.authenticator, Redirect(routes.Application.index))
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // FORGOT PASSWORD

  val emailForm = Form(single("email" -> email))

  /**
   * Starts the reset password mechanism if the user has forgot his password. It shows a form to insert his email address.
   */
  def forgotPassword = UserAwareAction { implicit request =>
    request.identity match {
      case Some(_) => Redirect(routes.Application.index)
      case None => Ok(viewsAuth.forgotPassword(emailForm))
    }
  }

  /**
   * Sends an email to the user with a link to reset the password
   */
  def handleForgotPassword = UnsecuredAction.async { implicit request =>
    emailForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(viewsAuth.forgotPassword(formWithErrors))),
      email => userService.retrieve(email).flatMap {
        case Some(_) => {
          val token = MailTokenUser(email, isSignUp = false)
          tokenService.create(token).map { _ =>
            mailer.forgotPassword(email, link = routes.Auth.resetPassword(token.id).absoluteURL())
            Ok(viewsAuth.forgotPasswordSent(email))
          }
        }
        case None => Future.successful(BadRequest(viewsAuth.forgotPassword(emailForm.withError("email", Messages("auth.user.notexists")))))
      }
    )
  }

  val resetPasswordForm = Form(tuple(
    "password1" -> passwordValidation,
    "password2" -> nonEmptyText
  ) verifying (Messages("auth.passwords.notequal"), passwords => passwords._2 == passwords._1))

  /**
   * Confirms the user's link based on the token and shows him a form to reset the password
   */
  def resetPassword(tokenId: String) = UnsecuredAction.async { implicit request =>
    tokenService.retrieve(tokenId).flatMap {
      case Some(token) if (!token.isSignUp && !token.isExpired) => {
        Future.successful(Ok(viewsAuth.resetPassword(tokenId, resetPasswordForm)))
      }
      case Some(token) => {
        tokenService.consume(tokenId)
        notFoundDefault
      }
      case None => notFoundDefault
    }
  }

  /**
   * Saves the new password and authenticates the user
   */
  def handleResetPassword(tokenId: String) = UnsecuredAction.async { implicit request =>
    resetPasswordForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(viewsAuth.resetPassword(tokenId, formWithErrors))),
      passwords => {
        tokenService.retrieve(tokenId).flatMap {
          case Some(token) if (!token.isSignUp && !token.isExpired) => {
            val loginInfo: LoginInfo = token.email
            userService.retrieve(loginInfo).flatMap {
              case Some(user) => {
                for {
                  _ <- authInfoRepository.update(loginInfo, passwordHasherRegistry.current.hash(passwords._1))
                  authenticator <- env.authenticatorService.create(user.email)
                  result <- env.authenticatorService.renew(authenticator, Ok(viewsAuth.resetedPassword(user)))
                } yield {
                  tokenService.consume(tokenId)
                  env.eventBus.publish(LoginEvent(user, request))
                  result
                }
              }
              case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
            }
          }
          case Some(token) => {
            tokenService.consume(tokenId)
            notFoundDefault
          }
          case None => notFoundDefault
        }
      }
    )
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // CHANGE PASSWORD

  val changePasswordForm = Form(tuple(
    "current" -> nonEmptyText,
    "password1" -> passwordValidation,
    "password2" -> nonEmptyText
  ) verifying (Messages("auth.passwords.notequal"), passwords => passwords._3 == passwords._2))

  /**
   * Starts the change password mechanism. It shows a form to insert his current password and the new one.
   */
  def changePassword = SecuredAction { implicit request =>
    Ok(viewsAuth.changePassword(changePasswordForm))
  }

  /**
   * Saves the new password and renew the cookie
   */
  def handleChangePassword = SecuredAction.async { implicit request =>
    changePasswordForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(viewsAuth.changePassword(formWithErrors))),
      passwords => {
        credentialsProvider.authenticate(Credentials(request.identity.email, passwords._1)).flatMap { loginInfo =>
          for {
            _ <- authInfoRepository.update(loginInfo, passwordHasherRegistry.current.hash(passwords._2))
            authenticator <- env.authenticatorService.create(loginInfo)
            result <- env.authenticatorService.renew(authenticator, Redirect(routes.Application.myAccount).flashing("success" -> Messages("auth.password.changed")))
          } yield result
        }.recover {
          case e: ProviderException => BadRequest(viewsAuth.changePassword(changePasswordForm.withError("current", Messages("auth.currentpwd.incorrect"))))
        }
      }
    )
  }
}