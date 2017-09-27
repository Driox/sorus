package exemples

import helpers.sorus._
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.Future
import scalaz._

class ControllerExemple extends Controller with SorusPlay with FormatErrorResult {

  // Sample User class
  case class User(id:Option[Long], email:String, validate:Boolean)
  implicit val user_format = Json.format[User]


  def updateUser(user_id:Long) = Action.async(BodyParsers.parse.json) { implicit request =>
    for {
      userUpdate <- request.body.validate[User]                 ?| ()
      currentUser <- loadUser(user_id)                          ?| NotFound
      _    <- currentUser.validate                              ?| "Account need to be validated before any update"
      userToUpdate = currentUser.copy(email = userUpdate.email)
      userUpdated <- saveUser(userToUpdate)                     ?| ()
    } yield {
      Ok(Json.toJson(userUpdated))
    }
  }

  private def loadUser(id:Long):Future[Option[User]] = {
    // Load it from DB / API / Services ...
    Future.successful(Some(User(Some(id), "foo@bar.com", false)))
  }

  private def saveUser(user:User):Future[Fail \/ User] = {
    // Save it to DB / API / Services ...
    Future.successful(\/-(user))
  }
}
