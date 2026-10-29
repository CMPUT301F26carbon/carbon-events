# Connecting Events to Firebase (≈10 minutes)

Until these steps are done, the app runs in **demo mode** on built-in sample data
(you'll see "Demo mode · sample data" on the splash screen). Nothing in the code needs to
change: once `app/google-services.json` exists, the app uses Cloud Firestore automatically.

## 1. Create the Firebase project
1. Go to <https://console.firebase.google.com> and sign in with a team Google account.
2. **Add project** → name it e.g. `carbon-events` → Google Analytics is optional (off is fine).
3. Stay on the free **Spark** plan. Nothing in this checkpoint needs Blaze.

## 2. Register the Android app
1. In the project overview click the **Android** icon.
2. **Android package name:** `com.carbon.events` (must match exactly).
3. App nickname: `Events`. The SHA-1 is not needed.
4. **Download `google-services.json`** and put it in the `app/` folder
   (next to `app/build.gradle.kts`).
5. Skip the "add SDK" steps; the project already has them.

## 3. Create the Firestore database
1. Left menu → **Build → Firestore Database → Create database**.
2. Location: `northamerica-northeast2 (Toronto)` or `nam5`.
3. Start in **test mode** for development. Before the demo, paste the rules from
   [`firestore.rules`](firestore.rules) into the **Rules** tab and **Publish**.

No indexes are needed: every query filters on one field and sorts on the device.

## 4. Run it
Sync Gradle in Android Studio and press **Run**. The splash screen no longer says
"Demo mode". The first launch on each phone shows the profile setup, and after that the
phone is recognised automatically (US 01.09.01).

The database starts empty. Register a campsite as an owner (Profile → Viewing as → Owner →
Add) to get data in.

## 5. Make someone an admin (US 03.01.01, US 03.04.01)
Admins are granted by hand, as the problem description implies:
1. Open the app once on the admin's phone so their profile exists.
2. Firestore console → `profiles` collection → their document (the ID is their device ID,
   shown on the Profile screen) → set field `isAdmin` (boolean) to `true`.
3. In the app: Profile → Viewing as → **Admin**.

## 6. (Optional) Google Maps key for the map picker
Without a key the app still works, and owners type coordinates instead of tapping a map.
1. <https://console.cloud.google.com> → select the **same project** Firebase created.
2. **APIs & Services → Library → Maps SDK for Android → Enable.** Google asks for a
   billing account on the project; normal use stays inside the free monthly credit.
3. **Credentials → Create credentials → API key.** Restrict it to Android apps with
   package `com.carbon.events`.
4. Add this line to `local.properties` (never commit this file):
   ```
   MAPS_API_KEY=your-key-here
   ```

## Should `google-services.json` be committed?
Yes. The TA builds from the repo and needs it to connect to the server. It is not a
secret: access is controlled by the Firestore security rules.
