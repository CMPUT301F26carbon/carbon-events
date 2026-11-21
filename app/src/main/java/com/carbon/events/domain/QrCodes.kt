/*
 * QrCodes.kt (Vaibhav)
 *
 * The text in a campsite's promo QR code, and reading it back.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

/** Builds and parses campsite QR payloads. */
object QrCodes {
    /** URI prefix every campsite QR code starts with. */
    const val PREFIX = "carbon-events://campsite/"

    private val idPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    /**
     * @param campsiteId the campsite's ID.
     * @return the text to encode in the campsite's QR code.
     * @throws IllegalArgumentException if the ID contains a path, whitespace, or exceeds 64 characters.
     */
    fun payloadFor(campsiteId: String): String {
        // Generation and scanning share one ID contract, so published QR codes can round-trip.
        require(idPattern.matches(campsiteId)) { "Invalid campsite ID for a promo QR code" }
        return PREFIX + campsiteId
    }

    /**
     * Extracts the campsite ID from scanned or typed text.
     *
     * Accepts a full payload ("carbon-events://campsite/abc") or, for manual entry, the
     * bare ID ("abc"). Anything else (a website link, random text) returns null.
     *
     * @param text the scanned or typed text.
     * @return the campsite ID, or null if [text] is not a campsite code.
     */
    fun parseCampsiteId(text: String): String? {
        val trimmed = text.trim()
        val id = if (trimmed.startsWith(PREFIX, ignoreCase = true)) trimmed.substring(PREFIX.length).trimEnd('/') else trimmed
        return id.takeIf { idPattern.matches(it) }
    }
}
