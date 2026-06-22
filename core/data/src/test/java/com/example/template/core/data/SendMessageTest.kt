package com.example.template.core.data

import com.example.template.core.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SendMessageTest {
    @Test
    fun `status sequence is DELIVERED then READ`() {
        // SENDING-этап отключён: новое сообщение публикуется сразу как DELIVERED.
        assertEquals(
            listOf(MessageStatus.DELIVERED, MessageStatus.READ),
            StatusTransitions.sequence,
        )
    }

    @Test
    fun `intervals match sequence transitions count`() {
        assertEquals(
            StatusTransitions.sequence.size - 1,
            StatusTransitions.intervalsMs.size,
        )
    }

    @Test
    fun `buildSentTextMessage carries replyTo snapshot`() {
        val reply = com.example.template.core.model.ReplyPreview(
            originalId = "m-source",
            authorName = "Алиса",
            text = "оригинал",
        )
        val msg = MockRepositoryImpl.buildSentTextMessage(
            chatId = "c-1",
            senderId = "u-me",
            body = "ответ",
            replyTo = reply,
            timestamp = 1L,
        )
        assertEquals(reply, msg.replyTo)
        assertEquals("ответ", msg.body)
        assertEquals(true, msg.isMine)
    }

    @Test
    fun `buildSentTextMessage without replyTo is null`() {
        val msg = MockRepositoryImpl.buildSentTextMessage(
            chatId = "c-1",
            senderId = "u-me",
            body = "ответ",
            replyTo = null,
            timestamp = 1L,
        )
        assertEquals(null, msg.replyTo)
    }

    @Test
    fun `buildSentMediaMessage carries replyTo snapshot`() {
        val reply = com.example.template.core.model.ReplyPreview(
            originalId = "m-source",
            authorName = "Боб",
            text = "видео",
        )
        val att = com.example.template.core.model.MediaAttachment(
            placeholderColor = "#000",
            type = com.example.template.core.model.AttachmentType.Photo,
        )
        val msg = MockRepositoryImpl.buildSentMediaMessage(
            chatId = "c-1",
            senderId = "u-me",
            attachments = listOf(att),
            caption = "пдпсь",
            replyTo = reply,
            timestamp = 2L,
        )
        assertEquals(reply, msg.replyTo)
        assertEquals("пдпсь", msg.caption)
        assertEquals(1, msg.attachments.size)
    }

    @Test
    fun `buildForwardedTextMessage carries body and metadata`() {
        val original = com.example.template.core.model.Message.Text(
            id = "m-source",
            chatId = "chat-original",
            senderId = "u-bob",
            timestamp = 1_000L,
            isMine = false,
            status = MessageStatus.READ,
            body = "привет",
        )
        val forwarded = MockRepositoryImpl.buildForwardedTextMessage(
            original = original,
            targetChatId = "chat-14",
            senderId = "u-me",
            timestamp = 9_999L,
        )
        assertEquals("fwd-9999", forwarded.id)
        assertEquals("chat-14", forwarded.chatId)
        assertEquals("u-me", forwarded.senderId)
        assertEquals(9_999L, forwarded.timestamp)
        assertEquals(true, forwarded.isMine)
        assertEquals(MessageStatus.READ, forwarded.status)
        assertEquals("привет", forwarded.body)
        assertEquals(null, forwarded.replyTo)
        assertEquals(emptyList<com.example.template.core.model.Reaction>(), forwarded.reactions)
    }

    @Test
    fun `buildForwardedMediaMessage preserves attachments and caption`() {
        val att = com.example.template.core.model.MediaAttachment(
            placeholderColor = "#abc",
            type = com.example.template.core.model.AttachmentType.Photo,
        )
        val original = com.example.template.core.model.Message.Media(
            id = "m-src",
            chatId = "chat-original",
            senderId = "u-bob",
            timestamp = 1_000L,
            isMine = false,
            status = MessageStatus.READ,
            attachments = listOf(att),
            caption = "подпись",
        )
        val forwarded = MockRepositoryImpl.buildForwardedMediaMessage(
            original = original,
            targetChatId = "chat-14",
            senderId = "u-me",
            timestamp = 9_999L,
        )
        assertEquals("fwd-9999", forwarded.id)
        assertEquals("chat-14", forwarded.chatId)
        assertEquals("u-me", forwarded.senderId)
        assertEquals(true, forwarded.isMine)
        assertEquals(MessageStatus.READ, forwarded.status)
        assertEquals(listOf(att), forwarded.attachments)
        assertEquals("подпись", forwarded.caption)
        assertEquals(null, forwarded.replyTo)
    }

    @Test
    fun `buildForwardedBatch preserves order and has monotonic ids`() {
        val a = com.example.template.core.model.Message.Text(
            id = "m-a", chatId = "src", senderId = "u-bob",
            timestamp = 1L, isMine = false, body = "A",
        )
        val b = com.example.template.core.model.Message.Text(
            id = "m-b", chatId = "src", senderId = "u-bob",
            timestamp = 2L, isMine = false, body = "B",
        )
        val out = MockRepositoryImpl.buildForwardedBatch(
            messages = listOf(a, b),
            targetChatId = "chat-14",
            senderId = "u-me",
            baseTimestamp = 100L,
        )
        assertEquals(2, out.size)
        assertEquals(listOf("fwd-100", "fwd-101"), out.map { it.id })
        assertEquals("A", (out[0] as com.example.template.core.model.Message.Text).body)
        assertEquals("B", (out[1] as com.example.template.core.model.Message.Text).body)
    }

    @Test
    fun `buildForwardedBatch skips Voice Link CallMeet System`() {
        val text = com.example.template.core.model.Message.Text(
            id = "m1", chatId = "src", senderId = "u-bob",
            timestamp = 1L, isMine = false, body = "t",
        )
        val voice = com.example.template.core.model.Message.Voice(
            id = "m2", chatId = "src", senderId = "u-bob",
            timestamp = 2L, isMine = false, durationMs = 1000, waveform = emptyList(),
        )
        val link = com.example.template.core.model.Message.Link(
            id = "m3", chatId = "src", senderId = "u-bob",
            timestamp = 3L, isMine = false, url = "https://x", title = "t",
        )
        val callmeet = com.example.template.core.model.Message.CallMeet(
            id = "m4", chatId = "src", senderId = "u-bob",
            timestamp = 4L, isMine = false,
            callStatus = com.example.template.core.model.CallStatus.Answered,
            isVideo = false, isGroupCall = false,
        )
        val media = com.example.template.core.model.Message.Media(
            id = "m5", chatId = "src", senderId = "u-bob",
            timestamp = 5L, isMine = false,
            attachments = emptyList(), caption = "x",
        )
        val out = MockRepositoryImpl.buildForwardedBatch(
            messages = listOf(text, voice, link, callmeet, media),
            targetChatId = "chat-14",
            senderId = "u-me",
            baseTimestamp = 100L,
        )
        assertEquals(2, out.size)
        assertEquals(listOf("fwd-100", "fwd-101"), out.map { it.id })
        assertEquals(true, out[0] is com.example.template.core.model.Message.Text)
        assertEquals(true, out[1] is com.example.template.core.model.Message.Media)
    }
}
