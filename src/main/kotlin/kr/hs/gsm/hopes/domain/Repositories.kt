package kr.hs.gsm.hopes.domain

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun findByUsername(username: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByUsername(username: String): Boolean
}

interface ConversationRepository : JpaRepository<Conversation, Long> {
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: Long): List<Conversation>
    fun findAllByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(userId: Long, title: String): List<Conversation>
    fun findByIdAndUserId(id: Long, userId: Long): Conversation?
    fun deleteAllByUserId(userId: Long)
}

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun findAllByConversationIdOrderByCreatedAtAsc(conversationId: Long): List<ChatMessage>
}

interface EmailVerificationRepository : JpaRepository<EmailVerification, String>
