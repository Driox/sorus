package exemples

import helpers._
import helpers.SorusDSL._
import scala.concurrent.Future
import scalaz._

class BasicExemple extends Sorus {

  // Sample User class
  case class User(id:Option[Long], email:String, validate:Boolean)

  def doSomething(): Future[Fail\/User] = {
    for {
      user <- loadUser(12L)       ?| "Error while loading user"     // <- you don't create Fail yoursefl but the ?| operator do it for you
      _    <- user.validate       ?| "Account need to be validated"
      _    <- logUserAction(user) ?| ()                             // <- You can just forward underlying Fail without adding a message
    } yield {
      user
    }
  }

  private def loadUser(id:Long):Future[Option[User]] = {
    // Load it from DB / API / Services ...
    Future.successful(Some(User(Some(id), "foo@bar.com", false)))
  }

  private def logUserAction(user:User):Future[Fail\/Unit] = {
    for {
      id <- user.id ?| "Can't log action of user wihtout id"
    } yield {
      println(s"user $id access the resource")
    }
  }
}
