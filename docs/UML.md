# Events: UML Class Diagram (half-way checkpoint)

GitHub renders the Mermaid diagram below directly in the wiki or README.
Layers: **model** (plain data) → **domain** (pure rules, unit-tested) → **data** (repository
interfaces with Firestore and in-memory implementations) → **ui** (MVVM view models and
Compose screens).

```mermaid
classDiagram
direction LR

%% ---------- Model ----------
class UserProfile {
  +deviceId: String
  +name: String
  +email: String
  +phone: String
  +activeRole: Role
  +isAdmin: Boolean
  +firstName: String
  +initials: String
  +availableRoles: List~Role~
}
class Role {
  <<enumeration>>
  ENTRANT
  OWNER
  ADMIN
}
class Campsite {
  +id: String
  +ownerId: String
  +name: String
  +location: String
  +latitude: Double?
  +longitude: Double?
  +maxOccupancy: Int
  +amenities: String
  +tags: List~String~
  +pricePerNight: Int
  +imageBase64: String?
  +rating: Double
  +hasMapLocation: Boolean
}
class Lottery {
  +id: String
  +campsiteId: String
  +registrationStart: Long
  +registrationEnd: Long
  +winnerCount: Int
  +waitlistCap: Int?
  +status: LotteryStatus
  +entrantCount: Int
  +isRegistrationOpen(now) Boolean
  +isDrawDue(now) Boolean
  +isUpcoming(now) Boolean
}
class WaitlistEntry {
  +lotteryId: String
  +entrantId: String
  +entrantName: String
  +joinedAt: Long
  +status: EntryStatus
}
class AppNotification {
  +id: String
  +recipientId: String
  +type: NotificationType
  +title: String
  +body: String
  +campsiteId: String
  +read: Boolean
}
UserProfile --> Role
Campsite "1" o-- "0..*" Lottery : listed in
Lottery "1" *-- "0..*" WaitlistEntry : waiting list
UserProfile "1" --> "0..*" WaitlistEntry : joins
UserProfile "1" --> "0..*" AppNotification : receives

%% ---------- Domain ----------
class LotteryDrawer {
  <<object>>
  +pickWinners(entries, winnerCount, random) Set~String~
  +draw(lottery, entries, now, random) DrawResult
}
class DrawResult {
  +entries: List~WaitlistEntry~
  +notifications: List~AppNotification~
  +winnerIds: Set~String~
}
class WaitlistPolicy {
  <<object>>
  +checkJoin(lottery, alreadyJoined, size, now) WaitlistError?
  +checkLeave(lottery, joined, now) WaitlistError?
}
class CampsiteSearch {
  <<object>>
  +search(campsites, query) List~Campsite~
}
class Validators {
  <<ProfileValidator · CampsiteValidator · LotteryValidator>>
  +validate(...) Map~Field, String~
}
class QrCodes {
  <<object>>
  +payloadFor(campsiteId) String
  +parseCampsiteId(text) String?
}
class Clock {
  <<interface>>
  +now() Long
}
class DrawScheduler {
  +drawDue(lotteries) Int
  +start(scope) Job
}
LotteryDrawer ..> DrawResult
DrawScheduler --> Clock
DrawScheduler --> LotteryRepository

%% ---------- Data ----------
class ProfileRepository {
  <<interface>>
  +observeProfile(deviceId) Flow~UserProfile?~
  +saveProfile(profile)
}
class CampsiteRepository {
  <<interface>>
  +observeCampsites() Flow
  +observeCampsite(id) Flow
  +observeCampsitesByOwner(ownerId) Flow
  +createCampsite(campsite) String
  +updateCampsite(campsite)
  +deleteCampsite(id)
}
class LotteryRepository {
  <<interface>>
  +observeLotteriesForCampsite(id) Flow
  +createLottery(lottery) String
  +observeEntries(lotteryId) Flow
  +joinWaitlist(lotteryId, entrantId, name, now)
  +leaveWaitlist(lotteryId, entrantId, now)
  +runDrawIfDue(lotteryId, now, random) Boolean
}
class NotificationRepository {
  <<interface>>
  +observeNotifications(recipientId) Flow
  +markAllRead(recipientId)
}
class FirestoreEventsStore
class InMemoryEventsStore
FirestoreEventsStore ..|> ProfileRepository
FirestoreEventsStore ..|> CampsiteRepository
FirestoreEventsStore ..|> LotteryRepository
FirestoreEventsStore ..|> NotificationRepository
InMemoryEventsStore ..|> ProfileRepository
InMemoryEventsStore ..|> CampsiteRepository
InMemoryEventsStore ..|> LotteryRepository
InMemoryEventsStore ..|> NotificationRepository
FirestoreEventsStore ..> WaitlistPolicy
FirestoreEventsStore ..> LotteryDrawer
InMemoryEventsStore ..> WaitlistPolicy
InMemoryEventsStore ..> LotteryDrawer

class AppContainer {
  +deviceId: String
  +usingServer: Boolean
  +drawScheduler: DrawScheduler
  +create(context)$ AppContainer
  +inMemory(deviceId, clock)$ AppContainer
}
AppContainer --> ProfileRepository
AppContainer --> CampsiteRepository
AppContainer --> LotteryRepository
AppContainer --> NotificationRepository
AppContainer --> Clock

%% ---------- UI (MVVM control classes) ----------
class ProfileViewModel {
  +state: StateFlow~ProfileState~
  +save(onSaved)
  +switchRole(role)
}
class ExploreViewModel
class SearchViewModel { +onQueryChange(q) }
class CampsiteDetailsViewModel {
  +state: StateFlow~DetailsState~
  +join(name)
  +leave()
}
class MyLotteriesViewModel
class NotificationsViewModel
class CampsiteFormViewModel { +save(owner, onSaved) }
class ManageCampsiteViewModel
class CreateLotteryViewModel { +publish(onPublished) }
class WaitlistViewModel
class AdminCampsitesViewModel
class AdminCampsiteViewModel { +remove() }
ProfileViewModel --> AppContainer
ExploreViewModel --> AppContainer
SearchViewModel --> CampsiteSearch
CampsiteDetailsViewModel --> AppContainer
CampsiteFormViewModel --> Validators
CreateLotteryViewModel --> Validators
AdminCampsiteViewModel --> AppContainer
```

## Notes
- **Device identity (US 01.09.01):** `UserProfile.deviceId` is the Android ID and also the
  Firestore document ID, so no username or password exists.
- **Automatic draw (US 02.05.01):** `DrawScheduler` watches lotteries while the app runs.
  `runDrawIfDue` runs `LotteryDrawer` inside a Firestore transaction so exactly one device
  performs each draw and every entrant gets a win or loss `AppNotification`.
- **Testability:** domain objects are pure Kotlin; view models depend on `AppContainer`,
  which tests build with `InMemoryEventsStore` and a `FixedClock`.
