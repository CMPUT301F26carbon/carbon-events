/*
 * DeviceSignOutTest.kt (Rayner)
 *
 * Stage 4 test: written before the stage 5 code. It fails (red) until stage 5 is merged.
 */
package com.carbon.events

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Log out of all devices" and admin remove/block: no phone stays trusted for that person. */
class DeviceSignOutTest {
    private fun device(id: String, user: String) = TrustedDevice(id, user, "Pixel", TestData.NOW)

    @Test fun removeAllDevices_removesEveryPhoneOfThatPersonOnly() = runTest {
        val store = InMemoryEventsStore(EventsSnapshot(devices = mapOf(
            "a1" to device("a1", "adam"), "a2" to device("a2", "adam"), "m1" to device("m1", "maya"),
        )))
        store.removeAllDevices("adam")
        assertTrue(store.observeDevices("adam").first().isEmpty())
        assertEquals(listOf("m1"), store.observeDevices("maya").first().map { it.deviceId })
    }

    @Test fun removeAllDevices_withNoPhones_doesNothing() = runTest {
        val store = InMemoryEventsStore()
        store.removeAllDevices("nobody")
        assertTrue(store.observeDevices("nobody").first().isEmpty())
    }

    @Test fun signedOutPhone_noLongerResolvesToThePerson() = runTest {
        val store = InMemoryEventsStore(EventsSnapshot(devices = mapOf("a1" to device("a1", "adam"))))
        assertEquals("adam", store.observeUserIdForDevice("a1").first())
        store.removeAllDevices("adam")
        assertEquals(null, store.observeUserIdForDevice("a1").first())
    }
}
