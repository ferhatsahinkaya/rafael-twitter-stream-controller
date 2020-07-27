package com.rafael.twitter.stream.controller.web

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.rafael.twitter.stream.controller.application.Application
import com.rafael.twitter.stream.controller.web.ControllerTest.Companion.PropertyOverrideContextInitializer
import com.rafael.twitter.stream.controller.web.ControllerTest.Companion.WireMockServerConfiguration
import org.hamcrest.Matchers.emptyString
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
import org.springframework.test.context.TestContext
import org.springframework.test.context.TestExecutionListener
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.stream.Stream
import kotlin.random.Random.Default.nextLong


@ExtendWith(SpringExtension::class)
@WebMvcTest(controllers = [Controller::class])
@ContextConfiguration(
        initializers = [PropertyOverrideContextInitializer::class],
        classes = [Application::class])
@TestExecutionListeners(
        value = [WireMockServerConfiguration::class],
        mergeMode = MERGE_WITH_DEFAULTS)
internal class ControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc
    private val jsonBuilder = json()
            .modulesToInstall(KotlinModule())
            .build<ObjectMapper>().setSerializationInclusion(NON_NULL)

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

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))
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

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))

            wireMockServer.verify(getRequestedFor(urlMatching(RULES_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE")))
        }

        @ParameterizedTest
        @MethodSource("successfulTestCases")
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
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
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

        private fun successfulTestCases() = Stream.of(
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

    @TestInstance(PER_CLASS)
    @Nested
    inner class AddRules {

        @ParameterizedTest
        @ValueSource(ints = [403, 404, 500])
        fun shouldReturnInternalServerErrorWhenOauthResourceReturnsFailure(statusCode: Int) {
            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(statusCode)))

            mockMvc.perform(post("/rules/add")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(jsonBuilder.writeValueAsString(AddRuleRequest(listOf(MentionRequest(userId = "test-account-${nextLong()}"))))))
                    .andExpect(status().`is`(500))
                    .andExpect(content().string(emptyString()))

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))
        }

        @ParameterizedTest
        @ValueSource(ints = [403, 404, 500])
        fun shouldReturnInternalServerErrorWhenAddRulesResourceReturnsFailure(statusCode: Int) {
            val account = "test-account-${nextLong()}"

            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(TwitterGetTokenResponse(TOKEN_TYPE, TOKEN_VALUE)))))

            wireMockServer.stubFor(post(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("@$account"))))))
                    .willReturn(aResponse()
                            .withStatus(statusCode)))

            mockMvc.perform(post("/rules/add")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(jsonBuilder.writeValueAsString(AddRuleRequest(listOf(MentionRequest(userId = account))))))
                    .andExpect(status().`is`(500))
                    .andExpect(content().string(emptyString()))

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))

            wireMockServer.verify(postRequestedFor(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("@$account")))))))
        }

        @ParameterizedTest
        @MethodSource("ruleTypeTestCases")
        fun shouldReturnSuccessWithRawResponseWhenAddRulesResourceReturnsSuccessForRuleType(actualRequest: AddRuleRequest,
                                                                                            actualTwitterRequest: TwitterAddRuleRequest,
                                                                                            actualTwitterResponse: TwitterAddRuleResponse,
                                                                                            expectedResponse: TwitterAddRuleResponse,
                                                                                            description: String) {

            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(TwitterGetTokenResponse(TOKEN_TYPE, TOKEN_VALUE)))))

            wireMockServer.stubFor(post(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(actualTwitterRequest)))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(actualTwitterResponse))))

            mockMvc.perform(post("/rules/add")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(jsonBuilder.writeValueAsString(actualRequest)))
                    .andExpect(status().`is`(200))
                    .andExpect(content().string(jsonBuilder.writeValueAsString(expectedResponse)))

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))

            wireMockServer.verify(postRequestedFor(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(actualTwitterRequest))))
        }

        private fun ruleTypeTestCases() = Stream.of(
                Arguments.of(
                        AddRuleRequest(listOf(MentionRequest(userId = "user-id-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("@user-id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "@user-id-1", id = "id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "@user-id-1", id = "id-1"))),
                        "Mention rule"),

                Arguments.of(
                        AddRuleRequest(listOf(HashtagRequest(hashtag = "hash-tag-value-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("#hash-tag-value-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "#hash-tag-value-1", id = "id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "#hash-tag-value-1", id = "id-1"))),
                        "Hashtag rule"),

                Arguments.of(
                        AddRuleRequest(listOf(ExactMatchRequest(value = "word-1 word-2 word-3"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("word-1 word-2 word-3"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "word-1 word-2 word-3", id = "id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "word-1 word-2 word-3", id = "id-1"))),
                        "Exact match rule"),

                Arguments.of(
                        AddRuleRequest(listOf(FromRequest(userId = "user-id-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("from: \"user-id-1\""))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "from: \"user-id-1\"", id = "id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "from: \"user-id-1\"", id = "id-1"))),
                        "From rule"),

                Arguments.of(
                        AddRuleRequest(listOf(ToRequest(userId = "user-id-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("to: \"user-id-1\""))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "to: \"user-id-1\"", id = "id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "to: \"user-id-1\"", id = "id-1"))),
                        "To rule"),

                Arguments.of(
                        AddRuleRequest(listOf(EntityRequest(entity = "entity-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("entity: \"entity-1\""))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "entity: \"entity-1\"", id = "id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "entity: \"entity-1\"", id = "id-1"))),
                        "Entity rule"),

                Arguments.of(
                        AddRuleRequest(listOf(RetweetsOfRequest(userId = "user-id-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("retweets_of: \"user-id-1\""))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "retweets_of \"user-id-1\"", id = "id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "retweets_of \"user-id-1\"", id = "id-1"))),
                        "RetweetsOf rule"))

        @ParameterizedTest
        @MethodSource("successfulTestCases")
        fun shouldReturnSuccessWithRawResponseWhenAddRulesResourceReturnsSuccess(actualRequest: AddRuleRequest,
                                                                                 actualTwitterRequest: TwitterAddRuleRequest,
                                                                                 actualTwitterResponse: TwitterAddRuleResponse,
                                                                                 expectedResponse: TwitterAddRuleResponse,
                                                                                 description: String) {

            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(TwitterGetTokenResponse(TOKEN_TYPE, TOKEN_VALUE)))))

            wireMockServer.stubFor(post(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(actualTwitterRequest)))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(actualTwitterResponse))))

            mockMvc.perform(post("/rules/add")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(jsonBuilder.writeValueAsString(actualRequest)))
                    .andExpect(status().`is`(200))
                    .andExpect(content().string(jsonBuilder.writeValueAsString(expectedResponse)))

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))

            wireMockServer.verify(postRequestedFor(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(actualTwitterRequest))))
        }

        private fun successfulTestCases() = Stream.of(
                Arguments.of(
                        AddRuleRequest(listOf(MentionRequest(userId = "user-id-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("@user-id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "@user-id-1", id = "id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "@user-id-1", id = "id-1"))),
                        "Response without tag"),
                Arguments.of(
                        AddRuleRequest(listOf(MentionRequest(userId = "user-id-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("@user-id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData("@user-id-1", "id-1", "tag-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData("@user-id-1", "id-1", "tag-1"))),
                        "Response with tag"),
                Arguments.of(
                        AddRuleRequest(
                                listOf(
                                        MentionRequest(userId = "user-id-1"),
                                        MentionRequest(userId = "user-id-2"),
                                        MentionRequest(userId = "user-id-3"))),
                        TwitterAddRuleRequest(
                                listOf(
                                        TwitterAddRuleRequestData("@user-id-1"),
                                        TwitterAddRuleRequestData("@user-id-2"),
                                        TwitterAddRuleRequestData("@user-id-3"))),
                        TwitterAddRuleResponse(
                                listOf(
                                        TwitterAddRuleData("@user-id-1", "id-1", "tag-1"),
                                        TwitterAddRuleData("@user-id-2", "id-2"),
                                        TwitterAddRuleData("@user-id-3", "id-3", "tag-3"))),
                        TwitterAddRuleResponse(
                                listOf(
                                        TwitterAddRuleData("@user-id-1", "id-1", "tag-1"),
                                        TwitterAddRuleData("@user-id-2", "id-2"),
                                        TwitterAddRuleData("@user-id-3", "id-3", "tag-3"))),
                        "Multiple rule requests"),
                Arguments.of(
                        AddRuleRequest(listOf(MentionRequest(userId = "user-id-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("@user-id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "@user-id-1", id = "id-1", extraField = "extra-field-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "@user-id-1", id = "id-1"))),
                        "Response with extra fields"),
                Arguments.of(
                        AddRuleRequest(listOf(MentionRequest(userId = "user-id-1"))),
                        TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData("@user-id-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "@user-id-1", id = "id-1")), listOf(TwitterAddRuleError("title-1", "type-1"))),
                        TwitterAddRuleResponse(listOf(TwitterAddRuleData(value = "@user-id-1", id = "id-1")), listOf(TwitterAddRuleError("title-1", "type-1"))),
                        "Response with error"),
                Arguments.of(
                        AddRuleRequest(
                                listOf(
                                        MentionRequest(userId = "user-id-1"),
                                        MentionRequest(userId = "user-id-2"),
                                        MentionRequest(userId = "user-id-3"))),
                        TwitterAddRuleRequest(
                                listOf(
                                        TwitterAddRuleRequestData("@user-id-1"),
                                        TwitterAddRuleRequestData("@user-id-2"),
                                        TwitterAddRuleRequestData("@user-id-3"))),
                        TwitterAddRuleResponse(
                                listOf(
                                        TwitterAddRuleData(value = "@user-id-1", id = "id-1"),
                                        TwitterAddRuleData(value = "@user-id-2", id = "id-2", extraField = "extra-field-2"),
                                        TwitterAddRuleData(value = "@user-id-3", id = "id-3")),
                                listOf(
                                        TwitterAddRuleError("title-1", "type-1", "extra-field-1"),
                                        TwitterAddRuleError("title-2", "type-2"))),
                        TwitterAddRuleResponse(
                                listOf(
                                        TwitterAddRuleData(value = "@user-id-1", id = "id-1"),
                                        TwitterAddRuleData(value = "@user-id-2", id = "id-2"),
                                        TwitterAddRuleData(value = "@user-id-3", id = "id-3")),
                                listOf(
                                        TwitterAddRuleError("title-1", "type-1"),
                                        TwitterAddRuleError("title-2", "type-2"))),
                        "Response with multiple errors, multiple rule requests and extra fields"))
    }

    @TestInstance(PER_CLASS)
    @Nested
    inner class DeleteRules {

        @ParameterizedTest
        @ValueSource(ints = [403, 404, 500])
        fun shouldReturnInternalServerErrorWhenOauthResourceReturnsFailure(statusCode: Int) {
            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(statusCode)))

            mockMvc.perform(post("/rules/delete")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(jsonBuilder.writeValueAsString(DeleteRuleRequest(listOf(DeleteRuleRequestData("id-${nextLong()}"))))))
                    .andExpect(status().`is`(500))
                    .andExpect(content().string(emptyString()))

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))
        }

        @ParameterizedTest
        @ValueSource(ints = [403, 404, 500])
        fun shouldReturnInternalServerErrorWhenDeleteRulesResourceReturnsFailure(statusCode: Int) {
            val id = "id-${nextLong()}"

            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(TwitterGetTokenResponse(TOKEN_TYPE, TOKEN_VALUE)))))

            wireMockServer.stubFor(post(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(TwitterDeleteRuleRequest(TwitterDeleteRuleRequestData(listOf(id))))))
                    .willReturn(aResponse()
                            .withStatus(statusCode)))

            mockMvc.perform(post("/rules/delete")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(jsonBuilder.writeValueAsString(DeleteRuleRequest(listOf(DeleteRuleRequestData(id))))))
                    .andExpect(status().`is`(500))
                    .andExpect(content().string(emptyString()))

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))

            wireMockServer.verify(postRequestedFor(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(TwitterDeleteRuleRequest(TwitterDeleteRuleRequestData(listOf(id)))))))
        }

        @ParameterizedTest
        @MethodSource("successfulTestCases")
        fun shouldReturnSuccessWithRawResponseWhenDeleteRulesResourceReturnsSuccess(actualRequest: DeleteRuleRequest,
                                                                                    actualTwitterRequest: TwitterDeleteRuleRequest,
                                                                                    actualTwitterResponse: TwitterDeleteRuleResponse,
                                                                                    expectedResponse: TwitterDeleteRuleResponse,
                                                                                    description: String) {

            wireMockServer.stubFor(post(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(TwitterGetTokenResponse(TOKEN_TYPE, TOKEN_VALUE)))))

            wireMockServer.stubFor(post(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(actualTwitterRequest)))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
                            .withBody(jsonBuilder.writeValueAsString(actualTwitterResponse))))

            mockMvc.perform(post("/rules/delete")
                    .contentType(APPLICATION_JSON_VALUE)
                    .content(jsonBuilder.writeValueAsString(actualRequest)))
                    .andExpect(status().`is`(200))
                    .andExpect(content().string(jsonBuilder.writeValueAsString(expectedResponse)))

            wireMockServer.verify(postRequestedFor(urlMatching(OAUTH_PATH))
                    .withHeader("Accept", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("Basic $BEARER_TOKEN")))

            wireMockServer.verify(postRequestedFor(urlMatching(RULES_PATH))
                    .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                    .withHeader("Authorization", equalTo("$TOKEN_TYPE $TOKEN_VALUE"))
                    .withRequestBody(equalToJson(jsonBuilder.writeValueAsString(actualTwitterRequest))))
        }

        private fun successfulTestCases() = Stream.of(
                Arguments.of(
                        DeleteRuleRequest(listOf(DeleteRuleRequestData("rule-id-1"))),
                        TwitterDeleteRuleRequest(TwitterDeleteRuleRequestData(listOf("rule-id-1"))),
                        TwitterDeleteRuleResponse(),
                        TwitterDeleteRuleResponse(),
                        "Response without extra fields"),
                Arguments.of(
                        DeleteRuleRequest(listOf(DeleteRuleRequestData("rule-id-1", "extra-field-1")), "extra-field-2"),
                        TwitterDeleteRuleRequest(TwitterDeleteRuleRequestData(listOf("rule-id-1"))),
                        TwitterDeleteRuleResponse(),
                        TwitterDeleteRuleResponse(),
                        "Request with extra fields"),
                Arguments.of(
                        DeleteRuleRequest(
                                listOf(
                                        DeleteRuleRequestData("rule-id-1"),
                                        DeleteRuleRequestData("rule-id-2"),
                                        DeleteRuleRequestData("rule-id-3"))),
                        TwitterDeleteRuleRequest(TwitterDeleteRuleRequestData(listOf("rule-id-1", "rule-id-2", "rule-id-3"))),
                        TwitterDeleteRuleResponse(),
                        TwitterDeleteRuleResponse(),
                        "Multiple requests"),
                Arguments.of(
                        DeleteRuleRequest(listOf(DeleteRuleRequestData("rule-id-1"))),
                        TwitterDeleteRuleRequest(TwitterDeleteRuleRequestData(listOf("rule-id-1"))),
                        TwitterDeleteRuleResponse(extraField = "extra-field-1"),
                        TwitterDeleteRuleResponse(),
                        "Response with extra fields"),
                Arguments.of(
                        DeleteRuleRequest(listOf(DeleteRuleRequestData("rule-id-1"))),
                        TwitterDeleteRuleRequest(TwitterDeleteRuleRequestData(listOf("rule-id-1"))),
                        TwitterDeleteRuleResponse(errors = listOf(TwitterDeleteRuleError("title-1", "type-1"))),
                        TwitterDeleteRuleResponse(errors = listOf(TwitterDeleteRuleError("title-1", "type-1"))),
                        "Response with error result"),
                Arguments.of(
                        DeleteRuleRequest(listOf(
                                DeleteRuleRequestData("rule-id-1"),
                                DeleteRuleRequestData("rule-id-2"),
                                DeleteRuleRequestData("rule-id-3"))),
                        TwitterDeleteRuleRequest(TwitterDeleteRuleRequestData(listOf("rule-id-1", "rule-id-2", "rule-id-3"))),
                        TwitterDeleteRuleResponse(errors = listOf(
                                TwitterDeleteRuleError("title-1", "type-1"),
                                TwitterDeleteRuleError("title-2", "type-2"),
                                TwitterDeleteRuleError("title-3", "type-3", "extra-field-3"))),
                        TwitterDeleteRuleResponse(errors = listOf(
                                TwitterDeleteRuleError("title-1", "type-1"),
                                TwitterDeleteRuleError("title-2", "type-2"),
                                TwitterDeleteRuleError("title-3", "type-3"))),
                        "Response with multiple errors and extra fields"))
    }

    data class TwitterGetTokenResponse(@JsonProperty("token_type") val type: String,
                                       @JsonProperty("access_token") val value: String,
                                       @JsonProperty("extra_field") val extraField: String? = null)

    data class TwitterGetRuleResponse(val data: List<TwitterGetRuleData> = emptyList(),
                                      @JsonProperty("extra_field") val extraField: String? = null)

    data class TwitterGetRuleData(val id: String,
                                  val value: String,
                                  @JsonProperty("extra_field") val extraField: String? = null)

    data class AddRuleRequest(val data: List<AddRuleRequestData>)

    abstract class AddRuleRequestData(@get:JsonProperty("@type") val type: String)

    data class MentionRequest(val userId: String) : AddRuleRequestData("mention")

    data class HashtagRequest(val hashtag: String) : AddRuleRequestData("hashtag")

    data class ExactMatchRequest(val value: String) : AddRuleRequestData("exact-match")

    data class FromRequest(val userId: String) : AddRuleRequestData("from")

    data class ToRequest(val userId: String) : AddRuleRequestData("to")

    data class EntityRequest(val entity: String) : AddRuleRequestData("entity")

    data class RetweetsOfRequest(val userId: String) : AddRuleRequestData("retweets-of")

    data class TwitterAddRuleRequest(@JsonProperty("add") val data: List<TwitterAddRuleRequestData>)

    data class TwitterAddRuleRequestData(val value: String)

    data class TwitterAddRuleResponse(val data: List<TwitterAddRuleData> = emptyList(),
                                      val errors: List<TwitterAddRuleError> = emptyList(),
                                      @JsonProperty("extra_field") val extraField: String? = null)

    data class TwitterAddRuleData(val value: String,
                                  val id: String,
                                  val tag: String? = null,
                                  @JsonProperty("extra_field") val extraField: String? = null)

    data class TwitterAddRuleError(val title: String,
                                   val type: String,
                                   @JsonProperty("extra_field") val extraField: String? = null)

    data class DeleteRuleRequest(val data: List<DeleteRuleRequestData>,
                                 @JsonProperty("extra_field") val extraField: String? = null)

    data class DeleteRuleRequestData(val id: String,
                                     @JsonProperty("extra_field") val extraField: String? = null)

    data class TwitterDeleteRuleRequest(@JsonProperty("delete") val data: TwitterDeleteRuleRequestData)

    data class TwitterDeleteRuleRequestData(val ids: List<String>)

    data class TwitterDeleteRuleResponse(val errors: List<TwitterDeleteRuleError> = emptyList(),
                                         @JsonProperty("extra_field") val extraField: String? = null)

    data class TwitterDeleteRuleError(val title: String,
                                      val type: String,
                                      @JsonProperty("extra_field") val extraField: String? = null)

    companion object {
        private const val BEARER_TOKEN = "test-bearer-token"
        private const val RULES_PATH = "/test-rules-path"
        private const val OAUTH_PATH = "/test-oauth-path"
        private val TOKEN_TYPE = "token-type-${nextLong()}"
        private val TOKEN_VALUE = "token-value-${nextLong()}"
        private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

        class WireMockServerConfiguration : TestExecutionListener {

            override fun beforeTestClass(testContext: TestContext) {
                wireMockServer.start()
            }

            override fun afterTestClass(testContext: TestContext) {
                wireMockServer.stop()
            }
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