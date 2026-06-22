package com.example.template.core.data

import com.example.template.core.model.AttachmentType
import com.example.template.core.model.MediaAttachment
import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import com.example.template.core.model.Reaction
import com.example.template.core.model.ReplyPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditMessageTest {
    private val originalText = Message.Text(
        id = "m-1",
        chatId = "c-1",
        senderId = "u-me",
        timestamp = 1000L,
        isMine = true,
        status = MessageStatus.READ,
        reactions = listOf(Reaction(emoji = "👍", count = 1, isMine = true)),
        replyTo = ReplyPreview(originalId = "m-0", authorName = "Алиса", text = "оригинал"),
        body = "original body",
    )

    private val originalMedia = Message.Media(
        id = "m-2",
        chatId = "c-1",
        senderId = "u-me",
        timestamp = 2000L,
        isMine = true,
        status = MessageStatus.READ,
        reactions = emptyList(),
        replyTo = null,
        attachments = listOf(
            MediaAttachment(placeholderColor = "#fff", type = AttachmentType.Photo),
        ),
        caption = "original caption",
    )

    @Test
    fun `buildEditedTextMessage replaces body and sets isEdited`() {
        val edited = MockRepositoryImpl.buildEditedTextMessage(originalText, newBody = "new body")
        assertEquals("new body", edited.body)
        assertTrue(edited.isEdited)
    }

    @Test
    fun `buildEditedTextMessage preserves id timestamp status reactions and replyTo`() {
        val edited = MockRepositoryImpl.buildEditedTextMessage(originalText, newBody = "x")
        assertEquals(originalText.id, edited.id)
        assertEquals(originalText.chatId, edited.chatId)
        assertEquals(originalText.senderId, edited.senderId)
        assertEquals(originalText.timestamp, edited.timestamp)
        assertEquals(originalText.status, edited.status)
        assertEquals(originalText.reactions, edited.reactions)
        assertEquals(originalText.replyTo, edited.replyTo)
        assertEquals(originalText.isMine, edited.isMine)
    }

    @Test
    fun `buildEditedMediaMessage replaces attachments and caption and sets isEdited`() {
        val newAttachments = listOf(
            MediaAttachment(placeholderColor = "#000", type = AttachmentType.Video),
            MediaAttachment(placeholderColor = "#111", type = AttachmentType.Photo),
        )
        val edited = MockRepositoryImpl.buildEditedMediaMessage(
            originalMedia,
            newAttachments = newAttachments,
            newCaption = "updated",
        )
        assertEquals(newAttachments, edited.attachments)
        assertEquals("updated", edited.caption)
        assertTrue(edited.isEdited)
    }

    @Test
    fun `buildEditedMediaMessage allows clearing caption to null`() {
        val edited = MockRepositoryImpl.buildEditedMediaMessage(
            originalMedia,
            newAttachments = originalMedia.attachments,
            newCaption = null,
        )
        assertEquals(null, edited.caption)
        assertTrue(edited.isEdited)
    }

    @Test
    fun `buildEditedMediaMessage preserves id timestamp status reactions and replyTo`() {
        val edited = MockRepositoryImpl.buildEditedMediaMessage(
            originalMedia,
            newAttachments = emptyList(),
            newCaption = null,
        )
        assertEquals(originalMedia.id, edited.id)
        assertEquals(originalMedia.chatId, edited.chatId)
        assertEquals(originalMedia.senderId, edited.senderId)
        assertEquals(originalMedia.timestamp, edited.timestamp)
        assertEquals(originalMedia.status, edited.status)
        assertEquals(originalMedia.reactions, edited.reactions)
        assertEquals(originalMedia.replyTo, edited.replyTo)
        assertEquals(originalMedia.isMine, edited.isMine)
    }

    @Test
    fun `buildMediaToTextConversion converts Media to Text preserving meta`() {
        val converted = MockRepositoryImpl.buildMediaToTextConversion(
            originalMedia,
            newBody = "только текст",
        )
        assertEquals("только текст", converted.body)
        assertTrue(converted.isEdited)
        assertEquals(originalMedia.id, converted.id)
        assertEquals(originalMedia.chatId, converted.chatId)
        assertEquals(originalMedia.senderId, converted.senderId)
        assertEquals(originalMedia.timestamp, converted.timestamp)
        assertEquals(originalMedia.status, converted.status)
        assertEquals(originalMedia.reactions, converted.reactions)
        assertEquals(originalMedia.replyTo, converted.replyTo)
        assertEquals(originalMedia.isMine, converted.isMine)
        assertEquals(emptyList<com.example.template.core.model.TextSpan>(), converted.formatting)
    }
}
