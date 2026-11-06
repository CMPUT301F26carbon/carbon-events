/*
 * SplashGreetingTest.kt (Aditya)
 *
 * Purpose: unit tests for the "Welcome back" greeting, kept only on a trusted phone.
 *
 * Outstanding issues: none known.
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
