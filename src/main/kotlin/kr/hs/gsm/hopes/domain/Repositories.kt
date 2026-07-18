package kr.hs.gsm.hopes.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun findByUsername(username: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByUsername(username: String): Boolean
}

interface ConversationRepository : JpaRepository<Conversation, Long> {
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: Long): List<Conversation>
    fun findAllByUserIdOrderByUpdatedAtDesc(userId: Long, pageable: Pageable): Slice<Conversation>
    fun findAllByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(userId: Long, title: String, pageable: Pageable): Slice<Conversation>
    fun findByIdAndUserId(id: Long, userId: Long): Conversation?
    fun deleteAllByUserId(userId: Long)
}

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun findAllByConversationIdOrderByCreatedAtAscIdAsc(conversationId: Long): List<ChatMessage>
    fun findAllByConversationIdOrderByCreatedAtDescIdDesc(conversationId: Long, pageable: Pageable): Slice<ChatMessage>

    @Modifying
    @Query("delete from ChatMessage message where message.conversation.id = :conversationId")
    fun deleteAllByConversationId(@Param("conversationId") conversationId: Long): Int
}

interface EmailVerificationRepository : JpaRepository<EmailVerification, String>

interface RateLimitWindowRepository : JpaRepository<RateLimitWindow, String> {
    @Modifying
    @Query("delete from RateLimitWindow window where window.windowMinute < :minute")
    fun deleteExpiredBefore(@Param("minute") minute: Long): Int
}
