package com.example.sillybilibili

import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLocaleTest {

    @Test
    fun `main activity is hosted by AppCompat locale delegate`() {
        assertTrue(AppCompatActivity::class.java.isAssignableFrom(MainActivity::class.java))
    }
}
