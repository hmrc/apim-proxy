/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.apimproxy.controllers

import play.api.Logging
import play.api.mvc.RequestHeader
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import uk.gov.hmrc.apimproxy.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject

class PlatformManagementRouter @Inject()(
                                          servicesConfig: ServicesConfig,
                                          config: AppConfig,
                                          platformManagementController: PlatformManagementController)
  extends SimpleRouter
  with Logging {

  private val platformManagementRoute = "platform-management"
  private val stubsRoute = "api-hub-apim-stubs"

  override def routes: Routes = {
    case request if request.path.startsWith(s"/$platformManagementRoute/") =>
      platformManagementController.forward(forwardPath(
            servicesConfig.baseUrl("platform-management-api"),
            servicesConfig.getConfString("platform-management-api.path", ""),
            servicePath(platformManagementRoute, request),
            request))
    case request if request.path.startsWith(s"/$stubsRoute") =>
          platformManagementController.forward(forwardPath(
            servicesConfig.baseUrl("api-hub-apim-stubs"),
            servicesConfig.getConfString("api-hub-apim-stubs.path", ""),
            servicePath(stubsRoute, request),
            request))
  }

  private def servicePath(routeToRemove: String, request: RequestHeader) =
    request.path.replaceFirst(s"/$routeToRemove/", "")

  private def forwardPath(
                           baseUrl: String,
                           path: String,
                           servicePath: String,
                           request: RequestHeader) = {
    logger.info(s"Received ${request.method} request for path ${request.path}")
    if (servicePath.isEmpty) {
        s"$baseUrl/$path"
    } else {
        s"$baseUrl/$path/$servicePath"
    }
  }

}