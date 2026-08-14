package com.example.sillybilibili.service

import com.example.sillybilibili.domain.model.OnlineVideoStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineVideoStatusClassifierTest {

    @Test
    fun `a successful Bilibili response is online`() {
        assertEquals(
            OnlineVideoStatus.ONLINE,
            OnlineVideoStatusClassifier.fromPayload("{\"code\":0,\"data\":{\"aid\":1}}")
        )
    }

    @Test
    fun `a not found Bilibili response is unavailable`() {
        assertEquals(
            OnlineVideoStatus.UNAVAILABLE,
            OnlineVideoStatusClassifier.fromPayload("{\"code\":-404,\"message\":\"啥都木有\"}")
        )
    }

    @Test
    fun `an access or malformed response stays unverifiable`() {
        assertEquals(OnlineVideoStatus.UNVERIFIABLE, OnlineVideoStatusClassifier.fromPayload("{\"code\":-403}"))
        assertEquals(OnlineVideoStatus.UNVERIFIABLE, OnlineVideoStatusClassifier.fromPayload("not json"))
    }
}
