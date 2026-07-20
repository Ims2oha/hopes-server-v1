package kr.hs.gsm.hopes.ai

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import kotlin.math.sqrt

/** Google Generative Language REST API 호출 담당 (임베딩 + 답변 생성). */
@Service
class GeminiClient(
    @Value("\${hopes.ai.gemini-api-key}") private val apiKey: String,
    @Value("\${hopes.ai.chat-model}") private val chatModel: String,
    @Value("\${hopes.ai.embedding-model}") private val embeddingModel: String,
    // 답변은 저장 시 12000자로 잘리므로 그보다 길게 생성하면 비용·대기시간만 낭비된다. 생성 단계에서 상한.
    @Value("\${hopes.ai.max-output-tokens:2048}") private val maxOutputTokens: Int = 2048,
) {
    val hasKey: Boolean get() = apiKey.isNotBlank()

    private val rest: RestClient = RestClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta")
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5_000)
            setReadTimeout(60_000)
        })
        .defaultHeader("x-goog-api-key", apiKey)
        .build()

    /**
     * 텍스트 목록을 임베딩한다. taskType: RETRIEVAL_DOCUMENT(청크) / RETRIEVAL_QUERY(질문).
     * gemini-embedding-001은 768차원 축소 시 정규화가 보장되지 않으므로 직접 L2 정규화한다.
     * 정규화된 벡터끼리는 내적 = 코사인 유사도.
     */
    fun embed(texts: List<String>, taskType: String): List<DoubleArray> =
        texts.chunked(100).flatMap { batch ->
            val body = mapOf(
                "requests" to batch.map { text ->
                    mapOf(
                        "model" to "models/$embeddingModel",
                        "content" to mapOf("parts" to listOf(mapOf("text" to text))),
                        "taskType" to taskType,
                        "outputDimensionality" to EMBED_DIM,
                    )
                }
            )
            val response = rest.post()
                .uri("/models/{model}:batchEmbedContents", embeddingModel)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode::class.java)
                ?: throw IllegalStateException("임베딩 응답이 비어 있습니다")
            response["embeddings"].map { node ->
                normalize(node["values"].map { it.asDouble() }.toDoubleArray())
            }
        }

    /** 시스템 프롬프트 + 대화 턴(role: "user"|"model", text)으로 답변 텍스트를 생성한다. */
    fun generate(systemPrompt: String, turns: List<Pair<String, String>>): String {
        val body = mapOf(
            "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to systemPrompt))),
            "contents" to turns.map { (role, text) ->
                mapOf("role" to role, "parts" to listOf(mapOf("text" to text)))
            },
            // 낮은 temperature → 창작 억제, 검색된 데이터에 근거한 답변 유도.
            "generationConfig" to mapOf("temperature" to 0.4, "topP" to 0.9, "maxOutputTokens" to maxOutputTokens),
        )
        val response = rest.post()
            .uri("/models/{model}:generateContent", chatModel)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw IllegalStateException("Gemini 응답이 비어 있습니다")
        val parts = response["candidates"]?.get(0)?.get("content")?.get("parts")
            ?: throw IllegalStateException("Gemini 응답에 후보가 없습니다: ${response.toString().take(300)}")
        val text = parts.mapNotNull { it["text"]?.asText() }.joinToString("")
        if (text.isBlank()) throw IllegalStateException("Gemini가 빈 답변을 반환했습니다")
        return text
    }

    private fun normalize(vector: DoubleArray): DoubleArray {
        var sum = 0.0
        for (x in vector) sum += x * x
        val norm = sqrt(sum)
        if (norm == 0.0) return vector
        for (i in vector.indices) vector[i] /= norm
        return vector
    }

    companion object {
        const val EMBED_DIM = 768
    }
}
