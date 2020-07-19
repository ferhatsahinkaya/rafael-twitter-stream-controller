package com.rafael.twitter.stream.controller.web

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.rafael.twitter.stream.controller.application.Application
import com.rafael.twitter.stream.controller.web.ControllerTest.Companion.PropertyOverrideContextInitializer
import org.hamcrest.Matchers.emptyString
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream
import kotlin.random.Random.Default.nextLong

@ExtendWith(SpringExtension::class)
@WebMvcTest(controllers = [Controller::class])
@ContextConfiguration(
        initializers = [PropertyOverrideContextInitializer::class],
        classes = [Application::class])
internal class ControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc
    private val jsonBuilder = json().build<ObjectMapper>().setSerializationInclusion(NON_NULL)

    @TestInstance(PER_CLASS)
    @Nested
    inner class GetRules {

        @ParameterizedTest
        @ValueSource(ints = [403, 404, 500])
        fun shouldReturnInternalServerErrorWhenOauthResourceReturnsFailure(statusCode: Int) {
            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(statusCode)))

            mockMvc.perform(get("/rules"))
                    .andExpect(status().`is`(500))
                    .andExpect(content().string(emptyString()))
        }

        @ParameterizedTest
        @ValueSource(ints = [403, 404, 500])
        fun shouldReturnInternalServerErrorWhenGetRulesResourceReturnsFailure(statusCode: Int) {
            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(TwitterGetTokenResponse(TOKEN_TYPE, TOKEN_VALUE)))))

            wireMockServer.stubFor(get(urlMatching(RULES_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .willReturn(aResponse()
                            .withStatus(statusCode)))

            mockMvc.perform(get("/rules"))
                    .andExpect(status().`is`(500))
                    .andExpect(content().string(emptyString()))
        }

        @ParameterizedTest
        @MethodSource("successfulResponses")
        fun shouldReturnSuccessWithRawResponseWhenGetRulesResourceReturnsSuccess(actualResponse: TwitterGetRuleResponse,
                                                                                 expectedResponse: TwitterGetRuleResponse,
                                                                                 description: String) {

            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(TwitterGetTokenResponse(TOKEN_TYPE, TOKEN_VALUE)))))

            wireMockServer.stubFor(get(urlMatching(RULES_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/plain")
                            .withBody(jsonBuilder.writeValueAsString(actualResponse))))

            mockMvc.perform(get("/rules"))
                    .andExpect(status().is2xxSuccessful)
                    .andExpect(content().contentType(APPLICATION_JSON_VALUE))
                    .andExpect(content().json(jsonBuilder.writeValueAsString(expectedResponse)))

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))

            wireMockServer.verify(getRequestedFor(urlMatching(RULES_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE")))
        }

        private fun successfulResponses() = Stream.of(
                Arguments.of(
                        TwitterGetRuleResponse(emptyList()),
                        TwitterGetRuleResponse(emptyList()),
                        "Rule response with no rule"),
                Arguments.of(
                        TwitterGetRuleResponse(listOf(TwitterGetRuleData("id-1", "value-1"))),
                        TwitterGetRuleResponse(listOf(TwitterGetRuleData("id-1", "value-1"))),
                        "Rule response with single rule"),
                Arguments.of(
                        TwitterGetRuleResponse(listOf(
                                TwitterGetRuleData("id-1", "value-1"),
                                TwitterGetRuleData("id-2", "value-2"),
                                TwitterGetRuleData("id-3", "value-3"))),
                        TwitterGetRuleResponse(listOf(
                                TwitterGetRuleData("id-1", "value-1"),
                                TwitterGetRuleData("id-2", "value-2"),
                                TwitterGetRuleData("id-3", "value-3"))),
                        "Rule response with multiple rules"),
                Arguments.of(
                        TwitterGetRuleResponse(listOf(
                                TwitterGetRuleData("id-1", "value-1", "extra-rule-data-field-value-${nextLong()}")), "extra-get-rule-response-field-value-${nextLong()}"),
                        TwitterGetRuleResponse(listOf(
                                TwitterGetRuleData("id-1", "value-1"))),
                        "Rule response with extra fields"))
    }

    data class TwitterGetTokenResponse(@JsonProperty("token_type") val type: String,
                                       @JsonProperty("access_token") val value: String,
                                       @JsonProperty("extra_field") val extraField: String? = null)

    data class TwitterGetRuleResponse(@JsonProperty("data") val data: List<TwitterGetRuleData> = emptyList(),
                                      @JsonProperty("extra_field") val extraField: String? = null)

    data class TwitterGetRuleData(@JsonProperty("id") val id: String,
                                  @JsonProperty("value") val value: String,
                                  @JsonProperty("extra_field") val extraField: String? = null)

    companion object {
        private const val BEARER_TOKEN = "test-bearer-token"
        private const val RULES_PATH = "/test-rules-path"
        private const val OAUTH_PATH = "/test-oauth-path"
        private val TOKEN_TYPE = "token-type-${nextLong()}"
        private val TOKEN_VALUE = "token-value-${nextLong()}"
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort()).apply { start() }

        // TODO Use wiremock server properly

        @AfterAll
        fun tearDown() {
            wireMockServer.stop()
        }

        class PropertyOverrideContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
            override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
                addInlinedPropertiesToEnvironment(configurableApplicationContext,
                        "twitter.base-url=${wireMockServer.baseUrl()}",
                        "twitter.rules-path=${RULES_PATH}",
                        "twitter.oauth-path=${OAUTH_PATH}",
                        "twitter.bearer-token=${BEARER_TOKEN}")
            }
        }
    }
}