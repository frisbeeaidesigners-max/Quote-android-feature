package com.example.template.core.data

import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.AvatarType
import com.example.template.core.model.CallStatus
import com.example.template.core.model.CallType
import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingCallRepositoryTest {

    private val target = MockRepositoryImpl.HistoryTargetMock(
        id = "u-01",
        name = "Мария Климова",
        avatar = AvatarSpec(type = AvatarType.Person, initials = "МК", gradientIndex = 1),
    )

    @Test
    fun `buildOutgoingCallRecord creates Outgoing Call with given target`() {
        val call = MockRepositoryImpl.buildOutgoingCallRecord(
            target = target,
            durationMs = 90_000L,
            isVideo = true,
            isGroupCall = false,
            timestamp = 1_700_000_000_000L,
            id = "call-test",
        )
        assertEquals(CallType.Outgoing, call.type)
        assertEquals("u-01", call.counterpartId)
        assertEquals("Мария Климова", call.counterpartName)
        assertEquals("МК", call.counterpartAvatar.initials)
        assertEquals(90_000L, call.durationMs)
        assertTrue(call.isVideo)
        assertEquals(false, call.isGroupCall)
        assertEquals(1_700_000_000_000L, call.timestamp)
        assertEquals("call-test", call.id)
    }

    @Test
    fun `buildCallMeetMessage creates Answered CallMeet with duration`() {
        val msg = MockRepositoryImpl.buildCallMeetMessage(
            chatId = "c-1",
            senderId = "u-self",
            durationMs = 90_000L,
            isVideo = true,
            isGroupCall = false,
            timestamp = 1_700_000_000_000L,
            id = "m-test",
        ) as Message.CallMeet
        assertEquals("c-1", msg.chatId)
        assertEquals("u-self", msg.senderId)
        assertEquals(CallStatus.Answered, msg.callStatus)
        assertEquals(90_000L, msg.durationMs)
        assertTrue(msg.isVideo)
        assertEquals(false, msg.isGroupCall)
        assertEquals(MessageStatus.DELIVERED, msg.status)
        assertEquals(true, msg.isMine)
        assertEquals(1_700_000_000_000L, msg.timestamp)
    }
}
