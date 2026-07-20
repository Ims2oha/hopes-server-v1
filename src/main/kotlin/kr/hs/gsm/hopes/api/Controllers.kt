package kr.hs.gsm.hopes.api

import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletRequest
import kr.hs.gsm.hopes.service.AuthService
import kr.hs.gsm.hopes.service.ChatService
import kr.hs.gsm.hopes.service.UserService
import kr.hs.gsm.hopes.service.VerificationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class AuthController(
    private val authService: AuthService,
    private val verificationService: VerificationService,
) {
    @PostMapping("/signup/email-verifications")
    fun requestVerification(
        @Valid @RequestBody request: EmailVerificationRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<MessageEnvelope> {
        verificationService.request(request.email, httpRequest.remoteAddr)
        return ResponseEntity.accepted().body(MessageEnvelope("인증번호를 발송했습니다"))
    }

    @PostMapping("/signup/email-verifications/confirm")
    fun confirmVerification(
        @Valid @RequestBody request: EmailVerificationConfirmRequest,
        httpRequest: HttpServletRequest,
    ) = MessageEnvelope("이메일 인증이 완료되었습니다").also {
        verificationService.confirm(request.email, request.code, httpRequest.remoteAddr)
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest, httpRequest: HttpServletRequest) =
        authService.signup(request, httpRequest.remoteAddr)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, httpRequest: HttpServletRequest) =
        authService.login(request, httpRequest.remoteAddr)

    @PostMapping("/password/request")
    fun requestPasswordReset(
        @Valid @RequestBody request: PasswordResetRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<MessageEnvelope> {
        verificationService.request(request.email, httpRequest.remoteAddr)
        return ResponseEntity.accepted().body(MessageEnvelope("비밀번호 변경 인증번호를 발송했습니다"))
    }

    @PostMapping("/password/reset")
    fun resetPassword(
        @Valid @RequestBody request: PasswordResetConfirmRequest,
        httpRequest: HttpServletRequest,
    ) = MessageEnvelope("비밀번호가 변경되었습니다").also {
        authService.resetPassword(request, httpRequest.remoteAddr)
    }

    @PostMapping("/logout")
    fun logout(authentication: Authentication) = MessageEnvelope("로그아웃되었습니다").also {
        authService.logout(authentication.name)
    }
}

@RestController
@RequestMapping("/api")
class MainController(private val chats: ChatService) {
    @GetMapping("/main")
    fun main(
        authentication: Authentication,
        @RequestParam(required = false) searchKeyword: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ) = chats.main(authentication.name, searchKeyword, page, size)

    @PostMapping("/chats")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(authentication: Authentication, @Valid @RequestBody request: CreateChatRequest = CreateChatRequest()) = chats.create(authentication.name, request)

    @GetMapping("/chats/{id}")
    fun get(
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestParam(defaultValue = "0") messagePage: Int,
        @RequestParam(defaultValue = "50") messageSize: Int,
    ) = chats.get(authentication.name, id, messagePage, messageSize)

    @PostMapping("/chats/{id}/messages")
    fun send(authentication: Authentication, @PathVariable id: Long, @Valid @RequestBody request: SendMessageRequest) = chats.send(authentication.name, id, request)
}

@RestController
@RequestMapping("/api")
class SettingsController(private val users: UserService) {
    @PatchMapping("/general")
    fun general(authentication: Authentication, @Valid @RequestBody request: ThemeRequest) = mapOf("theme" to users.setTheme(authentication.name, request.theme))

    @GetMapping("/mypage")
    fun myPage(authentication: Authentication) = users.response(users.requireUser(authentication.name))

    @PatchMapping("/mypage")
    fun updateMyPage(authentication: Authentication, @Valid @RequestBody request: MyPageUpdateRequest) = users.update(authentication.name, request)

    @GetMapping("/setting/main")
    fun settingMain(authentication: Authentication) = users.settings(authentication.name)

    @PatchMapping("/setting")
    fun setting(authentication: Authentication, @Valid @RequestBody request: SettingUpdateRequest) = users.updateSettings(authentication.name, request)

    @PostMapping("/setting/inquiry")
    fun inquiry(authentication: Authentication, @Valid @RequestBody request: InquiryRequest): ResponseEntity<MessageEnvelope> {
        users.submitInquiry(authentication.name, request.content)
        return ResponseEntity.accepted().body(MessageEnvelope("문의가 접수되었습니다"))
    }
}
