/*
 * SplashGreetingTest.kt (Aditya)
 *
 * Stage 4 test: written before the stage 5 code. It fails (red) until stage 5 is merged.
 */
package com.carbon.events

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Welcome back, <name>" is remembered only for a trusted phone; anything else clears it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplashGreetingTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun trustedPhone_remembersFirstName() {
        ReturningUser.remember(context, "Adam")
        assertEquals("Adam", ReturningUser.firstName(context))
    }

    @Test fun untrustedPhone_clearsTheGreeting() {
        ReturningUser.remember(context, "Adam")
        ReturningUser.remember(context, null)
        assertNull(ReturningUser.firstName(context))
    }
}
