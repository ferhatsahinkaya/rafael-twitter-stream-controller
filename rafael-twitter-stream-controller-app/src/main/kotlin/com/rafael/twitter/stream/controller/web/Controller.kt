package com.rafael.twitter.stream.controller.web

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/rules")
class Controller(@Value("\${twitter.base-url}") val baseUrl: String,
                 @Value("\${twitter.rules-path}") val rulesPath: String,
                 @Value("\${twitter.oauth-path}") val oauthPath: String,
                 @Value("\${twitter.bearer-token}") val bearerToken: String) {
    private val objectMapper = json().build<ObjectMapper>()
    private val rulesResourcePath = "$baseUrl$rulesPath"

    @PostMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun <T : RuleRequest> addRule(@RequestBody addRequest: T) {
        rulesResourcePath
                .httpPost()
                .header("Content-Type", "application/json")
                .header("Authorization", token().let { "${it.type} ${it.value}" })
                .body(objectMapper.writeValueAsString(TwitterAddRuleRequest(listOf(TwitterAddRuleRequestData(addRequest.value())))))
                .responseObject<TwitterAddRuleResponse>()
                .result()
                .also { println("Add Rule Result: $it") }
    }

    @PostMapping("/delete")
    fun <T : RuleRequest> deleteRule(@RequestBody deleteRequest: T) {
        getRules()
                .data
                .filter { it.value == deleteRequest.value() }
                .map { it.id }
                .takeIf { it.isNotEmpty() }
                ?.run {
                    rulesResourcePath
                            .httpPost()
                            .header("Content-Type", "application/json")
                            .header("Authorization", token().let { "${it.type} ${it.value}" })
                            .body(objectMapper.writeValueAsString(TwitterDeleteRuleRequest(TwitterDeleteRuleRequestData(this))))
                            .responseObject<TwitterDeleteRuleResponse>()
                            .result()
                            .also { println("Delete Rule Result: $it") }
                }
    }

    @GetMapping
    fun getRules() =
            rulesResourcePath
                    .httpGet()
                    .header("Accept", "application/json")
                    .header("Authorization", token().let { "${it.type} ${it.value}" })
                    .responseObject<TwitterGetRuleResponse>()
                    .result()
                    .also { println("Get Rules Result: $it") }

    private fun token() =
            "$baseUrl$oauthPath"
                    .httpPost()
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic $bearerToken")
                    .responseObject<TwitterGetTokenResponse>()
                    .result()
                    .also { println("Retrieved Token: $it") }

    @ResponseStatus
    @ExceptionHandler(Exception::class)
    fun exceptionHandler() {
    }

    private fun <T> ResponseResultOf<T>.result(): T {
        val (_, _, result) = this
        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw result.getException()
        }
    }

    data class TwitterGetTokenResponse(@JsonProperty("token_type") val type: String,
                                       @JsonProperty("access_token") val value: String)

    data class TwitterGetRuleResponse(@JsonProperty("data") val data: List<TwitterGetRuleData> = emptyList())

    data class TwitterGetRuleData(@JsonProperty("id") val id: String,
                                  @JsonProperty("value") val value: String)

    data class TwitterAddRuleRequest(val add: List<TwitterAddRuleRequestData>)

    data class TwitterAddRuleRequestData(val value: String)

    data class TwitterAddRuleResponse(@JsonProperty("data") val data: List<TwitterAddRuleData> = emptyList(),
                                      @JsonProperty("errors") val errors: List<TwitterAddRuleError> = emptyList())

    data class TwitterAddRuleData(@JsonProperty("value") val value: String,
                                  @JsonProperty("tag") val tag: String?,
                                  @JsonProperty("id") val id: String)

    data class TwitterAddRuleError(@JsonProperty("title") val title: String,
                                   @JsonProperty("type") val type: String)

    data class TwitterDeleteRuleRequest(val delete: TwitterDeleteRuleRequestData)

    data class TwitterDeleteRuleRequestData(val ids: List<String>)

    data class TwitterDeleteRuleResponse(@JsonProperty("errors") val errors: List<TwitterDeleteRuleError> = emptyList())

    data class TwitterDeleteRuleError(@JsonProperty("title") val title: String,
                                      @JsonProperty("type") val type: String)
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class RuleRequest {
    abstract fun value(): String
}

@JsonTypeName("mention")
data class MentionRequest(val userId: String) : RuleRequest() {
    override fun value() = "@$userId"
}
