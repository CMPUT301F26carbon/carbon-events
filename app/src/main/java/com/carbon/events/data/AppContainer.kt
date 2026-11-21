/*
 * AppContainer.kt (Vaibhav)
 *
 * Hands every screen the data layer: Firestore when Firebase is set up, otherwise the
 * in-memory store, plus this phone's ID and the draw scheduler.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * The dependencies every screen can use.
 *
 * @property deviceId stable phone identifier used to look up a trusted profile; not a login credential.
 * @property profiles profile data.
 * @property campsites campsite data.
 * @property lotteries lottery and waiting-list data.
 * @property notifications notification data.
 * @property clock time source.
 * @property usingServer true when repositories use Firestore; this is not a network-health check.
 * @property social ratings, follows, chats, announcements, reports and moderation.
 */
open class AppContainer(
    val deviceId: String,
    val deviceName: String = "This phone",
    val profiles: ProfileRepository,
    val campsites: CampsiteRepository,
    val lotteries: LotteryRepository,
    val notifications: NotificationRepository,
    val clock: Clock,
    val usingServer: Boolean,
    val userId: String = deviceId,
    val auth: AuthRepository = InMemoryAuth(deviceId),
    val social: SocialRepository = InMemoryEventsStore(),
) {
    /** The same data layer, acting as the signed-in person [id] (set once their profile loads). */
    fun forUser(id: String): AppContainer =
        if (id == userId) this
        else AppContainer(deviceId, deviceName, profiles, campsites, lotteries, notifications, clock, usingServer, id, auth, social).also {
            it.sharedScheduler = drawScheduler
        }

    private var sharedScheduler: DrawScheduler? = null

    /** Runs automatic lottery draws while the app is open. */
    val drawScheduler: DrawScheduler by lazy { sharedScheduler ?: DrawScheduler(lotteries, clock) }

    companion object {
        /** The app's container, set up by MainActivity.setupDataLayer(). */
        lateinit var instance: AppContainer
            private set

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var drawsStarted = false

        /** Creates the container the first time; later calls keep the same one. */
        fun init(context: Context): AppContainer {
            if (!::instance.isInitialized) instance = create(context.applicationContext)
            return instance
        }

        /** Starts the automatic lottery draws once for the life of the app. */
        fun startDraws() {
            if (drawsStarted) return
            drawsStarted = true
            instance.drawScheduler.start(appScope)
        }

        /**
         * Builds the production container: Firestore if Firebase is configured, otherwise
         * an empty in-memory store (used only for builds without google-services.json, e.g. CI).
         */
        fun create(context: Context): AppContainer {
            val deviceId = readDeviceId(context)
            val firebase = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
            return if (firebase != null) {
                val store = FirestoreEventsStore(FirebaseFirestore.getInstance(firebase))
                AppContainer(
                    deviceId, readDeviceName(), store, store, store, store, Clock.System, usingServer = true,
                    auth = FirebaseAuthStore(FirebaseAuth.getInstance(firebase)), social = store,
                )
            } else {
                // Nobody is signed in yet, so the offline build opens on Welcome like the real one.
                inMemory(deviceId, Clock.System, auth = InMemoryAuth(null))
            }
        }

        /** "Google Pixel 8"-style name for the trusted-phones list. */
        private fun readDeviceName(): String {
            val maker = android.os.Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            val model = android.os.Build.MODEL.orEmpty()
            return (if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model").trim().ifBlank { "Android phone" }
        }

        /** A container on an in-memory store, for tests and builds without Firebase. */
        fun inMemory(deviceId: String, clock: Clock, store: InMemoryEventsStore = InMemoryEventsStore(), auth: AuthRepository = InMemoryAuth(deviceId)) =
            AppContainer(deviceId, "This phone", store, store, store, store, clock, usingServer = false, auth = auth, social = store)

        /**
         * The Android ID: unique per device, user and app signing key, and stable across
         * reinstalls for the same signing/user context. It looks up trust after credential-based
         * signup; it does not replace authentication. If Android gives
         * none, a random ID is made once and kept on this phone, so two such phones never share
         * one (the old shared "unknown-device" fallback would have mixed them up).
         */
        @SuppressLint("HardwareIds")
        private fun readDeviceId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() && it != BROKEN_ANDROID_ID } ?: fallbackDeviceId(context)

        /** A known emulator bug returns this same ID on every phone. */
        private const val BROKEN_ANDROID_ID = "9774d56d682e549c"

        /** A random ID created on first use and stored on this phone. */
        private fun fallbackDeviceId(context: Context): String {
            val prefs = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
            prefs.getString("id", null)?.let { return it }
            val id = "phone-" + java.util.UUID.randomUUID().toString()
            prefs.edit().putString("id", id).apply()
            return id
        }
    }
}
