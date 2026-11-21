/*
 * Validators.kt (Vaibhav)
 *
 * Checks for the sign-up, campsite and lottery forms.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

/** Form fields that can carry a validation error. */
enum class Field { NAME, EMAIL, PHONE, LOCATION, OCCUPANCY, PRICE, REG_START, REG_END, WINNERS, BOOK_START, BOOK_END, CAP, PASSWORD, AGE }

/** Validates the entrant profile form. */
object ProfileValidator {
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val phonePattern = Regex("^[+]?[0-9 ()-]{7,20}$")

    /**
     * @param name full name (required).
     * @param email email address (required).
     * @param phone phone number (optional).
     * @return errors by field; empty when the form is valid.
     */
    fun validate(name: String, email: String, phone: String): Map<Field, String> = buildMap {
        if (name.isBlank()) put(Field.NAME, "Enter your name")
        if (email.isBlank()) put(Field.EMAIL, "Email is required")
        else if (!emailPattern.matches(email.trim())) put(Field.EMAIL, "Enter a valid email")
        if (phone.isNotBlank() && !phonePattern.matches(phone.trim())) put(Field.PHONE, "Enter a valid phone number")
    }

    /** Figma S-SIGNUP's rule: 8 or more characters with at least one number. */
    fun isStrongPassword(password: String): Boolean = password.length >= 8 && password.any(Char::isDigit)

    /** Youngest age accepted at sign-up ("You must be 18+ to reserve a campsite"). */
    const val MIN_AGE = 18

    /**
     * Validates the Create Account form (Figma S-SIGNUP).
     *
     * @param password the new password, or null when the account already exists.
     * @param age age as typed.
     * @return errors by field; empty when the form is valid.
     */
    fun validateSignUp(name: String, email: String, password: String?, phone: String, age: String): Map<Field, String> = buildMap {
        putAll(validate(name, email, phone))
        if (password != null && !isStrongPassword(password)) put(Field.PASSWORD, "Use 8 or more characters with at least one number")
        val years = age.trim().toIntOrNull()
        if (years == null) put(Field.AGE, "Enter your age")
        else if (years < MIN_AGE || years > 120) put(Field.AGE, "You must be $MIN_AGE+ to use Events")
    }
}

/** Validates the campsite registration form. */
object CampsiteValidator {
    /** Largest occupancy accepted for one site. */
    const val MAX_OCCUPANCY = 50

    /**
     * @param name campsite name (required).
     * @param location place name (required).
     * @param maxOccupancy guests per site (1..[MAX_OCCUPANCY]).
     * @param pricePerNight price in dollars (0 or more).
     * @return errors by field; empty when the form is valid.
     */
    fun validate(name: String, location: String, maxOccupancy: Int, pricePerNight: Int): Map<Field, String> = buildMap {
        if (name.isBlank()) put(Field.NAME, "Give your campsite a name")
        if (location.isBlank()) put(Field.LOCATION, "Set a location")
        if (maxOccupancy !in 1..MAX_OCCUPANCY) put(Field.OCCUPANCY, "Occupancy must be 1–$MAX_OCCUPANCY")
        if (pricePerNight < 0) put(Field.PRICE, "Price can't be negative")
    }
}

/** Validates the new-lottery form. */
object LotteryValidator {
    /**
     * @param registrationStart when the waiting list opens (epoch ms).
     * @param registrationEnd when it closes and the draw runs (epoch ms).
     * @param winnerCount number of winners (at least 1).
     * @param now current time (epoch ms); the close time must be in the future.
     * @return errors by field; empty when the form is valid.
     */
    fun validate(registrationStart: Long?, registrationEnd: Long?, winnerCount: Int, now: Long): Map<Field, String> = buildMap {
        if (registrationStart == null) put(Field.REG_START, "Pick when the waitlist opens")
        if (registrationEnd == null) put(Field.REG_END, "Pick when the waitlist closes")
        if (registrationStart != null && registrationEnd != null && registrationEnd <= registrationStart) {
            put(Field.REG_END, "Closing must be after opening")
        }
        if (registrationEnd != null && registrationEnd <= now) put(Field.REG_END, "Closing must be in the future")
        if (winnerCount < 1) put(Field.WINNERS, "Draw at least 1 winner")
    }

    /**
     * Validates a lottery's booking period (US 02.04.04): the nights winners may book.
     *
     * @param registrationEndDay day number on which registration closes, or null if not chosen yet.
     * @param startDay first night guests can stay (day number).
     * @param endDay last check-out day (day number).
     * @return errors by field; empty when valid.
     */
    fun validateBooking(registrationEndDay: Long?, startDay: Long?, endDay: Long?): Map<Field, String> = buildMap {
        if (startDay == null) put(Field.BOOK_START, "Pick the stay dates winners can book")
        if (startDay != null && endDay == null) put(Field.BOOK_END, "Pick the last check-out day")
        if (startDay != null && endDay != null && endDay <= startDay) put(Field.BOOK_END, "Check-out must be after the first night")
        if (startDay != null && registrationEndDay != null && startDay < registrationEndDay) {
            put(Field.BOOK_START, "Stays must start after registration closes")
        }
    }

    /**
     * Validates the optional waitlist cap (US 02.04.07).
     * @param cap maximum entrants, or null for no limit.
     * @return an error message, or null when valid.
     */
    fun validateCap(cap: Int?): String? = if (cap != null && cap < 1) "The cap must be at least 1" else null
}
