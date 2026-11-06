/*
 * LocationFieldTest.kt (Aditya)
 *
 * Stage 4 test: written before the stage 5 code. It fails (red) until stage 5 is merged.
 */
package com.carbon.events

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Filters location field shows each place with its province (Figma E-FILTERS-LOCATION). */
class LocationFieldTest {
    @Test fun cityAndProvinceCode_splitIntoPlaceAndFullProvince() {
        assertEquals("Banff" to "Alberta", placeParts("Banff, AB"))
        assertEquals("Tofino" to "British Columbia", placeParts("Tofino, BC"))
    }

    @Test fun lowercaseCode_stillRecognised() = assertEquals("Canmore" to "Alberta", placeParts("Canmore, ab"))

    @Test fun unknownRegion_isShownAsWritten() = assertEquals("Moab" to "Utah", placeParts("Moab, Utah"))

    @Test fun noComma_hasNoSubtitle() = assertEquals("Icefields Pkwy" to "", placeParts("Icefields Pkwy"))

    @Test fun longerPlaceName_keepsAllButTheRegion() =
        assertEquals("Two Jack Lake, Banff" to "Alberta", placeParts("Two Jack Lake, Banff, AB"))
}
