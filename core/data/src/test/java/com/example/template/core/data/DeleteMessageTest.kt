package com.example.template.core.data

import com.example.template.core.model.AttachmentType
import com.example.template.core.model.MediaAttachment
import com.example.template.core.model.Message
import com.example.template.core.model.MessagePreview
import com.example.template.core.model.MessageStatus
import com.example.template.core.model.PreviewKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteMessageTest {

    @Test
    fun `previewFromLast falls back to empty when list is empty`() {
        val preview = MockRepositoryImpl.previewFromLast(emptyList())
        assertEquals(
            MessagePreview(text = "", kind = PreviewKind.Text, timestamp = 0L, ownStatus = MessageStatus.NONE),
            preview,
        )
    }

    @Test
    fun `previewFromLast uses last Text message body`() {
        val msgs = listOf<Message>(
            Message.Text(
                id = "m1", chatId = "c", senderId = "u1", timestamp = 100L,
                isMine = false, status = MessageStatus.NONE, body = "old",
            ),
            Message.Text(
                id = "m2", chatId = "c", senderId = "u1", timestamp = 200L,
                isMine = true, status = MessageStatus.DELIVERED, body = "newest",
            ),
        )
        val preview = MockRepositoryImpl.previewFromLast(msgs)
        assertEquals("newest", preview.text)
        assertEquals(200L, preview.timestamp)
        assertEquals(MessageStatus.DELIVERED, preview.ownStatus)
    }

    @Test
    fun `previewFromLast labels Media without caption as Video when only video attached`() {
        val msgs = listOf<Message>(
            Message.Media(
                id = "m1", chatId = "c", senderId = "u1", timestamp = 300L,
                isMine = true, status = MessageStatus.DELIVERED,
                attachments = listOf(
                    MediaAttachment(placeholderColor = "#000", type = AttachmentType.Video, durationMs = 1234L),
                ),
                caption = null,
            ),
        )
        val preview = MockRepositoryImpl.previewFromLast(msgs)
        assertEquals("Видео", preview.text)
        assertEquals(300L, preview.timestamp)
    }

    @Test
    fun `previewFromLast labels Media without caption as Image when only photo attached`() {
        val msgs = listOf<Message>(
            Message.Media(
                id = "m1", chatId = "c", senderId = "u1", timestamp = 400L,
                isMine = true, status = MessageStatus.DELIVERED,
                attachments = listOf(
                    MediaAttachment(placeholderColor = "#000", type = AttachmentType.Photo),
                ),
                caption = null,
            ),
        )
        val preview = MockRepositoryImpl.previewFromLast(msgs)
        assertEquals("Изображение", preview.text)
    }
}
