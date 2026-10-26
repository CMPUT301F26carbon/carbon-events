package com.carbon.events

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.carbon.events.ui.theme.EventsTheme

/*
 * MainActivity.kt
 *
 * Purpose: entry point of Events. Hosts the one NavHost that holds every route in the app:
 * 96 routes, one per screen in the team screen list (Launch 14, Entrant 37, Owner 29, Admin 16).
 * Every route starts as a Placeholder that lists its user stories, its Figma pages and buttons
 * to the screens it links to in Figma, so the whole app can be tapped through from day 1.
 *
 * How to use: build your screen in its own file (for example ui/entrant/ExploreScreen.kt), then
 * replace only the Placeholder(...) call inside your route with your function, e.g.
 *     composable(Routes.EXPLORE) { ExploreScreen(nav) }
 * Keep the route name, and only touch the lines of your own routes so merges stay clean.
 *
 * Kinds: composable(...) = full screen, dialog(...) = pop-up over the previous screen.
 * Sheets (ProfilePhoto, RejectReservation) use dialog(...) for now.
 *
 * Outstanding issues:
 *  - Routes take no arguments yet. Detail screens will add ids later
 *    (campsiteId, lotteryId, reservationId, userId, imageId).
 *  - VerifyEmail, Terms, TrustDevice, NotificationPrefs and SignIn will take a role argument.
 *  - Bottom navigation bars are not added yet; placeholders link to the tab screens directly.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EventsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}

/** Route names for every screen. Use these constants instead of typing route strings. */
object Routes {
    // Launch
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val CREATE_ACCOUNT = "createAccount"
    const val ENTRANT_SIGN_UP = "entrantSignUp"
    const val OWNER_SIGN_UP = "ownerSignUp"
    const val VERIFY_EMAIL = "verifyEmail"
    const val TERMS = "terms"
    const val TRUST_DEVICE = "trustDevice"
    const val NOTIFICATION_PREFS = "notificationPrefs"
    const val SIGN_IN = "signIn"
    const val FORGOT_PASSWORD = "forgotPassword"
    const val RESET_CODE = "resetCode"
    const val NEW_PASSWORD = "newPassword"
    const val CONFIRM_PASSWORD_CHANGE = "confirmPasswordChange"
    // Entrant
    const val EXPLORE = "explore"
    const val SEARCH = "search"
    const val FILTERS = "filters"
    const val LOCATION_PICKER = "locationPicker"
    const val FILTER_DATES = "filterDates"
    const val FILTERED_RESULTS = "filteredResults"
    const val CAMPSITE_DETAILS = "campsiteDetails"
    const val CAMPSITE_MAP = "campsiteMap"
    const val NO_ACTIVE_LOTTERY = "noActiveLottery"
    const val JOINED_WAITLIST = "joinedWaitlist"
    const val LEAVE_WAITLIST = "leaveWaitlist"
    const val SCAN_QR = "scanQr"
    const val MY_LOTTERIES = "myLotteries"
    const val LOTTERY_RESULTS = "lotteryResults"
    const val NOTIFICATIONS = "notifications"
    const val LOTTERY_WON = "lotteryWon"
    const val CHOOSE_CAMPSITE = "chooseCampsite"
    const val CHOOSE_DATES = "chooseDates"
    const val CAMPSITE_RULES = "campsiteRules"
    const val PAYMENT = "payment"
    const val RESERVATION_CONFIRMED = "reservationConfirmed"
    const val DECLINE_RESERVATION = "declineReservation"
    const val SPOT_DECLINED = "spotDeclined"
    const val MY_RESERVATIONS = "myReservations"
    const val PAST_RESERVATIONS = "pastReservations"
    const val CANCEL_RESERVATION = "cancelReservation"
    const val CANCELLATION_CONFIRMED = "cancellationConfirmed"
    const val RATE_CAMPSITE = "rateCampsite"
    const val ENTRANT_CHAT = "entrantChat"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "editProfile"
    const val PROFILE_PHOTO = "profilePhoto"
    const val POLICY = "policy"
    const val DELETE_PROFILE = "deleteProfile"
    const val SIGN_OUT = "signOut"
    const val HELP_SUPPORT = "helpSupport"
    const val HELP_SEARCH = "helpSearch"
    // Owner
    const val OWNER_HOME = "ownerHome"
    const val CAMPSITE_DETAILS_STEP = "campsiteDetailsStep"
    const val CAMPSITE_MAP_LOCATION = "campsiteMapLocation"
    const val CAMPSITE_PHOTO_STEP = "campsitePhotoStep"
    const val CAMPSITE_TAGS = "campsiteTags"
    const val CAMPSITE_AVAILABILITY = "campsiteAvailability"
    const val CAMPSITE_PUBLISHED = "campsitePublished"
    const val MANAGE_CAMPSITE = "manageCampsite"
    const val EDIT_CAMPSITE = "editCampsite"
    const val CAMPSITE_QR = "campsiteQr"
    const val BOOKED_DATES = "bookedDates"
    const val OWNER_LOTTERIES = "ownerLotteries"
    const val LOTTERY_CAMPSITES = "lotteryCampsites"
    const val REGISTRATION_PERIOD = "registrationPeriod"
    const val BOOKING_PERIOD = "bookingPeriod"
    const val WINNERS_AND_CAP = "winnersAndCap"
    const val LOTTERY_REVIEW = "lotteryReview"
    const val EDIT_LOTTERY = "editLottery"
    const val EDIT_LOTTERY_DATES = "editLotteryDates"
    const val WAITLIST = "waitlist"
    const val NOTIFY_WAITLIST = "notifyWaitlist"
    const val OWNER_BOOKINGS = "ownerBookings"
    const val PAST_BOOKINGS = "pastBookings"
    const val CANCELLED_BOOKINGS = "cancelledBookings"
    const val REJECT_RESERVATION = "rejectReservation"
    const val REJECT_CONFIRM = "rejectConfirm"
    const val OWNER_INBOX = "ownerInbox"
    const val OWNER_CHAT = "ownerChat"
    const val OWNER_PROFILE = "ownerProfile"
    // Admin
    const val ADMIN_CAMPSITES = "adminCampsites"
    const val ADMIN_CAMPSITE_DETAIL = "adminCampsiteDetail"
    const val REMOVE_CAMPSITE = "removeCampsite"
    const val ADMIN_PROFILES = "adminProfiles"
    const val ADMIN_ENTRANT_DETAIL = "adminEntrantDetail"
    const val REMOVE_PROFILE = "removeProfile"
    const val ADMIN_OWNER_DETAIL = "adminOwnerDetail"
    const val REMOVE_OWNER = "removeOwner"
    const val ADMIN_IMAGES = "adminImages"
    const val IMAGE_FOLDER = "imageFolder"
    const val IMAGE_DETAIL = "imageDetail"
    const val REMOVE_IMAGE = "removeImage"
    const val NOTIFICATION_LOGS = "notificationLogs"
    const val LOG_DATE_RANGE = "logDateRange"
    const val MESSAGE_LOGS = "messageLogs"
    const val ADMIN_ACCOUNT = "adminAccount"
}

/**
 * Holds every route of the app. Starts on Splash.
 *
 * @param nav the controller used by all screens to move between routes
 */
@Composable
fun AppNavHost(nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Routes.SPLASH) {

        // ---------------- Launch (14) ----------------
        // Splash · US 01.09.01 · Figma: S-SPLASH, S-SPLASH-RETURNING, S-SPLASH-RETURNING-OWNER
        composable(Routes.SPLASH) {
            Placeholder(nav, "Splash", "US 01.09.01", "S-SPLASH, S-SPLASH-RETURNING, S-SPLASH-RETURNING-OWNER", dialog = false,
                links = listOf(Routes.WELCOME to "Welcome", Routes.EXPLORE to "Explore", Routes.OWNER_HOME to "Owner Home"))
        }
        // Welcome · US 01.09.01 · Figma: S-WELCOME
        composable(Routes.WELCOME) {
            Placeholder(nav, "Welcome", "US 01.09.01", "S-WELCOME", dialog = false,
                links = listOf(Routes.SIGN_IN to "Sign In", Routes.CREATE_ACCOUNT to "Create Account"))
        }
        // CreateAccount · US 01.09.01 · Figma: S-ACCOUNT
        composable(Routes.CREATE_ACCOUNT) {
            Placeholder(nav, "Create Account", "US 01.09.01", "S-ACCOUNT", dialog = false,
                links = listOf(Routes.ENTRANT_SIGN_UP to "Entrant Sign Up", Routes.TERMS to "Terms", Routes.SIGN_IN to "Sign In"))
        }
        // EntrantSignUp · US 01.07.03 · Figma: S-SIGNUP-EMPTY ... S-SIGNUP (11 pages)
        composable(Routes.ENTRANT_SIGN_UP) {
            Placeholder(nav, "Entrant Sign Up", "US 01.07.03", "S-SIGNUP-EMPTY ... S-SIGNUP (11 pages)", dialog = false,
                links = listOf(Routes.OWNER_SIGN_UP to "Owner Sign Up", Routes.VERIFY_EMAIL to "Verify Email", Routes.CREATE_ACCOUNT to "Create Account"))
        }
        // OwnerSignUp · US 02.01.01 · Figma: S-SIGNUP-OWNER-EMPTY ... S-SIGNUP-OWNER (11 pages)
        composable(Routes.OWNER_SIGN_UP) {
            Placeholder(nav, "Owner Sign Up", "US 02.01.01", "S-SIGNUP-OWNER-EMPTY ... S-SIGNUP-OWNER (11 pages)", dialog = false,
                links = listOf(Routes.ENTRANT_SIGN_UP to "Entrant Sign Up", Routes.VERIFY_EMAIL to "Verify Email", Routes.CREATE_ACCOUNT to "Create Account"))
        }
        // VerifyEmail · US 01.07.03 · Figma: S-VERIFY-*, S-VERIFY-OWNER-* (6 pages)
        composable(Routes.VERIFY_EMAIL) {
            Placeholder(nav, "Verify Email", "US 01.07.03", "S-VERIFY-*, S-VERIFY-OWNER-* (6 pages)", dialog = false,
                links = listOf(Routes.ENTRANT_SIGN_UP to "Entrant Sign Up", Routes.TERMS to "Terms", Routes.OWNER_SIGN_UP to "Owner Sign Up"))
        }
        // Terms · US 01.11.01 · Figma: S-TERMS, S-TERMS-AGREED, S-TERMS-OWNER, S-TERMS-OWNER-AGREED
        composable(Routes.TERMS) {
            Placeholder(nav, "Terms", "US 01.11.01", "S-TERMS, S-TERMS-AGREED, S-TERMS-OWNER, S-TERMS-OWNER-AGREED", dialog = false,
                links = listOf(Routes.WELCOME to "Welcome", Routes.SIGN_IN to "Sign In", Routes.VERIFY_EMAIL to "Verify Email", Routes.TRUST_DEVICE to "Trust Device"))
        }
        // TrustDevice · US 01.09.01 · Figma: S-TRUST, S-TRUST-OWNER, S-TRUST-SIGNIN, -SIGNIN-OWNER, -SIGNIN-ADMIN
        composable(Routes.TRUST_DEVICE) {
            Placeholder(nav, "Trust Device", "US 01.09.01", "S-TRUST, S-TRUST-OWNER, S-TRUST-SIGNIN, -SIGNIN-OWNER, -SIGNIN-ADMIN", dialog = false,
                links = listOf(Routes.NOTIFICATION_PREFS to "Notification Prefs", Routes.TERMS to "Terms", Routes.EXPLORE to "Explore", Routes.SIGN_IN to "Sign In", Routes.OWNER_HOME to "Owner Home", Routes.ADMIN_CAMPSITES to "Admin Campsites"))
        }
        // NotificationPrefs · US 01.10.01 · Figma: S-NOTIFS-* (7 pages), S-NOTIFS-OWNER-* (6 pages)
        composable(Routes.NOTIFICATION_PREFS) {
            Placeholder(nav, "Notification Prefs", "US 01.10.01", "S-NOTIFS-* (7 pages), S-NOTIFS-OWNER-* (6 pages)", dialog = false,
                links = listOf(Routes.TRUST_DEVICE to "Trust Device", Routes.EXPLORE to "Explore", Routes.OWNER_HOME to "Owner Home"))
        }
        // SignIn · US 01.09.01 · Figma: S-START-EMPTY ... S-START, -NOREMEMBER, -WRONG-PW, -PW-UPDATED, S-START-OWNER-*, S-START-ADMIN-* (16 pages)
        composable(Routes.SIGN_IN) {
            Placeholder(nav, "Sign In", "US 01.09.01", "S-START-EMPTY ... S-START, -NOREMEMBER, -WRONG-PW, -PW-UPDATED, S-START-OWNER-*, S-START-ADMIN-* (16 pages)", dialog = false,
                links = listOf(Routes.FORGOT_PASSWORD to "Forgot Password", Routes.TRUST_DEVICE to "Trust Device", Routes.WELCOME to "Welcome"))
        }
        // ForgotPassword · US 01.09.01 · Figma: S-FORGOT-EMPTY, S-FORGOT-EMAIL-KB, S-FORGOT
        composable(Routes.FORGOT_PASSWORD) {
            Placeholder(nav, "Forgot Password", "US 01.09.01", "S-FORGOT-EMPTY, S-FORGOT-EMAIL-KB, S-FORGOT", dialog = false,
                links = listOf(Routes.RESET_CODE to "Reset Code", Routes.SIGN_IN to "Sign In"))
        }
        // ResetCode · US 01.09.01 · Figma: S-FORGOT-CODE-* (6 pages)
        composable(Routes.RESET_CODE) {
            Placeholder(nav, "Reset Code", "US 01.09.01", "S-FORGOT-CODE-* (6 pages)", dialog = false,
                links = listOf(Routes.SIGN_IN to "Sign In", Routes.NEW_PASSWORD to "New Password"))
        }
        // NewPassword · US 01.09.01 · Figma: S-NEW-PW-* (6 pages)
        composable(Routes.NEW_PASSWORD) {
            Placeholder(nav, "New Password", "US 01.09.01", "S-NEW-PW-* (6 pages)", dialog = false,
                links = listOf(Routes.CONFIRM_PASSWORD_CHANGE to "Confirm Password Change", Routes.RESET_CODE to "Reset Code"))
        }
        // ConfirmPasswordChange · dialog · US 01.09.01 · Figma: S-PW-CONFIRM
        dialog(Routes.CONFIRM_PASSWORD_CHANGE) {
            Placeholder(nav, "Confirm Password Change", "US 01.09.01", "S-PW-CONFIRM", dialog = true,
                links = listOf(Routes.SIGN_IN to "Sign In", Routes.NEW_PASSWORD to "New Password"))
        }

        // ---------------- Entrant (37) ----------------
        // Explore · US 01.01.01, 01.01.06 · Figma: E-HOME, E-HOME-SWIPED, E-HOME-SWIPED-3
        composable(Routes.EXPLORE) {
            Placeholder(nav, "Explore", "US 01.01.01, 01.01.06", "E-HOME, E-HOME-SWIPED, E-HOME-SWIPED-3", dialog = false,
                links = listOf(Routes.FILTERED_RESULTS to "Filtered Results", Routes.SEARCH to "Search", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile", Routes.NOTIFICATIONS to "Notifications", Routes.CAMPSITE_DETAILS to "Campsite Details", Routes.FILTERS to "Filters"))
        }
        // Search · US 01.01.01 · Figma: E-SEARCH-KB, E-SEARCH
        composable(Routes.SEARCH) {
            Placeholder(nav, "Search", "US 01.01.01", "E-SEARCH-KB, E-SEARCH", dialog = false,
                links = listOf(Routes.FILTERS to "Filters", Routes.CAMPSITE_DETAILS to "Campsite Details", Routes.NO_ACTIVE_LOTTERY to "No Active Lottery", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile", Routes.EXPLORE to "Explore"))
        }
        // Filters · US 01.01.02 · Figma: E-FILTERS-START, E-FILTERS-STEP-*, E-FILTERS, E-FILTERS-RESET
        composable(Routes.FILTERS) {
            Placeholder(nav, "Filters", "US 01.01.02", "E-FILTERS-START, E-FILTERS-STEP-*, E-FILTERS, E-FILTERS-RESET", dialog = false,
                links = listOf(Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile", Routes.FILTERED_RESULTS to "Filtered Results", Routes.LOCATION_PICKER to "Location Picker", Routes.FILTER_DATES to "Filter Dates", Routes.SEARCH to "Search"))
        }
        // LocationPicker · US 01.01.02 · Figma: E-FILTERS-LOCATION, E-LOCATION-PERM
        composable(Routes.LOCATION_PICKER) {
            Placeholder(nav, "Location Picker", "US 01.01.02", "E-FILTERS-LOCATION, E-LOCATION-PERM", dialog = false,
                links = listOf(Routes.FILTERS to "Filters", Routes.FILTER_DATES to "Filter Dates", Routes.FILTERED_RESULTS to "Filtered Results", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile"))
        }
        // FilterDates · dialog · US 01.01.02 · Figma: E-FILTERS-DATES-OPEN, E-FILTERS-DATES-START, E-FILTERS-DATES
        dialog(Routes.FILTER_DATES) {
            Placeholder(nav, "Filter Dates", "US 01.01.02", "E-FILTERS-DATES-OPEN, E-FILTERS-DATES-START, E-FILTERS-DATES", dialog = true,
                links = listOf(Routes.FILTERS to "Filters", Routes.LOCATION_PICKER to "Location Picker", Routes.FILTERED_RESULTS to "Filtered Results", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile"))
        }
        // FilteredResults · US 01.01.03, 01.01.04 · Figma: E-SEARCH-FILTERED, E-SEARCH-FILTERED-SCROLLED
        composable(Routes.FILTERED_RESULTS) {
            Placeholder(nav, "Filtered Results", "US 01.01.03, 01.01.04", "E-SEARCH-FILTERED, E-SEARCH-FILTERED-SCROLLED", dialog = false,
                links = listOf(Routes.FILTERS to "Filters", Routes.CAMPSITE_DETAILS to "Campsite Details", Routes.NO_ACTIVE_LOTTERY to "No Active Lottery", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile", Routes.SEARCH to "Search"))
        }
        // CampsiteDetails · US 01.01.05, 01.01.06 · Figma: E-DETAILS, E-DETAILS-LEFT
        composable(Routes.CAMPSITE_DETAILS) {
            Placeholder(nav, "Campsite Details", "US 01.01.05, 01.01.06", "E-DETAILS, E-DETAILS-LEFT", dialog = false,
                links = listOf(Routes.EXPLORE to "Explore", Routes.RATE_CAMPSITE to "Rate Campsite", Routes.JOINED_WAITLIST to "Joined Waitlist", Routes.CAMPSITE_MAP to "Campsite Map"))
        }
        // CampsiteMap · US 01.01.05 · Figma: E-MAP
        composable(Routes.CAMPSITE_MAP) {
            Placeholder(nav, "Campsite Map", "US 01.01.05", "E-MAP", dialog = false,
                links = listOf(Routes.CAMPSITE_DETAILS to "Campsite Details"))
        }
        // NoActiveLottery · US 01.01.06 · Figma: E-NO-LOTTERY, E-NO-LOTTERY-NOTIFY
        composable(Routes.NO_ACTIVE_LOTTERY) {
            Placeholder(nav, "No Active Lottery", "US 01.01.06", "E-NO-LOTTERY, E-NO-LOTTERY-NOTIFY", dialog = false,
                links = listOf(Routes.SEARCH to "Search", Routes.CAMPSITE_DETAILS to "Campsite Details", Routes.FILTERED_RESULTS to "Filtered Results"))
        }
        // JoinedWaitlist · US 01.02.01 · Figma: E-DETAILS-JOINED
        composable(Routes.JOINED_WAITLIST) {
            Placeholder(nav, "Joined Waitlist", "US 01.02.01", "E-DETAILS-JOINED", dialog = false,
                links = listOf(Routes.EXPLORE to "Explore", Routes.LEAVE_WAITLIST to "Leave Waitlist", Routes.MY_LOTTERIES to "My Lotteries"))
        }
        // LeaveWaitlist · dialog · US 01.02.02 · Figma: E-LEAVE-WAITLIST
        dialog(Routes.LEAVE_WAITLIST) {
            Placeholder(nav, "Leave Waitlist", "US 01.02.02", "E-LEAVE-WAITLIST", dialog = true,
                links = listOf(Routes.JOINED_WAITLIST to "Joined Waitlist", Routes.CAMPSITE_DETAILS to "Campsite Details"))
        }
        // ScanQr · US 01.12.01 · Figma: E-SCAN-PERM, E-SCAN-SEARCHING, E-SCAN
        composable(Routes.SCAN_QR) {
            Placeholder(nav, "Scan QR", "US 01.12.01", "E-SCAN-PERM, E-SCAN-SEARCHING, E-SCAN", dialog = false,
                links = listOf(Routes.EXPLORE to "Explore", Routes.CAMPSITE_DETAILS to "Campsite Details"))
        }
        // MyLotteries · US 01.03.01, 01.03.02 · Figma: E-LOTTERIES
        composable(Routes.MY_LOTTERIES) {
            Placeholder(nav, "My Lotteries", "US 01.03.01, 01.03.02", "E-LOTTERIES", dialog = false,
                links = listOf(Routes.EXPLORE to "Explore", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile", Routes.NOTIFICATIONS to "Notifications", Routes.LOTTERY_RESULTS to "Lottery Results"))
        }
        // LotteryResults · US 02.05.01, 01.03.02 · Figma: E-LOTTERIES-RESULTS
        composable(Routes.LOTTERY_RESULTS) {
            Placeholder(nav, "Lottery Results", "US 02.05.01, 01.03.02", "E-LOTTERIES-RESULTS", dialog = false,
                links = listOf(Routes.EXPLORE to "Explore", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile", Routes.LOTTERY_WON to "Lottery Won", Routes.NOTIFICATIONS to "Notifications", Routes.MY_LOTTERIES to "My Lotteries", Routes.ENTRANT_CHAT to "Entrant Chat"))
        }
        // Notifications · US 01.03.01, 01.03.02, 01.05.01 · Figma: E-NOTIFS, E-NOTIFS-CHAT
        composable(Routes.NOTIFICATIONS) {
            Placeholder(nav, "Notifications", "US 01.03.01, 01.03.02, 01.05.01", "E-NOTIFS, E-NOTIFS-CHAT", dialog = false,
                links = listOf(Routes.EXPLORE to "Explore", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile", Routes.LOTTERY_WON to "Lottery Won", Routes.ENTRANT_CHAT to "Entrant Chat"))
        }
        // LotteryWon · US 01.03.01 · Figma: E-WON
        composable(Routes.LOTTERY_WON) {
            Placeholder(nav, "Lottery Won", "US 01.03.01", "E-WON", dialog = false,
                links = listOf(Routes.MY_LOTTERIES to "My Lotteries", Routes.DECLINE_RESERVATION to "Decline Reservation", Routes.CAMPSITE_RULES to "Campsite Rules", Routes.CHOOSE_CAMPSITE to "Choose Campsite"))
        }
        // ChooseCampsite · US 01.04.02 · Figma: E-WON-PICK
        composable(Routes.CHOOSE_CAMPSITE) {
            Placeholder(nav, "Choose Campsite", "US 01.04.02", "E-WON-PICK", dialog = false,
                links = listOf(Routes.CHOOSE_DATES to "Choose Dates", Routes.MY_LOTTERIES to "My Lotteries", Routes.DECLINE_RESERVATION to "Decline Reservation", Routes.CAMPSITE_RULES to "Campsite Rules"))
        }
        // ChooseDates · US 01.04.01 · Figma: E-WON-SITE7-NODATES, -CHECKIN, -UNREAD, E-WON-SITE7
        composable(Routes.CHOOSE_DATES) {
            Placeholder(nav, "Choose Dates", "US 01.04.01", "E-WON-SITE7-NODATES, -CHECKIN, -UNREAD, E-WON-SITE7", dialog = false,
                links = listOf(Routes.MY_LOTTERIES to "My Lotteries", Routes.DECLINE_RESERVATION to "Decline Reservation", Routes.PAYMENT to "Payment", Routes.CAMPSITE_RULES to "Campsite Rules", Routes.CHOOSE_CAMPSITE to "Choose Campsite"))
        }
        // CampsiteRules · US 02.11.01 · Figma: E-RULES, E-RULES-AGREED
        composable(Routes.CAMPSITE_RULES) {
            Placeholder(nav, "Campsite Rules", "US 02.11.01", "E-RULES, E-RULES-AGREED", dialog = false,
                links = listOf(Routes.CHOOSE_DATES to "Choose Dates", Routes.PAYMENT to "Payment"))
        }
        // Payment · US 01.05.01 · Figma: E-PAYMENT
        composable(Routes.PAYMENT) {
            Placeholder(nav, "Payment", "US 01.05.01", "E-PAYMENT", dialog = false,
                links = listOf(Routes.CAMPSITE_RULES to "Campsite Rules", Routes.RESERVATION_CONFIRMED to "Reservation Confirmed"))
        }
        // ReservationConfirmed · US 01.05.01, 01.05.02 · Figma: E-CONFIRMED
        composable(Routes.RESERVATION_CONFIRMED) {
            Placeholder(nav, "Reservation Confirmed", "US 01.05.01, 01.05.02", "E-CONFIRMED", dialog = false,
                links = listOf(Routes.MY_RESERVATIONS to "My Reservations"))
        }
        // DeclineReservation · US 01.04.03 · Figma: E-DECLINE
        composable(Routes.DECLINE_RESERVATION) {
            Placeholder(nav, "Decline Reservation", "US 01.04.03", "E-DECLINE", dialog = false,
                links = listOf(Routes.CHOOSE_DATES to "Choose Dates", Routes.SPOT_DECLINED to "Spot Declined"))
        }
        // SpotDeclined · US 01.04.03 · Figma: E-DECLINED
        composable(Routes.SPOT_DECLINED) {
            Placeholder(nav, "Spot Declined", "US 01.04.03", "E-DECLINED", dialog = false,
                links = listOf(Routes.MY_LOTTERIES to "My Lotteries"))
        }
        // MyReservations · US 01.06.01, 01.05.02 · Figma: E-BOOKINGS
        composable(Routes.MY_RESERVATIONS) {
            Placeholder(nav, "My Reservations", "US 01.06.01, 01.05.02", "E-BOOKINGS", dialog = false,
                links = listOf(Routes.EXPLORE to "Explore", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.PROFILE to "Profile", Routes.CANCEL_RESERVATION to "Cancel Reservation", Routes.PAST_RESERVATIONS to "Past Reservations", Routes.ENTRANT_CHAT to "Entrant Chat"))
        }
        // PastReservations · US 01.06.02 · Figma: E-PAST
        composable(Routes.PAST_RESERVATIONS) {
            Placeholder(nav, "Past Reservations", "US 01.06.02", "E-PAST", dialog = false,
                links = listOf(Routes.EXPLORE to "Explore", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.PROFILE to "Profile", Routes.MY_RESERVATIONS to "My Reservations", Routes.RATE_CAMPSITE to "Rate Campsite"))
        }
        // CancelReservation · dialog · US 01.07.01 · Figma: E-CANCEL
        dialog(Routes.CANCEL_RESERVATION) {
            Placeholder(nav, "Cancel Reservation", "US 01.07.01", "E-CANCEL", dialog = true,
                links = listOf(Routes.EXPLORE to "Explore", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.PROFILE to "Profile", Routes.MY_RESERVATIONS to "My Reservations", Routes.CANCELLATION_CONFIRMED to "Cancellation Confirmed"))
        }
        // CancellationConfirmed · US 01.07.02 · Figma: E-CANCELLED
        composable(Routes.CANCELLATION_CONFIRMED) {
            Placeholder(nav, "Cancellation Confirmed", "US 01.07.02", "E-CANCELLED", dialog = false,
                links = listOf(Routes.PAST_RESERVATIONS to "Past Reservations"))
        }
        // RateCampsite · US 01.13.01 · Figma: E-RATE
        composable(Routes.RATE_CAMPSITE) {
            Placeholder(nav, "Rate Campsite", "US 01.13.01", "E-RATE", dialog = false,
                links = listOf(Routes.CAMPSITE_DETAILS to "Campsite Details"))
        }
        // EntrantChat · US 02.12.01 · Figma: E-CHAT
        composable(Routes.ENTRANT_CHAT) {
            Placeholder(nav, "Entrant Chat", "US 02.12.01", "E-CHAT", dialog = false,
                links = listOf())
        }
        // Profile · US 01.07.03, 01.10.01 · Figma: E-PROFILE, E-PROFILE-SAVED, E-PROFILE-NOTIFS-OFF
        composable(Routes.PROFILE) {
            Placeholder(nav, "Profile", "US 01.07.03, 01.10.01", "E-PROFILE, E-PROFILE-SAVED, E-PROFILE-NOTIFS-OFF", dialog = false,
                links = listOf(Routes.EDIT_PROFILE to "Edit Profile", Routes.HELP_SUPPORT to "Help Support", Routes.POLICY to "Policy", Routes.SIGN_OUT to "Sign Out", Routes.DELETE_PROFILE to "Delete Profile", Routes.EXPLORE to "Explore", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations"))
        }
        // EditProfile · US 01.07.03 · Figma: E-PROFILE-EDIT-* (5 pages)
        composable(Routes.EDIT_PROFILE) {
            Placeholder(nav, "Edit Profile", "US 01.07.03", "E-PROFILE-EDIT-* (5 pages)", dialog = false,
                links = listOf(Routes.PROFILE_PHOTO to "Profile Photo", Routes.PROFILE to "Profile"))
        }
        // ProfilePhoto · sheet (dialog for now) · US 01.07.03 · Figma: E-PHOTO, E-PHOTO-PERM, E-CAMERA-PERM
        dialog(Routes.PROFILE_PHOTO) {
            Placeholder(nav, "Profile Photo", "US 01.07.03", "E-PHOTO, E-PHOTO-PERM, E-CAMERA-PERM", dialog = true,
                links = listOf(Routes.EDIT_PROFILE to "Edit Profile"))
        }
        // Policy · US 01.11.01 · Figma: E-POLICY, E-POLICY-GUIDELINES
        composable(Routes.POLICY) {
            Placeholder(nav, "Policy", "US 01.11.01", "E-POLICY, E-POLICY-GUIDELINES", dialog = false,
                links = listOf(Routes.EXPLORE to "Explore", Routes.MY_LOTTERIES to "My Lotteries", Routes.SCAN_QR to "Scan QR", Routes.MY_RESERVATIONS to "My Reservations", Routes.PROFILE to "Profile"))
        }
        // DeleteProfile · dialog · US 01.08.01 · Figma: E-DELETE, E-DELETE-KB, E-DELETE-TYPED
        dialog(Routes.DELETE_PROFILE) {
            Placeholder(nav, "Delete Profile", "US 01.08.01", "E-DELETE, E-DELETE-KB, E-DELETE-TYPED", dialog = true,
                links = listOf(Routes.PROFILE to "Profile", Routes.WELCOME to "Welcome"))
        }
        // SignOut · dialog · US 01.09.01 · Figma: E-SIGNOUT
        dialog(Routes.SIGN_OUT) {
            Placeholder(nav, "Sign Out", "US 01.09.01", "E-SIGNOUT", dialog = true,
                links = listOf(Routes.PROFILE to "Profile", Routes.SIGN_IN to "Sign In"))
        }
        // HelpSupport · US 01.11.01 · Figma: E-HELP-CLOSED, E-HELP, E-HELP-BOOKINGS, E-HELP-REFUNDS
        composable(Routes.HELP_SUPPORT) {
            Placeholder(nav, "Help Support", "US 01.11.01", "E-HELP-CLOSED, E-HELP, E-HELP-BOOKINGS, E-HELP-REFUNDS", dialog = false,
                links = listOf(Routes.HELP_SEARCH to "Help Search"))
        }
        // HelpSearch · US 01.11.01 · Figma: E-HELP-SEARCH-KB, E-HELP-SEARCH, E-HELP-SEARCH-RESULTS
        composable(Routes.HELP_SEARCH) {
            Placeholder(nav, "Help Search", "US 01.11.01", "E-HELP-SEARCH-KB, E-HELP-SEARCH, E-HELP-SEARCH-RESULTS", dialog = false,
                links = listOf())
        }

        // ---------------- Owner (29) ----------------
        // OwnerHome · US 02.03.01 · Figma: O-HOME
        composable(Routes.OWNER_HOME) {
            Placeholder(nav, "Owner Home", "US 02.03.01", "O-HOME", dialog = false,
                links = listOf(Routes.MANAGE_CAMPSITE to "Manage Campsite", Routes.CAMPSITE_DETAILS_STEP to "Campsite Details Step", Routes.OWNER_INBOX to "Owner Inbox", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.OWNER_BOOKINGS to "Owner Bookings", Routes.OWNER_PROFILE to "Owner Profile"))
        }
        // CampsiteDetailsStep · US 02.01.01, 02.01.02 · Figma: O-EDIT-EMPTY ... O-EDIT (7 pages)
        composable(Routes.CAMPSITE_DETAILS_STEP) {
            Placeholder(nav, "Campsite Details Step", "US 02.01.01, 02.01.02", "O-EDIT-EMPTY ... O-EDIT (7 pages)", dialog = false,
                links = listOf(Routes.OWNER_HOME to "Owner Home", Routes.CAMPSITE_AVAILABILITY to "Campsite Availability", Routes.CAMPSITE_PHOTO_STEP to "Campsite Photo Step", Routes.CAMPSITE_MAP_LOCATION to "Campsite Map Location", Routes.CAMPSITE_TAGS to "Campsite Tags"))
        }
        // CampsiteMapLocation · US 02.01.02 · Figma: O-MAP, O-EDIT-LOCATION
        composable(Routes.CAMPSITE_MAP_LOCATION) {
            Placeholder(nav, "Campsite Map Location", "US 02.01.02", "O-MAP, O-EDIT-LOCATION", dialog = false,
                links = listOf(Routes.CAMPSITE_DETAILS_STEP to "Campsite Details Step", Routes.OWNER_HOME to "Owner Home", Routes.CAMPSITE_PHOTO_STEP to "Campsite Photo Step"))
        }
        // CampsitePhotoStep · US 02.01.02 · Figma: O-PHOTO-PERM
        composable(Routes.CAMPSITE_PHOTO_STEP) {
            Placeholder(nav, "Campsite Photo Step", "US 02.01.02", "O-PHOTO-PERM", dialog = false,
                links = listOf(Routes.CAMPSITE_DETAILS_STEP to "Campsite Details Step"))
        }
        // CampsiteTags · US 02.01.02 · Figma: O-TAGS-* (10 pages), O-EDIT-TAGGED
        composable(Routes.CAMPSITE_TAGS) {
            Placeholder(nav, "Campsite Tags", "US 02.01.02", "O-TAGS-* (10 pages), O-EDIT-TAGGED", dialog = false,
                links = listOf(Routes.CAMPSITE_DETAILS_STEP to "Campsite Details Step", Routes.OWNER_HOME to "Owner Home", Routes.CAMPSITE_AVAILABILITY to "Campsite Availability", Routes.CAMPSITE_PHOTO_STEP to "Campsite Photo Step", Routes.CAMPSITE_MAP_LOCATION to "Campsite Map Location"))
        }
        // CampsiteAvailability · US 02.01.02 · Figma: O-AVAILABILITY-* (6 pages)
        composable(Routes.CAMPSITE_AVAILABILITY) {
            Placeholder(nav, "Campsite Availability", "US 02.01.02", "O-AVAILABILITY-* (6 pages)", dialog = false,
                links = listOf(Routes.CAMPSITE_DETAILS_STEP to "Campsite Details Step", Routes.CAMPSITE_PUBLISHED to "Campsite Published"))
        }
        // CampsitePublished · US 02.01.01 · Figma: O-PUBLISHED
        composable(Routes.CAMPSITE_PUBLISHED) {
            Placeholder(nav, "Campsite Published", "US 02.01.01", "O-PUBLISHED", dialog = false,
                links = listOf(Routes.LOTTERY_CAMPSITES to "Lottery Campsites", Routes.OWNER_HOME to "Owner Home"))
        }
        // ManageCampsite · US 02.01.04 · Figma: O-MANAGE
        composable(Routes.MANAGE_CAMPSITE) {
            Placeholder(nav, "Manage Campsite", "US 02.01.04", "O-MANAGE", dialog = false,
                links = listOf(Routes.OWNER_HOME to "Owner Home", Routes.CAMPSITE_DETAILS_STEP to "Campsite Details Step", Routes.EDIT_LOTTERY to "Edit Lottery", Routes.WAITLIST to "Waitlist", Routes.OWNER_BOOKINGS to "Owner Bookings", Routes.NOTIFY_WAITLIST to "Notify Waitlist", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.OWNER_INBOX to "Owner Inbox", Routes.OWNER_PROFILE to "Owner Profile", Routes.EDIT_CAMPSITE to "Edit Campsite", Routes.CAMPSITE_QR to "Campsite QR", Routes.BOOKED_DATES to "Booked Dates"))
        }
        // EditCampsite · US 02.01.03 · Figma: O-EDIT (filled, edit mode)
        composable(Routes.EDIT_CAMPSITE) {
            Placeholder(nav, "Edit Campsite", "US 02.01.03", "O-EDIT (filled, edit mode)", dialog = false,
                links = listOf(Routes.MANAGE_CAMPSITE to "Manage Campsite"))
        }
        // CampsiteQr · US 02.02.01 · Figma: QR on O-MANAGE, full size
        composable(Routes.CAMPSITE_QR) {
            Placeholder(nav, "Campsite QR", "US 02.02.01", "QR on O-MANAGE, full size", dialog = false,
                links = listOf(Routes.MANAGE_CAMPSITE to "Manage Campsite"))
        }
        // BookedDates · US 02.06.01 · Figma: O-BOOKINGS-CAMPSITE
        composable(Routes.BOOKED_DATES) {
            Placeholder(nav, "Booked Dates", "US 02.06.01", "O-BOOKINGS-CAMPSITE", dialog = false,
                links = listOf(Routes.OWNER_BOOKINGS to "Owner Bookings"))
        }
        // OwnerLotteries · US 02.04.06 · Figma: O-LOTTERIES, O-LOTTERIES-PUBLISHED
        composable(Routes.OWNER_LOTTERIES) {
            Placeholder(nav, "Owner Lotteries", "US 02.04.06", "O-LOTTERIES, O-LOTTERIES-PUBLISHED", dialog = false,
                links = listOf(Routes.WAITLIST to "Waitlist", Routes.LOTTERY_CAMPSITES to "Lottery Campsites", Routes.OWNER_HOME to "Owner Home", Routes.OWNER_BOOKINGS to "Owner Bookings", Routes.OWNER_INBOX to "Owner Inbox", Routes.OWNER_PROFILE to "Owner Profile"))
        }
        // LotteryCampsites · US 02.04.01, 02.04.02 · Figma: O-LOTTERY-NEW-EMPTY, O-LOTTERY-NEW-CAMPSITES
        composable(Routes.LOTTERY_CAMPSITES) {
            Placeholder(nav, "Lottery Campsites", "US 02.04.01, 02.04.02", "O-LOTTERY-NEW-EMPTY, O-LOTTERY-NEW-CAMPSITES", dialog = false,
                links = listOf(Routes.CAMPSITE_PUBLISHED to "Campsite Published", Routes.REGISTRATION_PERIOD to "Registration Period"))
        }
        // RegistrationPeriod · US 02.04.03 · Figma: O-LOTTERY-NEW-REG-* (4 pages)
        composable(Routes.REGISTRATION_PERIOD) {
            Placeholder(nav, "Registration Period", "US 02.04.03", "O-LOTTERY-NEW-REG-* (4 pages)", dialog = false,
                links = listOf(Routes.CAMPSITE_PUBLISHED to "Campsite Published", Routes.BOOKING_PERIOD to "Booking Period", Routes.LOTTERY_CAMPSITES to "Lottery Campsites"))
        }
        // BookingPeriod · US 02.04.04 · Figma: O-LOTTERY-NEW-BOOK-* (4 pages)
        composable(Routes.BOOKING_PERIOD) {
            Placeholder(nav, "Booking Period", "US 02.04.04", "O-LOTTERY-NEW-BOOK-* (4 pages)", dialog = false,
                links = listOf(Routes.CAMPSITE_PUBLISHED to "Campsite Published", Routes.WINNERS_AND_CAP to "Winners And Cap", Routes.REGISTRATION_PERIOD to "Registration Period"))
        }
        // WinnersAndCap · US 02.04.05, 02.04.07 · Figma: O-LOTTERY-NEW-WINNERS-KB, -WINNERS, -CAP-KB
        composable(Routes.WINNERS_AND_CAP) {
            Placeholder(nav, "Winners And Cap", "US 02.04.05, 02.04.07", "O-LOTTERY-NEW-WINNERS-KB, -WINNERS, -CAP-KB", dialog = false,
                links = listOf(Routes.CAMPSITE_PUBLISHED to "Campsite Published", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.LOTTERY_REVIEW to "Lottery Review"))
        }
        // LotteryReview · US 02.04.01 · Figma: O-LOTTERY-NEW
        composable(Routes.LOTTERY_REVIEW) {
            Placeholder(nav, "Lottery Review", "US 02.04.01", "O-LOTTERY-NEW", dialog = false,
                links = listOf(Routes.CAMPSITE_PUBLISHED to "Campsite Published", Routes.OWNER_LOTTERIES to "Owner Lotteries"))
        }
        // EditLottery · US 02.04.06 · Figma: O-LOTTERY-EDIT
        composable(Routes.EDIT_LOTTERY) {
            Placeholder(nav, "Edit Lottery", "US 02.04.06", "O-LOTTERY-EDIT", dialog = false,
                links = listOf(Routes.MANAGE_CAMPSITE to "Manage Campsite", Routes.EDIT_LOTTERY_DATES to "Edit Lottery Dates"))
        }
        // EditLotteryDates · US 02.04.04, 02.04.06 · Figma: O-LOTTERY-EDIT-DATES
        composable(Routes.EDIT_LOTTERY_DATES) {
            Placeholder(nav, "Edit Lottery Dates", "US 02.04.04, 02.04.06", "O-LOTTERY-EDIT-DATES", dialog = false,
                links = listOf(Routes.EDIT_LOTTERY to "Edit Lottery"))
        }
        // Waitlist · US 02.09.01, 02.05.01 · Figma: O-WAITLIST, O-WAITLIST-NOTIFIED
        composable(Routes.WAITLIST) {
            Placeholder(nav, "Waitlist", "US 02.09.01, 02.05.01", "O-WAITLIST, O-WAITLIST-NOTIFIED", dialog = false,
                links = listOf(Routes.MANAGE_CAMPSITE to "Manage Campsite", Routes.OWNER_INBOX to "Owner Inbox"))
        }
        // NotifyWaitlist · US 02.10.01 · Figma: O-NOTIFY-EMPTY, O-NOTIFY-KB, O-NOTIFY
        composable(Routes.NOTIFY_WAITLIST) {
            Placeholder(nav, "Notify Waitlist", "US 02.10.01", "O-NOTIFY-EMPTY, O-NOTIFY-KB, O-NOTIFY", dialog = false,
                links = listOf(Routes.WAITLIST to "Waitlist", Routes.OWNER_HOME to "Owner Home", Routes.OWNER_BOOKINGS to "Owner Bookings", Routes.OWNER_INBOX to "Owner Inbox", Routes.OWNER_PROFILE to "Owner Profile"))
        }
        // OwnerBookings · US 02.07.01 · Figma: O-BOOKINGS-START, O-BOOKINGS
        composable(Routes.OWNER_BOOKINGS) {
            Placeholder(nav, "Owner Bookings", "US 02.07.01", "O-BOOKINGS-START, O-BOOKINGS", dialog = false,
                links = listOf(Routes.OWNER_CHAT to "Owner Chat", Routes.REJECT_RESERVATION to "Reject Reservation", Routes.PAST_BOOKINGS to "Past Bookings", Routes.CANCELLED_BOOKINGS to "Cancelled Bookings", Routes.OWNER_HOME to "Owner Home", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.OWNER_INBOX to "Owner Inbox", Routes.BOOKED_DATES to "Booked Dates", Routes.OWNER_PROFILE to "Owner Profile"))
        }
        // PastBookings · US 02.07.02 · Figma: O-BOOKINGS-PAST
        composable(Routes.PAST_BOOKINGS) {
            Placeholder(nav, "Past Bookings", "US 02.07.02", "O-BOOKINGS-PAST", dialog = false,
                links = listOf(Routes.OWNER_BOOKINGS to "Owner Bookings", Routes.CANCELLED_BOOKINGS to "Cancelled Bookings", Routes.OWNER_CHAT to "Owner Chat", Routes.OWNER_HOME to "Owner Home", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.OWNER_INBOX to "Owner Inbox", Routes.OWNER_PROFILE to "Owner Profile"))
        }
        // CancelledBookings · US 02.07.03 · Figma: O-BOOKINGS-CANCELLED
        composable(Routes.CANCELLED_BOOKINGS) {
            Placeholder(nav, "Cancelled Bookings", "US 02.07.03", "O-BOOKINGS-CANCELLED", dialog = false,
                links = listOf(Routes.OWNER_BOOKINGS to "Owner Bookings", Routes.PAST_BOOKINGS to "Past Bookings", Routes.OWNER_CHAT to "Owner Chat", Routes.OWNER_HOME to "Owner Home", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.OWNER_INBOX to "Owner Inbox", Routes.OWNER_PROFILE to "Owner Profile"))
        }
        // RejectReservation · sheet (dialog for now) · US 02.08.01 · Figma: O-REJECT-EMPTY, -REASON, -REFUND, -KB, O-REJECT
        dialog(Routes.REJECT_RESERVATION) {
            Placeholder(nav, "Reject Reservation", "US 02.08.01", "O-REJECT-EMPTY, -REASON, -REFUND, -KB, O-REJECT", dialog = true,
                links = listOf(Routes.OWNER_HOME to "Owner Home", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.OWNER_INBOX to "Owner Inbox", Routes.REJECT_CONFIRM to "Reject Confirm", Routes.OWNER_BOOKINGS to "Owner Bookings", Routes.OWNER_PROFILE to "Owner Profile"))
        }
        // RejectConfirm · dialog · US 02.08.01 · Figma: O-REJECT-CONFIRM, O-BOOKINGS-REJECTED
        dialog(Routes.REJECT_CONFIRM) {
            Placeholder(nav, "Reject Confirm", "US 02.08.01", "O-REJECT-CONFIRM, O-BOOKINGS-REJECTED", dialog = true,
                links = listOf(Routes.REJECT_RESERVATION to "Reject Reservation", Routes.PAST_BOOKINGS to "Past Bookings", Routes.CANCELLED_BOOKINGS to "Cancelled Bookings", Routes.OWNER_HOME to "Owner Home", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.OWNER_INBOX to "Owner Inbox", Routes.OWNER_PROFILE to "Owner Profile"))
        }
        // OwnerInbox · US 02.10.02 · Figma: O-INBOX
        composable(Routes.OWNER_INBOX) {
            Placeholder(nav, "Owner Inbox", "US 02.10.02", "O-INBOX", dialog = false,
                links = listOf(Routes.OWNER_HOME to "Owner Home", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.OWNER_BOOKINGS to "Owner Bookings", Routes.OWNER_CHAT to "Owner Chat", Routes.OWNER_PROFILE to "Owner Profile"))
        }
        // OwnerChat · US 02.12.01 · Figma: O-CHAT
        composable(Routes.OWNER_CHAT) {
            Placeholder(nav, "Owner Chat", "US 02.12.01", "O-CHAT", dialog = false,
                links = listOf())
        }
        // OwnerProfile · US 03.11.01 · Figma: O-PROFILE
        composable(Routes.OWNER_PROFILE) {
            Placeholder(nav, "Owner Profile", "US 03.11.01", "O-PROFILE", dialog = false,
                links = listOf(Routes.OWNER_HOME to "Owner Home", Routes.OWNER_LOTTERIES to "Owner Lotteries", Routes.OWNER_BOOKINGS to "Owner Bookings", Routes.OWNER_INBOX to "Owner Inbox", Routes.SIGN_IN to "Sign In"))
        }

        // ---------------- Admin (16) ----------------
        // AdminCampsites · US 03.04.01 · Figma: A-CAMPSITES, -FLAGGED, -LIVE, -SEARCH-KB, -SEARCH, -REMOVED, -REMOVED-OWNER
        composable(Routes.ADMIN_CAMPSITES) {
            Placeholder(nav, "Admin Campsites", "US 03.04.01", "A-CAMPSITES, -FLAGGED, -LIVE, -SEARCH-KB, -SEARCH, -REMOVED, -REMOVED-OWNER", dialog = false,
                links = listOf(Routes.ADMIN_CAMPSITE_DETAIL to "Admin Campsite Detail", Routes.ADMIN_PROFILES to "Admin Profiles", Routes.ADMIN_IMAGES to "Admin Images", Routes.NOTIFICATION_LOGS to "Notification Logs", Routes.ADMIN_ACCOUNT to "Admin Account"))
        }
        // AdminCampsiteDetail · US 03.04.01 · Figma: A-CAMPSITE-DETAIL, A-CAMPSITE-FLAGGED
        composable(Routes.ADMIN_CAMPSITE_DETAIL) {
            Placeholder(nav, "Admin Campsite Detail", "US 03.04.01", "A-CAMPSITE-DETAIL, A-CAMPSITE-FLAGGED", dialog = false,
                links = listOf(Routes.REMOVE_CAMPSITE to "Remove Campsite"))
        }
        // RemoveCampsite · dialog · US 03.01.01, 03.07.01 · Figma: A-REMOVE, A-REMOVE-OWNER
        dialog(Routes.REMOVE_CAMPSITE) {
            Placeholder(nav, "Remove Campsite", "US 03.01.01, 03.07.01", "A-REMOVE, A-REMOVE-OWNER", dialog = true,
                links = listOf(Routes.ADMIN_PROFILES to "Admin Profiles", Routes.ADMIN_IMAGES to "Admin Images", Routes.NOTIFICATION_LOGS to "Notification Logs", Routes.ADMIN_CAMPSITES to "Admin Campsites", Routes.ADMIN_ACCOUNT to "Admin Account"))
        }
        // AdminProfiles · US 03.05.01 · Figma: A-PROFILES-ENTRANTS, A-PROFILES, -SEARCH-KB, -SEARCH, and the -REMOVED / -BLOCKED results
        composable(Routes.ADMIN_PROFILES) {
            Placeholder(nav, "Admin Profiles", "US 03.05.01", "A-PROFILES-ENTRANTS, A-PROFILES, -SEARCH-KB, -SEARCH, and the -REMOVED / -BLOCKED results", dialog = false,
                links = listOf(Routes.ADMIN_OWNER_DETAIL to "Admin Owner Detail", Routes.ADMIN_CAMPSITES to "Admin Campsites", Routes.ADMIN_IMAGES to "Admin Images", Routes.NOTIFICATION_LOGS to "Notification Logs", Routes.ADMIN_ACCOUNT to "Admin Account", Routes.REMOVE_PROFILE to "Remove Profile", Routes.ADMIN_ENTRANT_DETAIL to "Admin Entrant Detail"))
        }
        // AdminEntrantDetail · US 03.05.01 · Figma: A-ENTRANT-DETAIL, A-ENTRANT-WARNED
        composable(Routes.ADMIN_ENTRANT_DETAIL) {
            Placeholder(nav, "Admin Entrant Detail", "US 03.05.01", "A-ENTRANT-DETAIL, A-ENTRANT-WARNED", dialog = false,
                links = listOf(Routes.ADMIN_PROFILES to "Admin Profiles", Routes.REMOVE_PROFILE to "Remove Profile"))
        }
        // RemoveProfile · dialog · US 03.02.01 · Figma: A-REMOVE-PROFILE, A-REMOVE-PROFILE-BLOCK
        dialog(Routes.REMOVE_PROFILE) {
            Placeholder(nav, "Remove Profile", "US 03.02.01", "A-REMOVE-PROFILE, A-REMOVE-PROFILE-BLOCK", dialog = true,
                links = listOf(Routes.ADMIN_ENTRANT_DETAIL to "Admin Entrant Detail", Routes.ADMIN_PROFILES to "Admin Profiles"))
        }
        // AdminOwnerDetail · US 03.07.01 · Figma: A-PROFILE-DETAIL, A-PROFILE-WARNED
        composable(Routes.ADMIN_OWNER_DETAIL) {
            Placeholder(nav, "Admin Owner Detail", "US 03.07.01", "A-PROFILE-DETAIL, A-PROFILE-WARNED", dialog = false,
                links = listOf(Routes.ADMIN_PROFILES to "Admin Profiles", Routes.REMOVE_OWNER to "Remove Owner"))
        }
        // RemoveOwner · dialog · US 03.07.01 · Figma: A-REMOVE-OWNER-PROFILE
        dialog(Routes.REMOVE_OWNER) {
            Placeholder(nav, "Remove Owner", "US 03.07.01", "A-REMOVE-OWNER-PROFILE", dialog = true,
                links = listOf(Routes.ADMIN_OWNER_DETAIL to "Admin Owner Detail", Routes.ADMIN_PROFILES to "Admin Profiles"))
        }
        // AdminImages · US 03.06.01 · Figma: A-IMAGES
        composable(Routes.ADMIN_IMAGES) {
            Placeholder(nav, "Admin Images", "US 03.06.01", "A-IMAGES", dialog = false,
                links = listOf(Routes.ADMIN_CAMPSITES to "Admin Campsites", Routes.ADMIN_PROFILES to "Admin Profiles", Routes.NOTIFICATION_LOGS to "Notification Logs", Routes.IMAGE_FOLDER to "Image Folder", Routes.ADMIN_ACCOUNT to "Admin Account"))
        }
        // ImageFolder · US 03.06.01 · Figma: A-IMAGES-FOLDER, A-IMAGES-PROFILE, and their -REMOVED results
        composable(Routes.IMAGE_FOLDER) {
            Placeholder(nav, "Image Folder", "US 03.06.01", "A-IMAGES-FOLDER, A-IMAGES-PROFILE, and their -REMOVED results", dialog = false,
                links = listOf(Routes.ADMIN_CAMPSITES to "Admin Campsites", Routes.ADMIN_PROFILES to "Admin Profiles", Routes.NOTIFICATION_LOGS to "Notification Logs", Routes.IMAGE_DETAIL to "Image Detail", Routes.ADMIN_IMAGES to "Admin Images", Routes.ADMIN_ACCOUNT to "Admin Account"))
        }
        // ImageDetail · US 03.03.01 · Figma: A-IMAGE-DETAIL, A-IMAGE-PROFILE-DETAIL
        composable(Routes.IMAGE_DETAIL) {
            Placeholder(nav, "Image Detail", "US 03.03.01", "A-IMAGE-DETAIL, A-IMAGE-PROFILE-DETAIL", dialog = false,
                links = listOf(Routes.IMAGE_FOLDER to "Image Folder", Routes.REMOVE_IMAGE to "Remove Image", Routes.ADMIN_CAMPSITE_DETAIL to "Admin Campsite Detail", Routes.ADMIN_ENTRANT_DETAIL to "Admin Entrant Detail"))
        }
        // RemoveImage · dialog · US 03.03.01 · Figma: A-IMAGE-REMOVE, A-IMAGE-PROFILE-REMOVE
        dialog(Routes.REMOVE_IMAGE) {
            Placeholder(nav, "Remove Image", "US 03.03.01", "A-IMAGE-REMOVE, A-IMAGE-PROFILE-REMOVE", dialog = true,
                links = listOf(Routes.IMAGE_DETAIL to "Image Detail", Routes.IMAGE_FOLDER to "Image Folder"))
        }
        // NotificationLogs · US 03.08.01 · Figma: A-LOGS, A-LOGS-14
        composable(Routes.NOTIFICATION_LOGS) {
            Placeholder(nav, "Notification Logs", "US 03.08.01", "A-LOGS, A-LOGS-14", dialog = false,
                links = listOf(Routes.MESSAGE_LOGS to "Message Logs", Routes.ADMIN_CAMPSITES to "Admin Campsites", Routes.ADMIN_PROFILES to "Admin Profiles", Routes.ADMIN_IMAGES to "Admin Images", Routes.LOG_DATE_RANGE to "Log Date Range", Routes.ADMIN_ACCOUNT to "Admin Account"))
        }
        // LogDateRange · US 03.08.01 · Figma: A-LOGS-DATES, A-LOGS-DATES-14
        composable(Routes.LOG_DATE_RANGE) {
            Placeholder(nav, "Log Date Range", "US 03.08.01", "A-LOGS-DATES, A-LOGS-DATES-14", dialog = false,
                links = listOf(Routes.NOTIFICATION_LOGS to "Notification Logs"))
        }
        // MessageLogs · US 03.12.01 · Figma: A-MESSAGES, A-MESSAGES-FLAGGED
        composable(Routes.MESSAGE_LOGS) {
            Placeholder(nav, "Message Logs", "US 03.12.01", "A-MESSAGES, A-MESSAGES-FLAGGED", dialog = false,
                links = listOf(Routes.NOTIFICATION_LOGS to "Notification Logs", Routes.ADMIN_CAMPSITES to "Admin Campsites", Routes.ADMIN_PROFILES to "Admin Profiles", Routes.ADMIN_IMAGES to "Admin Images", Routes.ADMIN_ACCOUNT to "Admin Account", Routes.ADMIN_OWNER_DETAIL to "Admin Owner Detail"))
        }
        // AdminAccount · US 03.11.01 · Figma: A-ACCOUNT (A-VIEW-ENTRANT / A-VIEW-OWNER reuse Profile and OwnerProfile)
        composable(Routes.ADMIN_ACCOUNT) {
            Placeholder(nav, "Admin Account", "US 03.11.01", "A-ACCOUNT (A-VIEW-ENTRANT / A-VIEW-OWNER reuse Profile and OwnerProfile)", dialog = false,
                links = listOf(Routes.ADMIN_CAMPSITES to "Admin Campsites", Routes.ADMIN_PROFILES to "Admin Profiles", Routes.ADMIN_IMAGES to "Admin Images", Routes.NOTIFICATION_LOGS to "Notification Logs", Routes.SIGN_IN to "Sign In", Routes.EXPLORE to "Explore", Routes.OWNER_HOME to "Owner Home"))
        }
    }
}

/**
 * Temporary screen shown until a team member builds the real one.
 * Shows the screen name, its user stories and Figma pages, a button for each screen it links to,
 * and Back when there is a previous screen.
 *
 * @param nav the NavHost controller
 * @param title screen name
 * @param stories user stories this screen covers
 * @param figma Figma pages this screen covers
 * @param links routes this screen leads to in Figma, paired with their button labels
 * @param dialog true when the route is a dialog, so the placeholder is drawn as a card
 */
@Composable
fun Placeholder(
    nav: NavHostController,
    title: String,
    stories: String,
    figma: String,
    links: List<Pair<String, String>>,
    dialog: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(text = stories, style = MaterialTheme.typography.bodyMedium)
            Text(text = "Figma: $figma", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            links.forEach { (target, label) ->
                Button(onClick = { nav.navigate(target) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = label)
                }
            }
            if (nav.previousBackStackEntry != null) {
                OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Back")
                }
            }
        }
    }
    if (dialog) {
        Card { content() }
    } else {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { content() }
    }
}