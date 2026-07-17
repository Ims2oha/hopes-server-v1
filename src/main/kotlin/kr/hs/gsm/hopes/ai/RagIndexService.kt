package kr.hs.gsm.hopes.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.io.File

// GSM 약어/별칭 → 정식 명칭. 검색 질의 확장과 시스템 프롬프트의 용어 안내에 함께 쓰인다.
val ABBREVIATIONS = mapOf(
    "기자위" to "기숙사자치위원회",
    "자치위" to "기숙사자치위원회",
    "야자" to "야간자율학습",
    "방부" to "방송부",
    "학회" to "학생회",
)

data class RetrievedChunk(
    val chunkId: String,
    val text: String,
    val question: String?,
    val answer: String?,
    val similarity: Double,
)

/**
 * RAG 검색 인덱스. 서버 시작 시 selective-guide 청크(JSONL)를 임베딩해 메모리에 올리고,
 * 질문과의 코사인 유사도로 상위 청크를 찾는다. 임베딩 결과는 파일로 캐시해 재시작 시 재사용.
 */
@Service
final class RagIndexService(
    private val client: GeminiClient,
    private val objectMapper: ObjectMapper,
    private val resourceLoader: ResourceLoader,
    @Value("\${hopes.ai.enabled}") private val enabledFlag: Boolean,
    @Value("\${hopes.ai.chunks-path}") private val chunksPath: String,
    @Value("\${hopes.ai.cache-path}") private val cachePath: String,
    @Value("\${hopes.ai.embedding-model}") private val embeddingModel: String,
    @Value("\${hopes.ai.top-k}") private val topK: Int,
    @Value("\${hopes.ai.min-similarity}") private val minSimilarity: Double,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val enabled: Boolean get() = enabledFlag && client.hasKey

    @Volatile
    var ready: Boolean = false
        private set

    @Volatile
    private var index: List<IndexedChunk> = emptyList()

    private data class IndexedChunk(
        val chunkId: String,
        val text: String,
        val question: String?,
        val answer: String?,
        val embedding: DoubleArray,
    )

    // 같은 질문 반복 시 임베딩 API 호출 생략 (LRU 500개).
    private val queryCache = object : LinkedHashMap<String, DoubleArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DoubleArray>): Boolean = size > 500
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!enabledFlag) {
            log.info("[ai] AI 기능 비활성화 (hopes.ai.enabled=false)")
            return
        }
        if (!client.hasKey) {
            log.warn("[ai] GEMINI_API_KEY가 설정되지 않아 AI 기능이 비활성화됩니다")
            return
        }
        // 시작을 막지 않도록 별도 스레드에서 인덱스 구축. 완료 전 질문은 503 응답.
        Thread {
            try {
                buildIndex()
            } catch (e: Exception) {
                log.error("[ai] RAG 인덱스 구축 실패 — AI 응답이 비활성 상태로 유지됩니다", e)
            }
        }.apply { isDaemon = true; name = "rag-index-build" }.start()
    }

    private fun buildIndex() {
        val chunks = loadChunks()
        val cached = loadCache()
        val missing = chunks.filter { it["chunk_id"].asText() !in cached }
        if (missing.isNotEmpty()) {
            log.info("[ai] 청크 {}개 임베딩 중 (캐시 재사용 {}개)…", missing.size, chunks.size - missing.size)
            val vectors = client.embed(missing.map { it["text"].asText() }, "RETRIEVAL_DOCUMENT")
            missing.forEachIndexed { i, node -> cached[node["chunk_id"].asText()] = vectors[i] }
            saveCache(cached)
        }
        index = chunks.map { node ->
            IndexedChunk(
                chunkId = node["chunk_id"].asText(),
                text = node["text"].asText(),
                question = node["question"]?.asText()?.takeIf { it.isNotBlank() },
                answer = node["answer"]?.asText()?.takeIf { it.isNotBlank() },
                embedding = cached.getValue(node["chunk_id"].asText()),
            )
        }
        ready = true
        log.info("[ai] RAG 인덱스 구축 완료: 청크 {}개 (모델: {})", index.size, embeddingModel)
    }

    /** 질문과 관련된 청크를 최대 topK개 반환. 유사도 미달이면 빈 목록 (호출부는 사실 창작 금지 프롬프트로 처리). */
    fun retrieve(query: String): List<RetrievedChunk> {
        if (!ready || index.isEmpty()) return emptyList()
        val queryVector = embedQueryCached(expandQuery(query))
        return index.asSequence()
            .map { chunk -> chunk to dot(queryVector, chunk.embedding) }
            .sortedByDescending { it.second }
            .take(topK)
            .filter { it.second >= minSimilarity }
            .map { (chunk, similarity) ->
                RetrievedChunk(chunk.chunkId, chunk.text, chunk.question, chunk.answer, similarity)
            }
            .toList()
    }

    /** 질문에 약어가 있으면 정식 명칭을 덧붙여 검색 정확도를 높인다 (원문 보존). */
    fun expandQuery(text: String): String {
        val hits = ABBREVIATIONS.entries
            .filter { (abbr, full) -> text.contains(abbr) && !text.contains(full) }
            .map { (abbr, full) -> "${abbr}는 $full" }
        return if (hits.isEmpty()) text else "$text\n(용어: ${hits.joinToString(", ")})"
    }

    private fun embedQueryCached(text: String): DoubleArray {
        synchronized(queryCache) { queryCache[text]?.let { return it } }
        val vector = client.embed(listOf(text), "RETRIEVAL_QUERY").first()
        synchronized(queryCache) { queryCache[text] = vector }
        return vector
    }

    private fun loadChunks(): List<JsonNode> =
        resourceLoader.getResource(chunksPath).inputStream.bufferedReader(Charsets.UTF_8).readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                try {
                    val node = objectMapper.readTree(trimmed)
                    if (node["chunk_id"]?.asText().isNullOrBlank() || node["text"]?.asText().isNullOrBlank()) null
                    else node
                } catch (e: Exception) {
                    null
                }
            }

    private fun loadCache(): MutableMap<String, DoubleArray> {
        val file = File(cachePath)
        if (!file.exists()) return mutableMapOf()
        return try {
            val root = objectMapper.readTree(file)
            val vectors = root["vectors"]
            if (root["model"]?.asText() != embeddingModel || vectors == null) return mutableMapOf()
            vectors.fieldNames().asSequence().associateWithTo(mutableMapOf()) { name ->
                vectors[name].map { it.asDouble() }.toDoubleArray()
            }
        } catch (e: Exception) {
            log.warn("[ai] 임베딩 캐시 로드 실패 — 새로 임베딩합니다: {}", e.message)
            mutableMapOf()
        }
    }

    private fun saveCache(vectorsById: Map<String, DoubleArray>) {
        try {
            val file = File(cachePath)
            file.parentFile?.mkdirs()
            objectMapper.writeValue(file, mapOf("model" to embeddingModel, "vectors" to vectorsById))
        } catch (e: Exception) {
            log.warn("[ai] 임베딩 캐시 저장 실패: {}", e.message)
        }
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}
