# carbon-events

Welcome to **carbon-events**, a campsite reservation Android app by Team Carbon (CMPUT 301, Fall 2026).

Popular campsites fill up fast and are spread across different websites. carbon-events uses a **lottery** instead of first-come, first-served: owners list a campsite for a block of time, anyone can join the waiting list, and the app randomly draws winners when registration closes. You don't have to be first, just interested.

## Table of Contents
* [Team Members](#team-members)
* [Overview](#overview)
* [Features](#features)
* [Project Status](#project-status)
* [Tech Stack](#tech-stack)
* [Getting Started](#getting-started)
* [Wiki Page](#wiki-page)
* [License](#license)
* [Credits](#credits)
* [References and Resources](#references-and-resources)

## Team Members

| CCID | Github Username | Name |
| ------------- | ------------- | ------------- |
| anair5 | [nair-adi](https://github.com/nair-adi) | Aditya Nair |
| arajput3 | [rajputansh09](https://github.com/rajputansh09) | Ansh Rajput |
| rdcunha | [raynerdcunha](https://github.com/raynerdcunha) | Rayner Dcunha |
| suvir1 | [SiliconBeast](https://github.com/SiliconBeast) | Suvir Duggal |
| tausifha | [TausifHasnineChoudhury](https://github.com/TausifHasnineChoudhury) | Tausif Hasnine Choudhury |
| baswala | [RedCyrus14](https://github.com/RedCyrus14) | Vaibhav Baswala |

## Overview

The app has three roles:

* **Entrant:** searches and filters campsites, joins lottery waiting lists, reserves a spot after winning, and manages their reservations.
* **Campsite Owner:** lists campsites, runs lotteries, manages reservations, and messages guests.
* **Administrator:** moderates the app by browsing and removing campsites, profiles and images, and reviewing logs. An admin can also act as an entrant or owner.

## Features

**Done at this checkpoint (Project Part 3):**

* Search campsites and see their summary and details
* Join and leave a lottery's waiting list
* Notifications when you win or lose a lottery, including while the app is closed
* Promo QR codes: owners get one per campsite, and entrants scan it to open the campsite
* Owners register, view, update and list their campsites
* Owners run lotteries: registration and booking periods, number of winners, editing, and an automatic draw when registration closes
* Owners see booked dates, active and past reservations, and the waitlist
* Profiles you can update, and phones identified by device
* Admins browse and remove campsites and browse profiles
* **Wow factor (approved by our TA):** an owner–entrant messaging tool with an inbox, unread counts, new-message banners, and read-only notices for entrants who were not selected

**Planned for Project Part 4:** filters, choosing a campsite and dates after winning, declining, reservation confirmations and CSV export, cancelling, deleting a profile, opting out of notifications, the app policy, ratings, multi-campsite lotteries, waitlist caps, cancelled and rejected reservations, notifying the waitlist, rule confirmation, and the remaining admin tools (removing profiles, images and owners, notification and message logs, and acting as an owner or entrant).

## Project Status

**Project Part 3 (half-way checkpoint):** 30 of the 60 user stories are complete. The [Part 3: Product Backlog](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-Product-Backlog) marks which ones, and the remaining stories are planned for Project Part 4.

## Tech Stack

* Android (Kotlin), Jetpack Compose with Material 3, MVVM with ViewModels
* Firebase Cloud Firestore and Firebase Authentication
* WorkManager for background lottery draws, results and message alerts
* OpenStreetMap (osmdroid) for campsite maps, with no API key
* CameraX and ML Kit for QR code scanning; ZXing for generating QR codes
* JUnit, Robolectric, Compose UI tests, Espresso and the Firebase Emulator for testing
* GitHub Actions for continuous integration (unit, UI and Firestore tests)
* Dokka for the generated code documentation
* Figma for UI mockups and storyboards

## Getting Started

1. Clone the repository and open it in Android Studio.
2. Without Firebase set up, the app still builds and runs in **offline demo mode**: it starts empty, nothing is saved, and a red "Offline demo" strip shows at the top. To connect it to Firebase, follow [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md). `google-services.json` is not committed to the repository.
3. Run the app on an emulator or phone with Android 8.0 (API 26) or newer.
4. Run the tests:
   * Unit tests: `./gradlew :app:testDebugUnitTest`
   * UI tests (emulator or phone connected): `./gradlew :app:connectedDebugAndroidTest`
   * Code documentation: `./gradlew :app:dokkaGenerateHtml`

## Wiki Page

Project details, meeting minutes, the product backlog, CRC cards and storyboards are on our [wiki](https://github.com/CMPUT301F26carbon/carbon-events/wiki).

* [Project Problem Description](https://ualberta-cmput301.github.io/projects/project_problem_descr.html)
* [Part 3: Product Backlog](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-Product-Backlog)
* [Part 3: Sprint Planning and Reviews](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-Sprint-Planning-and-Reviews)
* [Part 3: MVC Design](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-MVC-Design)
* [Part 3: CRC Cards](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-CRC-Cards)
* [Part 3: UML Class Diagram](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-UML-Class-Diagram)
* [Part 3: UI Mockups](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-UI-Mockups)
* [Part 3: Storyboard](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-Storyboard)
* [Part 3: Wow Factor](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-Wow-Factor)
* [Part 3: TA Feedback Addressed](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-TA-Feedback-Addressed)

## License

Copyright (C) 2026 Team Carbon (Aditya Nair, Ansh Rajput, Rayner Dcunha, Suvir Duggal, Tausif Hasnine Choudhury, Vaibhav Baswala).

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY. See the [LICENSE](LICENSE) file for the full text.

## Credits

We use these open-source and third-party libraries. Each is used under its own license.

| Library | Used for | License |
|---|---|---|
| [Jetpack Compose, Material 3, Navigation, Lifecycle](https://developer.android.com/jetpack) | UI, navigation and ViewModels | Apache 2.0 |
| [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) | Background lottery draws and alerts | Apache 2.0 |
| [CameraX](https://developer.android.com/media/camera/camerax) | Camera preview for QR scanning | Apache 2.0 |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Asynchronous work | Apache 2.0 |
| [Firebase Firestore and Authentication](https://firebase.google.com/) | Database and sign-in | Google APIs Terms of Service |
| [ML Kit Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning) | Reading QR codes | ML Kit Terms of Service |
| [ZXing](https://github.com/zxing/zxing) | Generating promo QR codes | Apache 2.0 |
| [osmdroid](https://github.com/osmdroid/osmdroid) | Campsite maps | Apache 2.0 |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | Map tiles and data | © OpenStreetMap contributors, ODbL |
| [JUnit 4](https://junit.org/junit4/), [AndroidX Test and Espresso](https://developer.android.com/training/testing) | Unit and UI tests | EPL 1.0 / Apache 2.0 |
| [Robolectric](https://robolectric.org/) | Running Android tests on the JVM | MIT |
| [Roborazzi](https://github.com/takahirom/roborazzi) | Screenshot tests | Apache 2.0 |
| [Dokka](https://github.com/Kotlin/dokka) | Generating the KDoc documentation site | Apache 2.0 |

## References and Resources

> Anthropic (2026), Claude (Claude Opus 5.5), "Verification of button placement, colour scheme, Storyboard design and arrangement using the Figma -Claude connector, implementation of user stories and wow-factor requirements for the Campsite app", October 19, 2026.

We used Claude (Claude Opus 5.5) from Anthropic as a verification and review tool during the development of the Campsite app. We used the Figma - Claude connector to review and verify the Storyboard design and layout/arrangement in Figma. Claude was also used to verify button placement and colour scheme.

CRC cards and the Product Backlog were completed manually without the use of an LLM. One final verification was performed using Claude to check that all user stories and wow-factor requirements had been implemented completely and correctly.

### AI Use — Project Part 3

Each entry gives the tool and model, the date we used it, the prompt we gave (summarized), and what came out of it. Team Carbon reviewed, tested and edited all AI output and is responsible for everything submitted.

| Tool and model | Date accessed | Prompt given (summary) | Used for |
|---|---|---|---|
| Claude (Claude Opus 5.5), Anthropic, with the Figma connector | November 5, 2026 | "Compare our Part 2 and Part 3 UI screens in Figma. Duplicate the removed screens into a Deleted section, add the new screens, and update the owner bottom bar and the storyboard." | Figma UI mockup and storyboard updates; exported screen images in `images/part3` |
| Claude (Claude Opus 5.5), Anthropic | November 5–6, 2026 | "Check our GitHub wiki against the Project Part 3 requirements and give me the text for each Part 3 page." | Drafts of the Part 3 wiki pages: UI Mockups, Storyboard, Wow Factor, TA Feedback Addressed, Product Backlog, Sprint Planning and Reviews, and the Messaging CRC card |
| Claude (Claude Opus 5.5), Anthropic | November 6, 2026 | "Add the purpose and any currently outstanding issues to the header comment of every source file, as the Part 3 requirements ask." | Source file header comments (PR #164) |
| Claude (Claude Opus 5.5), Anthropic | November 6, 2026 | "Update the README for Project Part 3 and credit the third-party libraries we use." | This README and `docs/FIREBASE_SETUP.md` (PRs #168, #169) |
| Claude (Claude Opus 5.5), Anthropic | November 6, 2026 | "Write UI (intent) tests for the 16 half-way user stories that have no UI test yet." | `OwnerCampsitesTest`, `OwnerLotteryTest`, `LotteryPeriodsTest`, `LotteryResultsTest`, `AdminProfilesTest` (PR #170) |
| Claude (Claude Opus 5.5), Anthropic | October–November 2026 | "Help build the Android prototype for our user stories and review the code." | Android prototype development and code review |
| Codex, OpenAI | November 2026 | "Help organize the code, write KDoc and tests, and debug build and startup issues." | Code organization, KDoc, test development and debugging |
