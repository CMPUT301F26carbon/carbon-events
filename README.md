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

* Search and filter campsites by location, dates and number of guests
* Lottery-based waiting lists with automatic winner draws when registration closes
* Multi-campsite lotteries, where winners choose their campsite and dates
* Reservations with rule confirmation, cancellation, and CSV export
* Promotional QR codes that open a campsite's details
* Campsite map locations and promo / profile image uploads
* Notifications (with opt-out), including while the app is closed
* Campsite ratings
* Admin tools to remove campsites, owners, profiles and images, and review notification and message logs
* Users identified by device, with no username or password
* **Wow factor:** an owner–entrant messaging tool with an inbox, unread counts, new-message banners, and read-only notices for entrants who were not selected

## Project Status

**Project Part 3 (half-way checkpoint):** 30 of the 60 user stories are complete. The [Part 3: Product Backlog](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-3:-Product-Backlog) marks which ones, and the remaining stories are planned for Project Part 4.

## Tech Stack

* Android (Kotlin), Jetpack Compose with Material 3, MVVM with ViewModels
* Firebase Cloud Firestore and Firebase Authentication
* WorkManager for background lottery draws, results and message alerts
* Google Maps for Compose for campsite locations
* CameraX and ML Kit for QR code scanning; ZXing for generating QR codes
* JUnit, Robolectric, Compose UI tests, Espresso and the Firebase Emulator for testing
* GitHub Actions for continuous integration (unit, UI and Firestore tests)
* Dokka for the generated code documentation
* Figma for UI mockups and storyboards

## Getting Started

1. Clone the repository and open it in Android Studio.
2. Without Firebase set up, the app runs in **demo mode** on built-in sample data. To connect it to Firebase, follow [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md). `google-services.json` is not committed to the repository.
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

## References and Resources

> Anthropic (2026), Claude (Claude Opus 5.5), "Verification of button placement, colour scheme, Storyboard design and arrangement using the Figma -Claude connector, implementation of user stories and wow-factor requirements for the Campsite app", October 19, 2026.

We used Claude (Claude Opus 5.5) from Anthropic as a verification and review tool during the development of the Campsite app. We used the Figma - Claude connector to review and verify the Storyboard design and layout/arrangement in Figma. Claude was also used to verify button placement and colour scheme.

CRC cards and the Product Backlog were completed manually without the use of an LLM. One final verification was performed using Claude to check that all user stories and wow-factor requirements had been implemented completely and correctly.

### AI Use — Project Part 3

> Anthropic (2026), Claude (Claude Opus 5.5). AI assistance with Android prototype development, code review, source file documentation, Figma UI mockup and storyboard updates, and wiki formatting, October–November 2026.  
> OpenAI (2026), Codex. AI assistance with code organization, KDoc, test development, and debugging, November 2026.

We used AI tools to draft and revise portions of the app, document the code, develop tests, investigate build and startup issues, update the Figma UI mockups and storyboards, and format the wiki pages. Team Carbon reviewed all AI output and is responsible for the code and deliverables submitted for the half-way checkpoint.
