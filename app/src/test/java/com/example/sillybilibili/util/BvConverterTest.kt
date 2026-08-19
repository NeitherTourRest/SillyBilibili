package com.example.sillybilibili.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BvConverterTest {

    @Test
    fun `official example av170001 maps to BV17x411w7KC`() {
        assertEquals("BV17x411w7KC", BvConverter.avidToBv(170001L))
        assertEquals(170001L, BvConverter.bvToAvid("BV17x411w7KC"))
    }

    @Test
    fun `documentation example round trips`() {
        assertEquals("BV1L9Uoa9EUx", BvConverter.avidToBv(111_298_867_365_120L))
        assertEquals(111_298_867_365_120L, BvConverter.bvToAvid("BV1L9Uoa9EUx"))
    }

    @Test
    fun `small avids round trip`() {
        for (avid in listOf(1L, 2L, 1_000L, 1_632_794_017L, 170_001L)) {
            val bv = BvConverter.avidToBv(avid)
            assertEquals(avid, BvConverter.bvToAvid(bv!!))
        }
    }

    @Test
    fun `maximum avids round trip`() {
        assertEquals(2_251_799_813_685_247L, BvConverter.bvToAvid(BvConverter.avidToBv(2_251_799_813_685_247L)!!))
    }

    @Test
    fun `invalid inputs return null`() {
        assertNull(BvConverter.avidToBv(0L))
        assertNull(BvConverter.avidToBv(-1L))
        assertNull(BvConverter.bvToAvid(""))
        assertNull(BvConverter.bvToAvid("AV17x411w7KC"))
        assertNull(BvConverter.bvToAvid("BV17x411w7K!"))
    }
}
