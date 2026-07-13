package kr.hs.gsm.hopes.domain

import jakarta.persistence.*
import java.time.Instant

enum class Theme { DARK, LIGHT }
enum class MessageRole { USER, ASSISTANT }

@Entity
@Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(unique = true, nullable = false)
    var email: String = "",
    @Column(nullable = false)
    var passwordHash: String = "",
    @Column(nullable = false, unique = true)
    var username: String = "",
    var nickname: String = "",
    var gender: String? = null,
    var major: String? = null,
    var cohort: Int? = null,
    @Column(length = 2000)
    var profileInfo: String = "",
    var profileImage: String? = null,
    @Enumerated(EnumType.STRING)
    var theme: Theme = Theme.LIGHT,
    @Column(length = 4000)
    var customPrompt: String = "",
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "conversations")
class Conversation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    var user: User = User(),
    @Column(nullable = false)
    var title: String = "새 대화",
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "messages")
class ChatMessage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    var conversation: Conversation = Conversation(),
    @Enumerated(EnumType.STRING)
    var role: MessageRole = MessageRole.USER,
    @Column(nullable = false, length = 12000)
    var content: String = "",
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "email_verifications")
class EmailVerification(
    @Id
    var email: String = "",
    var code: String = "",
    var expiresAt: Instant = Instant.now(),
    var verified: Boolean = false,
)
