/*
 * SocialDataTest.kt (Tausif)
 *
 * Purpose: Unit tests for the parts of SocialData.kt that no other test calls directly: the
 * campsite rating average and who may rate (US 01.06.01), following a campsite and hearing when
 * it opens a lottery (US 01.06.02), an owner's sent messages, reports to the admins and the admin
 * logs (US 03.08.01), admin warnings (US 03.04.01), photos sent in a chat, and emoji reactions
 * to chat messages.
 *
 * AI use: written with AI assistance (Claude Opus 5.5, Anthropic, November 20, 2026). Prompt: "Write
 * tests for every part of SocialData.kt that isn't tested yet." Reviewed, edited and run by
 * Team Carbon.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialDataTest {
    private val now = TestData.NOW
    private val lot = TestData.lottery()
    private val store = InMemoryEventsStore(EventsSnapshot(
        campsites = mapOf("two-jack" to TestData.campsite()),
        lotteries = mapOf(lot.id to lot),
        entries = listOf(
            WaitlistEntry(lot.id, "won", "Wren Won", now, status = EntryStatus.WON),
            WaitlistEntry(lot.id, "lost", "Lee Lost", now, status = EntryStatus.LOST),
            WaitlistEntry(lot.id, "wait", "Wade Waiting", now),
        ),
    ))

    // ---- Rating ----

    @Test fun aggregate_firstRating() {
        assertEquals(4.0 to 1, RatingPolicy.aggregate(0.0, 0, 4, null))
    }

    @Test fun aggregate_addsANewRating_roundedToOneDecimal() {
        assertEquals(4.3 to 3, RatingPolicy.aggregate(4.5, 2, 4, null))
    }

    @Test fun aggregate_replacesYourOwnEarlierRating_withoutCountingItTwice() {
        // Ratings 5 and 3 (average 4.0): the 5 becomes a 1, so (1 + 3) / 2.
        assertEquals(2.0 to 2, RatingPolicy.aggregate(4.0, 2, 1, previous = 5))
    }

    @Test fun aggregate_onlyAcceptsOneToFiveStars() {
        assertThrows(IllegalArgumentException::class.java) { RatingPolicy.aggregate(4.0, 1, 0, null) }
        assertThrows(IllegalArgumentException::class.java) { RatingPolicy.aggregate(4.0, 1, 6, null) }
    }

    @Test fun onlyAWinnerWhoBookedNights_mayRate() {
        val won = WaitlistEntry(lot.id, "e", "E", now, status = EntryStatus.WON)
        assertFalse("won but not booked", RatingPolicy.canRate(won))
        assertTrue(RatingPolicy.canRate(won.copy(checkInDay = 10, checkOutDay = 12)))
        assertFalse(RatingPolicy.canRate(won.copy(status = EntryStatus.LOST, checkInDay = 10)))
    }

    @Test fun rating_updatesTheCampsiteAverage_andCanBeChangedLater() = runTest {
        store.rate(Rating("two-jack", "won", "Wren Won", 5, "Lovely", lot.id, now))
        store.rate(Rating("two-jack", "other", "Ola", 3, "", lot.id, now + 1))
        assertEquals(4.0, store.snapshot.campsites.getValue("two-jack").rating, 0.001)
        store.rate(Rating("two-jack", "won", "Wren Won", 1, "Changed my mind", lot.id, now + 2))
        val site = store.snapshot.campsites.getValue("two-jack")
        assertEquals(2, site.ratingCount)
        assertEquals(2.0, site.rating, 0.001)
        assertEquals("newest first", listOf("won", "other"), store.observeRatings("two-jack").first().map { it.userId })
    }

    // ---- Following ----

    @Test fun following_canBeTurnedOnAndOff() = runTest {
        assertFalse(store.observeFollowing("two-jack", "maya").first())
        store.setFollowing("two-jack", "maya", "Maya Chen", true, now)
        assertTrue(store.observeFollowing("two-jack", "maya").first())
        store.setFollowing("two-jack", "maya", "Maya Chen", false, now)
        assertFalse(store.observeFollowing("two-jack", "maya").first())
    }

    @Test fun followers_hearWhenTheCampsiteOpensALottery() = runTest {
        store.setFollowing("two-jack", "maya", "Maya Chen", true, now)
        store.setFollowing("two-jack", "liam", "Liam Okafor", true, now)
        store.setFollowing("johnston", "noah", "Noah Patel", true, now)
        assertEquals(2, store.notifyFollowers(lot, now))
        val told = store.snapshot.notifications.filter { it.type == NotificationType.OPENED }
        assertEquals(setOf("maya", "liam"), told.map { it.recipientId }.toSet())
    }

    @Test fun aCampsiteWithNoFollowers_tellsNobody() = runTest {
        assertEquals(0, store.notifyFollowers(lot, now))
    }

    // ---- An owner's messages to the waitlist ----

    @Test fun announcement_goesOnlyToItsAudience_andIsLogged() = runTest {
        assertEquals(1, store.sendAnnouncement(lot, Audience.WINNERS, "Check in after 2 pm.", now))
        assertEquals(3, store.sendAnnouncement(lot, Audience.ALL, "Fire ban this week.", now + 1))
        val sent = store.observeAnnouncements(lot.id).first()
        assertEquals("newest first", listOf("Fire ban this week.", "Check in after 2 pm."), sent.map { it.text })
        assertEquals(listOf(3, 1), sent.map { it.recipientCount })
        assertEquals(2, store.observeAllAnnouncements().first().size)
    }

    @Test fun audiences_matchTheRightPeople() {
        val w = WaitlistEntry(lot.id, "w", "W", now, status = EntryStatus.WON)
        val l = w.copy(status = EntryStatus.LOST)
        val q = w.copy(status = EntryStatus.WAITING)
        assertEquals(listOf(true, true, true), listOf(w, l, q).map(Audience.ALL::includes))
        assertEquals(listOf(true, false, false), listOf(w, l, q).map(Audience.WINNERS::includes))
        assertEquals(listOf(false, true, false), listOf(w, l, q).map(Audience.NOT_SELECTED::includes))
        assertEquals(listOf(false, false, true), listOf(w, l, q).map(Audience.WAITING::includes))
    }

    // ---- Reports and admin logs ----

    @Test fun reports_reachTheAdmins_untilDismissed() = runTest {
        store.report(Report("", "two-jack", "Two Jack Lakeside", "maya", "Maya Chen", "Unsafe or closed", now))
        store.report(Report("", "johnston", "Johnston Canyon", "liam", "Liam Okafor", "Spam or scam", now + 1))
        assertEquals("newest first", listOf("johnston", "two-jack"), store.observeReports().first().map { it.campsiteId })
        store.dismissReports("two-jack")
        assertEquals(listOf("johnston"), store.observeReports().first().map { it.campsiteId })
    }

    @Test fun reportReasons_includeOther() {
        assertTrue("Other" in REPORT_REASONS)
        assertEquals(REPORT_REASONS.size, REPORT_REASONS.toSet().size)
    }

    @Test fun adminLogs_seeEveryChatAndNotification() = runTest {
        val chat = Chat(Chat.idFor("two-jack", "won"), "two-jack", "Two Jack Lakeside", "owner-1", "Parks Lodge", "won", "Wren Won")
        store.sendMessage(chat, "won", "Wren Won", "Is there firewood?", now)
        store.notifyFollowers(lot, now)
        store.warn(UserProfile("lee", "Lee Lost", "lee@example.com"), "Be kind in reviews.", now)
        assertEquals(listOf(chat.id), store.observeAllChats().first().map { it.id })
        assertTrue(store.observeAllNotifications().first().any { it.type == NotificationType.WARNING && it.recipientId == "lee" })
    }

    // ---- Warnings ----

    @Test fun warning_isSentAndCountedOnTheProfile() = runTest {
        val lee = UserProfile("lee", "Lee Lost", "lee@example.com", warnings = 1)
        store.saveProfile(lee)
        store.warn(lee, "Second warning.", now)
        assertEquals(2, store.snapshot.profiles.getValue("lee").warnings)
        val note = store.snapshot.notifications.single { it.recipientId == "lee" }
        assertEquals("Second warning.", note.body)
        assertEquals(NotificationType.WARNING, note.type)
    }

    // ---- Photos in a chat ----

    private val chat = Chat(Chat.idFor("two-jack", "won"), "two-jack", "Two Jack Lakeside", "owner-1", "Priya", "won", "Wren Won", lotteryKey = lot.id)

    @Test fun photoPreview_saysPhoto_withTheCaptionIfThereIsOne() {
        assertEquals("📷 Photo", ChatPhotos.preview("", hasPhoto = true))
        assertEquals("📷 Photo", ChatPhotos.preview("   ", hasPhoto = true))
        assertEquals("📷 Photo · Our site at sunset", ChatPhotos.preview("Our site at sunset", hasPhoto = true))
        assertEquals("Just text", ChatPhotos.preview("Just text", hasPhoto = false))
    }

    @Test fun photoLimits_fitInOneFirestoreDocument() {
        assertTrue(ChatPhotos.MAX_CHARS < 1_000_000)
        assertTrue(ChatPhotos.MAX_SIDE in 640..1600)
    }

    @Test fun sendingAPhoto_keepsIt_andShowsPhotoInTheInbox() = runTest {
        store.sendMessage(chat, "won", "Wren Won", "", now, photoBase64 = "abc123")
        val msg = store.observeMessages(chat.id).first().single()
        assertEquals("abc123", msg.photoBase64)
        assertEquals("", msg.text)
        val saved = store.observeChat(chat.id).first()!!
        assertEquals("📷 Photo", saved.lastText)
        assertEquals(1, saved.unreadForOwner)
        assertEquals("📷 Photo", buildInbox("owner-1", listOf(saved), emptyList()).single().preview)
    }

    @Test fun aPhotoWithACaption_keepsBoth() = runTest {
        store.sendMessage(chat, "owner-1", "Priya", "Your site, number 14", now, photoBase64 = "xyz")
        val msg = store.observeMessages(chat.id).first().single()
        assertEquals("xyz", msg.photoBase64)
        assertEquals("Your site, number 14", msg.text)
        assertEquals("📷 Photo · Your site, number 14", store.observeChat(chat.id).first()!!.lastText)
    }

    @Test fun aTextMessage_hasNoPhoto() = runTest {
        store.sendMessage(chat, "owner-1", "Priya", "Gates open at 2 PM.", now)
        assertEquals(null, store.observeMessages(chat.id).first().single().photoBase64)
        assertEquals("Gates open at 2 PM.", store.observeChat(chat.id).first()!!.lastText)
    }

    // ---- Reactions ----

    @Test fun reactions_offerSixEmoji() {
        assertEquals(listOf("👍", "❤️", "😂", "😮", "⛺", "🔥"), ChatReactions.CHOICES)
    }

    @Test fun toggle_addsReplacesAndTakesOff() {
        assertEquals("👍", ChatReactions.toggle(emptyMap(), "won", "👍"))
        assertEquals("🔥", ChatReactions.toggle(mapOf("won" to "👍"), "won", "🔥"))
        assertEquals(null, ChatReactions.toggle(mapOf("won" to "👍"), "won", "👍"))
        assertEquals("👍", ChatReactions.toggle(mapOf("owner-1" to "👍"), "won", "👍"))
    }

    @Test fun counts_inTheOrderShown_leavingOutUnused() {
        val counts = ChatReactions.counts(mapOf("a" to "🔥", "b" to "👍", "c" to "🔥"))
        assertEquals(listOf("👍" to 1, "🔥" to 2), counts)
        assertTrue(ChatReactions.counts(emptyMap()).isEmpty())
    }

    @Test fun reacting_isSaved_andCanBeTakenOff() = runTest {
        store.sendMessage(chat, "owner-1", "Priya", "Gates open at 2 PM.", now)
        val id = store.observeMessages(chat.id).first().single().id
        store.react(chat.id, id, "won", "👍")
        store.react(chat.id, id, "owner-1", "❤️")
        assertEquals(mapOf("won" to "👍", "owner-1" to "❤️"), store.observeMessages(chat.id).first().single().reactions)
        store.react(chat.id, id, "won", null)
        assertEquals(mapOf("owner-1" to "❤️"), store.observeMessages(chat.id).first().single().reactions)
    }

    @Test fun reacting_leavesTheChatUnreadCountsAlone() = runTest {
        store.sendMessage(chat, "owner-1", "Priya", "Hello", now)
        val before = store.observeChat(chat.id).first()
        store.react(chat.id, store.observeMessages(chat.id).first().single().id, "won", "😂")
        assertEquals(before, store.observeChat(chat.id).first())
    }
}
