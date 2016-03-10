Sorus
====================

Sorus take inspiration from [Play Monadic Action](https://github.com/Driox/play-monadic-actions) to extends the DSL outside of Actions.
It provides some syntactic sugar that allows boilerplate-free combination of "classical" type such as Future[Option[A]], Future[Either[A, B]], Future[A] using for-comprehensions. It also provide a simple and powerful way to handle error via Fail

This [article](https://medium.com/@adriencrovetto/130034b21b37) explain in greater detail the problem that this project addresses, and how to use the solution in your own projects.

## Installation

Using sbt :

Current version is 1.0.0

~~~scala
libraryDependencies += "com.github.driox" %% "sorus" % "1.0.0"
~~~

or in your build.sbt 

~~~scala
lazy val sorus = RootProject(file("../sorus"))

lazy val root = (project in file(".")).enablePlugins(PlayScala)
                  .dependsOn(sorus
~~~

## Usage

The DSL adds the `?|` operator to most of the types one could normally encounter (such as `Future[A]`, `Future[Option[A]]`, `Either[B,A]`, Future[Fail \/ A], etc...). The `?|` operator will transform the "error" part of the type to `Fail` (ie. None for Option, Left for Either, etc...) returning an `EitherT[Future, Fail, A]` (which is aliased to `Step[A]` for convenience)
It enables the writing of the whole action as a single for-comprehension.
Implicit convertion allows us to retrive a Future[Fail \/ A] as a result of the for-comprehension

~~~scala
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
~~~

`Fail` will accumulate error and if you compose multiple Fails. It has convenient method to retrive informations about the error : 

~~~scala

val lastErrorMessage:String = fail.message

val exception:Option[Throwable] = fail.getRootException()

val allMessagesInOneString:String = fail.userMessage()

~~~