# Error handlong in Scala With Play! Framework

## Problem
If you come from the Java world and use try/catch to handle exception, you may have experience a few trouble in Scala. According to functionnal paradygme, classical try / catch must be avoid as it is a side effect. Scala has his own tools : 
 - [Option](http://www.scala-lang.org/api/current/#scala.Option) allow to handle simple case, mainly "does my data exist ?", e.g. load a user from DB with id = 12. A good tool to avoid NullPointerException
 - [Try](http://www.scala-lang.org/api/current/#scala.util.Try) allow to handle exception with stacktrace, e.g. wrap a Java lib in scala.
 - [Either](http://www.scala-lang.org/api/current/#scala.util.Either) allow to handle exception with custom object (usually a beautiful String). By convention the right side is the data and the left side is the error. 


In tutorial it looks shinny but as soos as we dive in a real application, we use asynchronous call, json parsing, etc...  and we end with type like Future[Either[String, User]] and the ["cage au fold"](https://fr.wikipedia.org/wiki/La_Cage_aux_folles) begin

```
def updateUserAddress(user_id:Long) = {
    request.fold(
        case success(addressJson) => {
            userDao.findById(user_id).map { userOption => 
                userOption.map{ user =>  
                    adrDao.findByUser(user.id.getOrElse(0L)).map{ adr => 
                        adrDao.update(adr, addressJson)
                    }
                }.getOrElse("error message")
            }
        } 
        case error(err) => BadRequest("error message")
    )
}

// or 

def updateStatus(userId: String) = Action.async(parse.json) {
  request =>
    request.body.validate[StatusChange].fold(
      error => Future.successful(BadRequest(JsError.toFlatJson(error)),
      statusUpdate =>
        userService.findById(userId).map{
          mayBeUser =>
            maybeUser.fold(NotFound)(
              socialService.updateStatus(user.credentials, statusChange).map{
                _.fold(
                  error => Forbidden,
                  success => NoContent
                )
              }
            )
        }
}
```


The main issue is the code looks really uggly. If you don't write it yourself it's a pain to understand such code. 
Where is the beautifull scala we have been sold ???

I'll try to explain how we solve this at Particeep with some monadic stuff.  
We use intensivly Play! so every exemple will be base on the Play framework but it may be translate to every project quickly. The code is available on [github](insert ref.)


# first Solution : Monadic Action

After the first controller written like above, I start looking for an alternative. Happilly the good folk of Kanaka-io did "MonadicAction" ([their github](https://github.com/Kanaka-io/play-monadic-actions)). If you speak french, [their slides](https://kanaka-io.github.io/play-monadic-actions/index.html) will explain clearly how it works. For the rest of you (yes I speak french ^^) I'll try a quick explaination : 

A pattern emmerge in our code : we use Future[Option] or Future[Either] on every dao return. We get our result in the future and it may miss or return an error. To avoid the double map (first on future and second on Option/Either) we need a new operator. Scalaz has FutureT that do the job. Basiccly it allow you to do that : 

```
def updateStatus(userId: String) = Action.async(parse.json) {
  request =>
    val result = for {
      statusUpdate <- EitherT(Future.successful(request.body.validate[StatusUpdate].fold(
                        err => BadRequest(JsError.toFlatJson(err)).left,
                        su => su.right
                      )))
      user         <- EitherT(userService.find(userId).map(u => u \/> NotFound))
      _            <- EitherT(socialService.updateStatus(user.credentials, statusUpdate).map{
                         fe =>
                           fe.fold(err => Forbidden.left, identity)
                       })
    } yield NoContent

    result.run.merge
}
```

* It compile !
* we handle error close to the source of the error
* it's still a litle bit complexe to read and it add boilerplate (eg: result.run.merge)

MonadicAction give us a mini-DSL that allow us to rewrite the updateStatus like that 

```
def updateStatus(userId: String) = Action.async(parse.json) {
  request =>
    for {
      statusUpdate <- request.body.validate[StatusUpdate]                        ?| (err => BadRequest(JsError.toFlatJson(err))
      user         <- userService.find(userId)                                   ?| NotFound
      _            <- socialService.updateStatus(user.credentials, statusUpdate) ?| Forbidden
    } yield NoContent
}
```

* It look really nice and clear aka no boilerplate
* Erorrs are handle at the close to the code that generate them and not at the end of flatMaps
* The operator ?| handle a lot of commons cases (Future[Option], Future[Either], Json error, etc...)
* however it only works in controller

At that point we manage to write nice controllers in Play! However there still some issue to fix : 

 * when we got an error, we "loose" the "stacktrace" because we only return the last error. It's because we use Either[String, A] : we need a better error structure 
 * Monadic action only works in controller because it needs to return a "Result". We need to generalize it


# First I introduce our new structure for error : Fail

It's greatly inspire from [this presentation](http://fr.slideshare.net/normation/nrm-scala-iocorrectlymanagingerrorsinscalav13)

```
  case class Fail(message: String, cause: Option[\/[Throwable, Fail]] = None) {

    def info(s: String) = Fail(s, Some(\/-(this)))

    def withEx(ex: Throwable) = this.copy(cause = Some(-\/(ex)))

    def messages(): NonEmptyList[String] = cause match {
      case None              => NonEmptyList(message)
      case Some(-\/(exp))    => message <:: message <:: NonEmptyList(exp.getMessage)
      case Some(\/-(parent)) => message <:: message <:: parent.messages
    }

    def userMessage(): String = messages.list.mkString("", " <- ", "")

    def getRootException(): Option[Throwable] = cause flatMap {
      _ match {
        case -\/(exp)    => Some(exp)
        case \/-(parent) => parent.getRootException
      }
    }
  }
```

Fail allow us to : 

* handle simple error message like String or any Throwable
* chain Fail to keep the hierarchy of exception
* we stick with immutability
* we keep it simple to use

```
try{
    ...
}catch { case err:Exception => Fail("can't query user from DB").withEx(err) }
```

# Second Solution : Sorus

Sorus : don't google it, I write it so I name it ! When I start functionnal programming I often see word that has no meaning (at least without reading dozen of wikipedia pages about Mathematical therory :D ) like modadic, functor or Kleisli. So why not add a new one, just for my personnal pleasure :D

Sorus is the generalisation of the above solution. 


Now that we got Fail we change MonadicAction to handle OptionT[Future, Fail, A] instead of OptionT[Future, Result, A] (TODO : check type) This allow us to use it everywhere in our application !

We change the operator a little bit to handle failure 
```
    def ?|(failureThunk: => String): Step[A] = orFailWith {
      case err: Throwable => Fail(failureThunk).withEx(err)
      case b              => Fail(b.toString).info(failureThunk)
    }
```

and now we can use it this way : 

```
val result:Future[Fail\/JsValue]] = for {
    user:User <- daoResult                          ?| "error message for user dao"
    adr:Address <- adrDao.findByUser(user)          ?| "error message for adr dao"
    enterprise <- enterpriseDao.findByUser(user)    ?| "error message for enterprise dao"
} yield {
    //do stuff with user, adr and enterprise
}
```

We don't even write Fail because the operator handle it for us. If the expression before the operator generate a Throwable, it wrap it into a Fail with our error message. If the expression generate another kind of error (Either.Left, Option.None, etc...) it wrap the error message in a Fail and our new error message.

###A more complex exemple : 

Use case : we want to use Amazon API to retreave a list of books

```
object AmazonService extends SorusExtensions {

  def search(title: String): Future[\/[Fail, List[Book]]] = {
    for {
      xml <- searchRequest(title, 1) ?| "error.amazons.search.request"
      resultPage1 <- parseXml(xml)   ?| "error.amazons.parse.xml"
    } yield {
      resultPage1
    }
  }

  private def searchRequest(title: String, page: Int = 1): Future[\/[Fail, Elem]] = {
    val params = buildParams(title, page)
    for {
      url <- buildRequestUrl(params) ?| "error.amazons.build.request.url"
      response <- WS.url(url).get()  ?| "error.amazons.webservice.request"
    } yield {
      response.xml
    }
  }

  private def parseXml(xml: Elem): Future[List[Book]] = {
    // sample result
    Future.successful(List(Book("Tintin au Congo"), Book("Le trésor de Rakam le rouge")))
  }

  ... more methods ...
}
```

```
class Application extends Controller with MonadicActions {

  def searchAction() = Action.async {
    for {
      books <- AmazonService.search("tintin") ?| (fail => handleFailure(fail))
    } yield {
      Ok(s"some books : $books")
    }
  }

  def handleFailure(fail: Fail) = {
    fail.getRootException.map(Logger.error(fail.userMessage, _))
    BadRequest(fail.userMessage)
  }
}

```

if parseXml throw an Exception, we get the following : 
```
error.amazons.parse.xml <- test exception
```

if buildRequestUrl throw an Exception, we get the following : 
```
error.amazons.search.request <- error.amazons.build.request.url <- test exception
```

This give us flexibility : 

* we get access the root exception
* we can follow error path through our method according we use good error message

#Conclusion

It's really a quick tour about what we can do to improve error handling in Scala. For instance we can go way further with Future such as retry the future on the first timeout or short circuit a external service that's not responding. 
But at least we improve the syntax of for comprehension and error handling 

# who am I
