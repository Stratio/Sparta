/**
 * Copyright (C) 2016 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.sparkta.serving.api.service.http

import akka.pattern.ask
import com.stratio.sparkta.serving.api.constants.HttpConstant
import com.stratio.sparkta.serving.api.service.cors.CorsSupport
import com.stratio.sparkta.serving.core.models.TemplateModel
import com.stratio.spray.oauth2.client.OauthClient
import com.stratio.spray.oauth2.client.OauthClientHelper._
import com.wordnik.swagger.annotations._
import spray.routing._

import scala.concurrent.Await
import scala.util.{Failure, Success}
import com.stratio.sparkta.serving.api.actor.TemplateActor._

@Api(value = HttpConstant.TemplatePath,
  description = "Operations about templates. One template will have an abstract" +
    " element that represents a validation, a tip, an icon over it.")
trait TemplateHttpService extends BaseHttpService with OauthClient with CorsSupport {

  override def routes: Route = cors {
    findByType ~ findByTypeAndName
  }

  @ApiOperation(value = "Find all templates depending ot its type. (input|output)",
    notes             = "Find all templates depending ot its type. (input|output)",
    httpMethod        = "GET",
    response          = classOf[TemplateModel],
    responseContainer = "List")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name      = "templateType",
      value     = "type of the template.",
      dataType  = "string",
      required = true,
      paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code    = HttpConstant.NotFound,
      message = HttpConstant.NotFoundMessage)
  ))
  def findByType: Route = {
    secured { user =>
      path(HttpConstant.TemplatePath / Segment) { (templateType) =>
        authorize(hasRole(Seq("*"), user)) {
          get {
            complete {
              val future = supervisor ? new FindByType(templateType)
              Await.result(future, timeout.duration) match {
                case ResponseTemplates(Failure(exception)) => throw exception
                case ResponseTemplates(Success(templates)) => templates
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "Find a template depending ot its type and name. (input|output)",
    notes = "Returns a template.",
    httpMethod = "GET",
    response = classOf[TemplateModel])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name      = "templateType",
      value     = "type of the template.",
      dataType  = "string",
      required = true,
      paramType = "path"),
    new ApiImplicitParam(name      = "name",
      value     = "name of the template",
      dataType  = "string",
      required = true,
      paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = HttpConstant.NotFound,
      message = HttpConstant.NotFoundMessage)
  ))
  def findByTypeAndName: Route = {
    secured { user =>
      path(HttpConstant.TemplatePath / Segment / Segment) { (templateType, name) =>
        authorize(hasRole(Seq("*"), user)) {
          get {
            complete {
              val future = supervisor ? new FindByTypeAndName(templateType, name)
              Await.result(future, timeout.duration) match {
                case ResponseTemplate(Failure(exception)) => throw exception
                case ResponseTemplate(Success(template)) => template
              }
            }
          }
        }
      }
    }
  }
}
