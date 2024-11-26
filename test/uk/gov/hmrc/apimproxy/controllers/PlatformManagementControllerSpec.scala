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

import com.github.tomakehurst.wiremock.client.WireMock.{status => _, _}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{spy, verify}
import org.scalatest.OptionValues
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.ContentTypes
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{ACCEPT, _}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.{Application, Configuration}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.apimproxy.service.AuthorizationDecorator
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class PlatformManagementControllerSpec extends AsyncFreeSpec
  with Matchers
  with WireMockSupport
  with HttpClientV2Support
  with OptionValues
  with MockitoSugar
  with TableDrivenPropertyChecks {

  "GET request" - {
    "must be forwarded with headers" in {
      val responseBody = """{"jam": "scones"}"""
      forAll(Table(
        "backends",
        "internal",
        "api-hub-apim-stubs"
      )) {
        backend =>
        stubFor(
          get(urlEqualTo("/apim-path/oas-deployments"))
            .withHeader(ACCEPT, equalTo(ContentTypes.JSON))
            .withHeader(AUTHORIZATION, equalTo("Basic dGVzdC1lbXMtY2xpZW50LWlkOnRlc3QtZW1zLXNlY3JldA=="))
            .willReturn(
              aResponse()
                .withBody(responseBody)
            )
        )

        val fixture = buildApplication()
        running(fixture.application) {
          val request = FakeRequest(GET, s"/apim-proxy/$backend/oas-deployments")
            .withHeaders(FakeHeaders(Seq(
              (AUTHORIZATION, "Basic dGVzdC1lbXMtY2xpZW50LWlkOnRlc3QtZW1zLXNlY3JldA=="),
              (ACCEPT, "application/json")
            )))
          val result = route(fixture.application, request).value

          status(result) mustBe OK
          verify(fixture.authorizationDecorator).decorate(any(), any())
          contentAsString(result) mustBe responseBody
        }
      }
    }

    "must strip out an x-api-key header" in {
      val xApiKeyHeaderName = "x-api-key"
      forAll(Table(
        "backends",
        "internal",
        "api-hub-apim-stubs"
      )) {
        backend =>
          stubFor(
            get(urlEqualTo("/apim-path/oas-deployments"))
              .withHeader(xApiKeyHeaderName, absent())
              .willReturn(
                aResponse()
              )
          )

          val fixture = buildApplication()
          running(fixture.application) {
            val request = FakeRequest(GET, s"/apim-proxy/$backend/oas-deployments")
              .withHeaders(FakeHeaders(Seq(
                (xApiKeyHeaderName, "test-api-key")
              )))

            val result = route(fixture.application, request).value

            status(result) mustBe OK
          }
      }
    }

    "must work for all configured APIs" in {
      val apis = Table(
        "API",
        "/oas-deployments",
        "/simple-api-deployment"
      )

      val fixture = buildApplication()
      running(fixture.application) {
        forAll(apis) {
          api =>
            stubFor(
              get(urlEqualTo(s"/apim-path$api/test-endpoint"))
                .willReturn(
                  aResponse()
                )
            )

            val request = FakeRequest(GET, s"/apim-proxy/internal$api/test-endpoint")
            val result = route(fixture.application, request).value

            status(result) mustBe OK
        }
      }
    }

    "must preserve content type header when present" in {
      val responseBody = """{"jam": "scones"}"""

      forAll(Table(
        "backends",
        "internal",
        "api-hub-apim-stubs"
      )) {
        backend =>

          stubFor(
            post(urlEqualTo("/apim-path/oas-deployments"))
              .withHeader(ACCEPT, equalTo(ContentTypes.JSON))
              .withHeader(CONTENT_TYPE, equalTo(ContentTypes.FORM))
              .withHeader(AUTHORIZATION, equalTo("Basic dGVzdC1lbXMtY2xpZW50LWlkOnRlc3QtZW1zLXNlY3JldA=="))
              .willReturn(
                aResponse()
                  .withBody(responseBody)
              )
          )

          val fixture = buildApplication()
          running(fixture.application) {
            val request = FakeRequest(POST, s"/apim-proxy/$backend/oas-deployments")
              .withHeaders(FakeHeaders(Seq(
                (ACCEPT, "application/json"),
                (CONTENT_TYPE, ContentTypes.FORM),
                (AUTHORIZATION, "Basic dGVzdC1lbXMtY2xpZW50LWlkOnRlc3QtZW1zLXNlY3JldA==")
              )))
            val result = route(fixture.application, request).value

            status(result) mustBe OK
            contentAsString(result) mustBe responseBody
          }
      }
    }
  }

  case class Fixture(
                      application: Application,
                      authorizationDecorator: AuthorizationDecorator
                    )

  private def buildApplication(): Fixture = {
    val servicesConfig = new ServicesConfig(
      Configuration.from(Map(
        "microservice.services.platform-management-api.host" -> wireMockHost,
        "microservice.services.platform-management-api.port" -> wireMockPort,
        "microservice.services.platform-management-api.path" -> "apim-path",
        "microservice.services.api-hub-apim-stubs.host" -> wireMockHost,
        "microservice.services.api-hub-apim-stubs.port" -> wireMockPort,
        "microservice.services.api-hub-apim-stubs.path" -> "apim-path"
      ))
    )

    val decorator = spy(new AuthorizationDecorator)

    val build = new GuiceApplicationBuilder()
      .overrides(
        bind[ServicesConfig].toInstance(servicesConfig),
        bind[HttpClientV2].toInstance(httpClientV2),
        bind[AuthorizationDecorator].toInstance(decorator)
      )
      .build()
    Fixture(build, decorator)
  }

}
