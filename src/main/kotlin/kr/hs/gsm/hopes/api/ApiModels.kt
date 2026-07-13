package kr.hs.gsm.hopes.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import kr.hs.gsm.hopes.domain.MessageRole
import kr.hs.gsm.hopes.domain.Theme
import java.time.Instant

data class SignupRequest(
    @field:Email @field:Pattern(regexp = "^[A-Za-z0-9._%+-]+@gsm\\.hs\\.kr$", message = "학교 이메일만 사용할 수 있습니다")
    val email: String,
    @field:NotBlank val username: String,
    @field:Size(min = 8, max = 15) @field:Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 포함해야 합니다")
    val password: String,
    val passwordConfirm: String,
    val verificationCode: String,
    val gender: String? = null,
    val major: String? = null,
    val cohort: Int? = null,
)

data class LoginRequest(@field:NotBlank val username: String, @field:NotBlank val password: String)
data class EmailVerificationRequest(@field:Email val email: String)
data class EmailVerificationConfirmRequest(@field:Email val email: String, @field:NotBlank val code: String)
data class PasswordResetRequest(@field:Email val email: String)
data class PasswordResetConfirmRequest(@field:Email val email: String, val code: String, @field:Size(min = 8, max = 15) val newPassword: String)
data class ThemeRequest(val theme: Theme)
data class MyPageUpdateRequest(val username: String? = null, val nickname: String? = null, val profileInfo: String? = null, val profileImage: String? = null)
data class SettingUpdateRequest(val customPrompt: String? = null, val deleteAllChats: Boolean = false)
data class CreateChatRequest(val title: String? = null)
data class SendMessageRequest(@field:NotBlank val content: String)
data class InquiryRequest(@field:NotBlank val content: String)

data class TokenResponse(val accessToken: String, val tokenType: String = "Bearer")
data class UserResponse(val username: String, val email: String, val nickname: String, val profileInfo: String, val profileImage: String?, val gender: String?, val major: String?, val cohort: Int?)
data class ChatSummary(val id: Long, val title: String, val updatedAt: Instant)
data class MessageResponse(val id: Long, val role: MessageRole, val content: String, val createdAt: Instant)
data class ChatResponse(val id: Long, val title: String, val messages: List<MessageResponse>)
data class MainResponse(val chatList: List<ChatSummary>, val newChat: Boolean = true, val searchKeyword: String? = null)
data class SettingMainResponse(val accountSetting: UserResponse, val theme: Theme, val customPrompt: String, val logout: Boolean = true, val inquiry: Boolean = true)
data class MessageEnvelope(val message: String)
