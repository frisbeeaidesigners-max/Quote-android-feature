package com.example.template.core.data

import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.Call
import com.example.template.core.model.Chat
import com.example.template.core.model.CurrentUser
import com.example.template.core.model.MediaAttachment
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.model.ReplyPreview
import com.example.template.core.model.Space
import com.example.template.core.model.User
import kotlinx.coroutines.flow.StateFlow

interface MessengerRepository {
    val currentUser: StateFlow<CurrentUser>
    val users: StateFlow<List<User>>
    val personas: StateFlow<List<Persona>>
    val spaces: StateFlow<List<Space>>
    val currentSpaceId: StateFlow<String>
    fun setCurrentSpace(id: String)

    val p2pChats: StateFlow<List<Chat>>
    /** All Group/Channel chats across every space. */
    val allSpaceChats: StateFlow<List<Chat>>
    fun spaceChats(spaceId: String): StateFlow<List<Chat>>

    val calls: StateFlow<List<Call>>

    suspend fun loadMessages(chatId: String): StateFlow<List<Message>>
    suspend fun sendTextMessage(chatId: String, body: String, replyTo: ReplyPreview? = null)
    suspend fun sendMediaMessage(
        chatId: String,
        attachments: List<MediaAttachment>,
        caption: String?,
        replyTo: ReplyPreview? = null,
    )

    suspend fun sendVoiceMessage(
        chatId: String,
        durationMs: Long,
        replyTo: ReplyPreview? = null,
    )

    suspend fun toggleReaction(chatId: String, messageId: String, emoji: String)
    suspend fun deleteMessage(chatId: String, messageId: String)

    suspend fun editTextMessage(chatId: String, messageId: String, newBody: String)

    suspend fun editMediaMessage(
        chatId: String,
        messageId: String,
        newAttachments: List<MediaAttachment>,
        newCaption: String?,
    )

    suspend fun forwardMessagesToSaved(messages: List<Message>)

    fun getUser(id: String): User?
    fun getPersona(id: String): Persona?
    fun personaForUser(userId: String): Persona?
    fun getChat(id: String): Chat?

    suspend fun recordOutgoingCall(
        counterpartId: String,
        counterpartName: String,
        counterpartAvatar: AvatarSpec,
        durationMs: Long,
        isVideo: Boolean,
        isGroupCall: Boolean,
    )

    suspend fun appendCallMeetMessage(
        chatId: String,
        durationMs: Long,
        isVideo: Boolean,
        isGroupCall: Boolean,
    )
}
