package com.example.template.core.data

import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonParsingTest {

    private val json = AppJson

    @Test
    fun `parses Message Text via type discriminator`() {
        val src = """
            {"type":"text","id":"m-1","chatId":"c-1","senderId":"u-1","timestamp":1715000000000,
             "isMine":false,"status":"NONE","reactions":[],"replyTo":null,"body":"Hi","formatting":[]}
        """.trimIndent()
        val msg = json.decodeFromString<Message>(src)
        assertTrue(msg is Message.Text)
        assertEquals("Hi", (msg as Message.Text).body)
    }

    @Test
    fun `parses Message Voice`() {
        val src = """
            {"type":"voice","id":"m-2","chatId":"c-1","senderId":"u-1","timestamp":1,
             "isMine":true,"status":"READ","reactions":[],"replyTo":null,
             "durationMs":4200,"waveform":[1,2,3]}
        """.trimIndent()
        val msg = json.decodeFromString<Message>(src)
        assertTrue(msg is Message.Voice)
        assertEquals(4200L, (msg as Message.Voice).durationMs)
        assertEquals(MessageStatus.READ, msg.status)
    }
}
