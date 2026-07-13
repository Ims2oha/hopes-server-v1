package kr.hs.gsm.hopes

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hs.gsm.hopes.domain.EmailVerificationRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@SpringBootTest(
    properties = [
        "hopes.mail.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:hopes-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    ]
)
@AutoConfigureMockMvc
class HopesApiIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val verificationRepository: EmailVerificationRepository,
) {
    @Test
    fun `signup login chat and settings flow`() {
        val email = "s20000@gsm.hs.kr"
        mockMvc.post("/api/signup/email-verifications") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email"}"""
        }.andExpect { status { isAccepted() } }
        val verificationCode = verificationRepository.findById(email).orElseThrow().code

        val signupResult = mockMvc.post("/api/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{
              "email":"$email", "username":"tester", "password":"password1",
              "passwordConfirm":"password1", "verificationCode":"$verificationCode",
              "gender":"NONE", "major":"software", "cohort":10
            }"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.accessToken") { exists() }
        }.andReturn()

        val token = objectMapper.readTree(signupResult.response.contentAsString)["accessToken"].asText()
        val authorization = "Bearer $token"

        val loginResult = mockMvc.post("/api/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"tester","password":"password1"}"""
        }.andExpect { status { isOk() } }.andReturn()
        assert(objectMapper.readTree(loginResult.response.contentAsString)["accessToken"].asText().isNotBlank())

        val chatResult = mockMvc.post("/api/chats") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Kotlin 질문"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val chatId = objectMapper.readTree(chatResult.response.contentAsString)["id"].asLong()

        mockMvc.post("/api/chats/$chatId/messages") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"서버를 어떻게 실행해?"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.messages[0].role") { value("USER") }
        }

        mockMvc.patch("/api/general") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON
            content = """{"theme":"DARK"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.theme") { value("DARK") }
        }

        mockMvc.get("/api/main") {
            header("Authorization", authorization)
        }.andExpect {
            status { isOk() }
            jsonPath("$.chatList[0].title") { value("Kotlin 질문") }
        }
    }
}
