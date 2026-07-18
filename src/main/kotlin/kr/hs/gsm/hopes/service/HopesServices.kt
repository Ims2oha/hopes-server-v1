package kr.hs.gsm.hopes.service

import kr.hs.gsm.hopes.ai.AiChatService
import kr.hs.gsm.hopes.api.*
import kr.hs.gsm.hopes.domain.*
import kr.hs.gsm.hopes.security.AccessTokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.Instant

@Service
class VerificationService(
    private val repository: EmailVerificationRepository,
    private val mailService: VerificationMailService,
    @Value("\${hopes.verification.ttl-minutes}") private val ttlMinutes: Long,
) {
    fun request(email: String) {
        requireSchoolEmail(email)
        val code = "%06d".format(SecureRandom().nextInt(1_000_000))
        mailService.sendVerificationCode(email.lowercase(), code, ttlMinutes)
        repository.save(EmailVerification(email.lowercase(), code, Instant.now().plusSeconds(ttlMinutes * 60), false))
    }

    fun confirm(email: String, code: String) {
        val verification = repository.findById(email.lowercase()).orElseThrow {
            ApiException(HttpStatus.BAD_REQUEST, "인증번호를 먼저 요청해주세요")
        }
        if (verification.expiresAt.isBefore(Instant.now()) || verification.code != code) {
            throw ApiException(HttpStatus.BAD_REQUEST, "잘못된 인증 코드입니다.")
        }
        verification.verified = true
        repository.save(verification)
    }

    fun consume(email: String, code: String) {
        confirm(email, code)
        val verification = repository.findById(email.lowercase()).orElseThrow()
        if (!verification.verified) throw ApiException(HttpStatus.BAD_REQUEST, "이메일 인증이 필요합니다")
        repository.delete(verification)
    }

    private fun requireSchoolEmail(email: String) {
        if (!email.lowercase().endsWith("@gsm.hs.kr")) {
            throw ApiException(HttpStatus.BAD_REQUEST, "학교 이메일만 사용할 수 있습니다")
        }
    }
}

@Service
class AuthService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: AccessTokenService,
    private val verificationService: VerificationService,
) {
    @Transactional
    fun signup(request: SignupRequest): TokenResponse {
        if (request.password != request.passwordConfirm) {
            throw ApiException(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다")
        }
        val email = request.email.lowercase()
        if (users.existsByEmail(email) || users.existsByUsername(request.username)) {
            throw ApiException(HttpStatus.CONFLICT, "이미 가입된 회원입니다")
        }
        verificationService.consume(email, request.verificationCode)
        val user = users.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode(request.password),
                username = request.username.trim(),
                nickname = request.username.trim(),
                gender = request.gender,
                major = request.major,
                cohort = request.cohort,
            )
        )
        return TokenResponse(tokenService.create(user.email))
    }

    fun login(request: LoginRequest): TokenResponse {
        val key = request.username.trim()
        val user = users.findByEmail(key.lowercase()) ?: users.findByUsername(key)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "등록된 회원을 찾을 수 없습니다.")
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "등록된 회원을 찾을 수 없습니다.")
        }
        return TokenResponse(tokenService.create(user.email))
    }

    @Transactional
    fun resetPassword(request: PasswordResetConfirmRequest) {
        verificationService.consume(request.email, request.code)
        val user = users.findByEmail(request.email.lowercase())
            ?: throw ApiException(HttpStatus.NOT_FOUND, "등록된 회원을 찾을 수 없습니다.")
        if (!request.newPassword.matches(Regex("^(?=.*[A-Za-z])(?=.*\\d).{8,15}$"))) {
            throw ApiException(HttpStatus.BAD_REQUEST, "비밀번호는 영문과 숫자를 포함하여 8자 이상이어야 합니다.")
        }
        user.passwordHash = passwordEncoder.encode(request.newPassword)
    }
}

@Service
class UserService(
    private val users: UserRepository,
    private val conversations: ConversationRepository,
    private val messages: ChatMessageRepository,
) {
    fun requireUser(email: String): User = users.findByEmail(email)
        ?: throw ApiException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다")

    fun response(user: User) = UserResponse(
        user.username, user.email, user.nickname, user.profileInfo, user.profileImage,
        user.gender, user.major, user.cohort,
    )

    @Transactional
    fun update(email: String, request: MyPageUpdateRequest): UserResponse {
        val user = requireUser(email)
        request.username?.trim()?.takeIf { it.isNotEmpty() }?.let {
            val blockedWords = listOf("관리자", "admin", "운영자")
            if (blockedWords.any { word -> it.contains(word, ignoreCase = true) }) {
                throw ApiException(HttpStatus.BAD_REQUEST, "이름에 부적절한 단어가 포함 되어 있습니다")
            }
            val duplicate = users.findByUsername(it)
            if (duplicate != null && duplicate.id != user.id) throw ApiException(HttpStatus.CONFLICT, "이미 사용 중인 이름입니다")
            user.username = it
        }
        request.nickname?.let { user.nickname = it.trim() }
        request.profileInfo?.let { user.profileInfo = it.trim() }
        request.profileImage?.let { user.profileImage = it.trim().ifEmpty { null } }
        return response(user)
    }

    @Transactional
    fun setTheme(email: String, theme: Theme): Theme {
        requireUser(email).theme = theme
        return theme
    }

    fun settings(email: String): SettingMainResponse {
        val user = requireUser(email)
        return SettingMainResponse(response(user), user.theme, user.customPrompt)
    }

    @Transactional
    fun updateSettings(email: String, request: SettingUpdateRequest): SettingMainResponse {
        val user = requireUser(email)
        request.customPrompt?.let { user.customPrompt = it.trim() }
        if (request.deleteAllChats) {
            conversations.findAllByUserIdOrderByUpdatedAtDesc(user.id!!).forEach { conversation ->
                messages.deleteAll(messages.findAllByConversationIdOrderByCreatedAtAsc(conversation.id!!))
            }
            conversations.deleteAllByUserId(user.id!!)
        }
        return SettingMainResponse(response(user), user.theme, user.customPrompt)
    }
}

@Service
class ChatService(
    private val users: UserService,
    private val conversations: ConversationRepository,
    private val messages: ChatMessageRepository,
    private val ai: AiChatService,
    private val transactionTemplate: TransactionTemplate,
    private val rateLimiter: RateLimiter,
) {
    fun main(email: String, keyword: String?): MainResponse {
        val user = users.requireUser(email)
        val chats = if (keyword.isNullOrBlank()) {
            conversations.findAllByUserIdOrderByUpdatedAtDesc(user.id!!)
        } else {
            conversations.findAllByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(user.id!!, keyword)
        }
        return MainResponse(chats.map(::summary), true, keyword)
    }

    @Transactional
    fun create(email: String, request: CreateChatRequest): ChatResponse {
        val user = users.requireUser(email)
        val conversation = conversations.save(Conversation(user = user, title = request.title?.trim()?.takeIf { it.isNotEmpty() } ?: "새 대화"))
        return detail(conversation)
    }

    fun get(email: String, id: Long): ChatResponse = detail(requireConversation(users.requireUser(email), id))

    fun send(email: String, id: Long, request: SendMessageRequest): ChatResponse {
        rateLimiter.checkMessage(email)
        val user = users.requireUser(email)
        val conversation = requireConversation(user, id)
        if (ai.enabled && !ai.isReady()) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI가 아직 준비 중입니다. 잠시 후 다시 시도해주세요")
        }
        val content = request.content.trim()
        // 이번 질문을 저장하기 전의 내역을 확보해야 모델에 직전 대화 맥락이 전달된다.
        val history = messages.findAllByConversationIdOrderByCreatedAtAsc(conversation.id!!)
        // Gemini 호출은 최대 60초까지 걸릴 수 있어 트랜잭션(DB 커넥션) 밖에서 실행한다.
        // 실패하면 여기서 예외가 전파되어 아무것도 저장되지 않으므로 클라이언트는 같은 내용으로 재시도하면 된다.
        val answer = if (ai.enabled) ai.reply(user, history, content) else null
        transactionTemplate.executeWithoutResult {
            messages.save(ChatMessage(conversation = conversation, role = MessageRole.USER, content = content, createdAt = Instant.now()))
            if (conversation.title == "새 대화") conversation.title = content.take(40)
            answer?.let {
                messages.save(ChatMessage(conversation = conversation, role = MessageRole.ASSISTANT, content = it.take(12000), createdAt = Instant.now()))
            }
            conversation.updatedAt = Instant.now()
            conversations.save(conversation)
        }
        return detail(conversation)
    }

    private fun requireConversation(user: User, id: Long): Conversation =
        conversations.findByIdAndUserId(id, user.id!!)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "지난 대화를 찾을 수 없습니다")

    private fun summary(value: Conversation) = ChatSummary(value.id!!, value.title, value.updatedAt)
    private fun detail(value: Conversation) = ChatResponse(
        value.id!!,
        value.title,
        messages.findAllByConversationIdOrderByCreatedAtAsc(value.id!!).map { MessageResponse(it.id!!, it.role, it.content, it.createdAt) },
    )
}
