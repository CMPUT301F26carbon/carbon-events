# carbon-events

Welcome to **carbon-events**, a campsite reservation Android app by Team Carbon (CMPUT 301, Fall 2026).

Popular campsites fill up fast and are spread across different websites. carbon-events uses a **lottery** instead of first-come, first-served: owners list a campsite for a block of time, anyone can join the waiting list, and the app randomly draws winners when registration closes. You don't have to be first, just interested.

## Table of Contents
* [Team Members](#team-members)
* [Overview](#overview)
* [Features](#features)
* [Tech Stack](#tech-stack)
* [Wiki Page](#wiki-page)
* [License](#license)

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
* Notifications (with opt-out) and owner-to-guest messaging
* Campsite ratings
* Admin tools to remove campsites, owners, profiles and images, and review notification and message logs
* Users identified by device, with no username or password

## Tech Stack

* Android (Kotlin)
* Firebase for campsites, lotteries, reservations and real-time updates
* Figma for UI mockups and storyboards

## Wiki Page

Project details, meeting minutes, the product backlog, CRC cards and storyboards are on our [wiki](https://github.com/CMPUT301F26carbon/carbon-events/wiki).

* [Project Problem Description](https://ualberta-cmput301.github.io/projects/project_problem_descr.html)
* [Part 2: Product Backlog](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-2:-Product-Backlog)
* [Part 2: CRC Cards](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-2:-CRC-Cards)
* [Part 2: Storyboard](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-2:-Storyboard)
* [Part 2: UI Mockups](https://github.com/CMPUT301F26carbon/carbon-events/wiki/Part-2:-UI-Mockups)

## License

[GPL-3.0](LICENSE)
