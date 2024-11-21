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

package uk.gov.hmrc.apimproxy.service;

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.ws.WSRequest
import play.api.test.Helpers.{ACCEPT, AUTHORIZATION}

class AuthorizationDecoratorSpec extends AnyFreeSpec
  with Matchers with MockitoSugar {

  "Decorator " - {
        "must not add auth header if already present" in {
            val decorator = new AuthorizationDecorator

            val wsRequest: WSRequest = mock[WSRequest]
            when(wsRequest.headers).thenReturn(Map(AUTHORIZATION -> Seq("test-authorization")))
            decorator.decorate(wsRequest, Some("xyz"))

            verify(wsRequest, times(0)).addHttpHeaders(any())
        }

        "must add the auth header if not present" in {
            val decorator = new AuthorizationDecorator

            val wsRequest: WSRequest = mock[WSRequest]
            when(wsRequest.headers).thenReturn(Map(ACCEPT -> Seq("test-content-type")))
            when(wsRequest.addHttpHeaders((AUTHORIZATION, "test-authorization"))).thenReturn(wsRequest)
            decorator.decorate(wsRequest, Some("test-authorization"))

            verify(wsRequest).addHttpHeaders((AUTHORIZATION, "test-authorization"))
        }

        "must not update the request if there isn't an Authorization header in the inbound request" in {
            val decorator = new AuthorizationDecorator

            val wsRequest: WSRequest = mock[WSRequest]
            when(wsRequest.headers).thenReturn(Map.empty)
            decorator.decorate(wsRequest, None)

            verify(wsRequest, times(0)).addHttpHeaders(any())
        }
    }

}