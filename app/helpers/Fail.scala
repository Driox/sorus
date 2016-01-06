/*
 * Copyright 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package helpers

import scalaz._
  
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