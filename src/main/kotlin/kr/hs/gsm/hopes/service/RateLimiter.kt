package kr.hs.gsm.hopes.service

import kr.hs.gsm.hopes.api.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 사용자(이메일)별 분당 질문 횟수 제한. Gemini 호출 비용·시간을 지키기 위한 것으로,
 * 0 이하로 설정하면 제한 없음. 단일 서버 인스턴스 기준 인메모리 고정 윈도우 카운터.
 */
@Component
class RateLimiter(
    @Value("\${hopes.rate-limit.messages-per-minute}") private val messagesPerMinute: Int,
) {
    private class Window(val minute: Long, val count: AtomicInteger = AtomicInteger(0))

    private val windows = ConcurrentHashMap<String, Window>()

    fun checkMessage(email: String) {
        if (messagesPerMinute <= 0) return
        val minute = Instant.now().epochSecond / 60
        val window = windows.compute(email) { _, current ->
            if (current == null || current.minute != minute) Window(minute) else current
        }!!
        if (window.count.incrementAndGet() > messagesPerMinute) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요")
        }
    }
}
