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

import org.apache.pekko.util.{ByteString, CompactByteString}
import play.api.Logging
import play.api.http.HttpEntity
import play.api.libs.ws.DefaultBodyWritables.writeableOf_Bytes
import play.api.mvc._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotAcceptableException, StringContextOps}
import uk.gov.hmrc.apimproxy.service.AuthorizationDecorator
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

@Singleton
class PlatformManagementController @Inject()(
                                              override val controllerComponents: ControllerComponents,
                                              httpClient: HttpClientV2,
                                              servicesConfig: ServicesConfig,
                                              authorizationDecorator: AuthorizationDecorator)(implicit ec: ExecutionContext) extends BackendController(controllerComponents) with Logging {

  implicit class HttpClientExtensions(httpClient: HttpClientV2) {
    def httpVerb(method: String, relativePath: String)(implicit hc: HeaderCarrier): RequestBuilder = {
      val url = url"${targetUrl(relativePath)}"

      logger.info(s"Forwarding $method request to resolved path: ${url.toString}")

      method match {
        case "GET" => httpClient.get(url)
        case "POST" => httpClient.post(url)
        case "PUT" => httpClient.put(url)
        case "DELETE" => httpClient.delete(url)
        case "PATCH" => httpClient.patch(url)
        case "OPTIONS" => httpClient.options(url)
        case "HEAD" => httpClient.head(url)
        case _ => throw new IllegalArgumentException(s"No such verb $method")
      }
    }
  }

  def forward: Action[ByteString] = Action(parse.byteString).async {
    implicit request =>
      logger.info(s"Received ${request.method} request for path ${request.path}")
      Try(httpClient
        .httpVerb(request.method, request.path.replaceFirst("/apim-proxy", ""))
      ).fold(
        {
          case NonFatal(e: NotAcceptableException) => Future.successful(NotAcceptable(e.message))
          case NonFatal(e) => Future.successful(InternalServerError(e.getMessage))
        },
        builder => {
          val builderWithContentType = request.headers.get(CONTENT_TYPE) match {
            case Some(contentType) =>
              builder.setHeader(CONTENT_TYPE -> contentType).withBody(request.body)
            case _ =>
              builder.withBody(request.body)
          }

          val builderWithAuthHeaders = if (request.headers.hasHeader(ACCEPT)) {
            builderWithContentType.setHeader((ACCEPT, request.headers.get(ACCEPT).get))
              .transform(wsRequest => authorizationDecorator.decorate(wsRequest, request.headers.get(AUTHORIZATION)))
          } else {
            builderWithContentType.transform(wsRequest => authorizationDecorator.decorate(wsRequest, request.headers.get(AUTHORIZATION)))
          }

          builderWithAuthHeaders.execute[HttpResponse]
            .map(
              response => {
                logger.info(s"Received response code ${response.status} and body ${response.body}")
                Result(
                  ResponseHeader(
                    status = response.status,
                    headers = buildHeaders(response.headers)
                  ),
                  body = buildBody(response.body, response.headers)
                )
              }
            )
          }
      )
  }

  private def buildBody(body: String, headers: Map[String, Seq[String]]): HttpEntity = {
    if (body.isEmpty) {
      HttpEntity.NoEntity
    }
    else {
      HttpEntity.Strict(CompactByteString(body), buildContentType(headers))
    }
  }


  private def buildContentType(headers: Map[String, Seq[String]]): Option[String] = {
    headers
      .find(_._1.equalsIgnoreCase("content-type"))
      .map(_._2.head)
  }


  private def buildHeaders(headers: Map[String, Seq[String]]): Map[String, String] = {
    headers
      .map {
        case (header, values) => (header, values.head)
      }
      .filter {
        case (header, _) if header.equalsIgnoreCase("content-type") => false
        case (header, _) if header.equalsIgnoreCase("content-length") => false
        case _ => true
      }
  }

  private def targetUrl(relativePath: String): String = {
    val (baseUrl, servicePath, path) = relativePath match {
      case s"/platform-management/$servicePath" =>
        (
          servicesConfig.baseUrl("platform-management-api"),
          servicesConfig.getConfString("platform-management-api.path", ""),
          servicePath
        )
      case s"/api-hub-apim-stubs/$servicePath" =>
        (
          servicesConfig.baseUrl("api-hub-apim-stubs"),
          servicesConfig.getConfString("api-hub-apim-stubs.path", ""),
          servicePath
        )
      case unsupportedPath => throw new NotAcceptableException(s"Unsupported path $unsupportedPath")
    }

    if (servicePath.isEmpty) {
      s"$baseUrl/$path"
    }
    else {
      s"$baseUrl/$servicePath/$path"
    }
  }
}