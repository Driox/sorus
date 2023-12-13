package exemples

import helpers.sorus._
import play.api.libs.json.{ JsValue, Json, OFormat }
import play.api.mvc._
import scalaz._

import scala.concurrent.Future

class ControllerExemple(
  parse: PlayBodyParsers
) extends InjectedController with SorusPlay[Request[_]] with FormatErrorResult[Request[_]] {

  // Sample User class
  case class User(id: Option[Long], email: String, validate: Boolean)
  implicit val user_format: OFormat[User] = Json.format[User]

  def updateUser(user_id: Long): Action[JsValue] = Action.async(parse.json) { implicit request =>
    for {
      userUpdate  <- request.body.validate[User] ?| ()
      currentUser <- loadUser(user_id)           ?| NotFound
      _           <- currentUser.validate        ?| "Account need to be validated before any update"
      userToUpdate = currentUser.copy(email = userUpdate.email)
      userUpdated <- saveUser(userToUpdate)      ?| ()
    } yield {
      Ok(Json.toJson(userUpdated))
    }
  }

  private def loadUser(id: Long): Future[Option[User]] = {
    // Load it from DB / API / Services ...
    Future.successful(Some(User(Some(id), "foo@bar.com", false)))
  }

  private def saveUser(user: User): Future[Fail \/ User] = {
    // Save it to DB / API / Services ...
    Future.successful(\/-(user))
  }
}
