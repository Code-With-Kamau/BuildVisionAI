# BuildVision AI

[![Android SDK](https://img.shields.io/badge/Android%20SDK-API%2037-brightgreen.svg)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Language-Java%2017-orange.svg)](https://www.oracle.com/java/)
[![AGP](https://img.shields.io/badge/AGP-9.4.0-blue.svg)](https://developer.android.com/studio/releases/gradle-plugin)
[![Gradle](https://img.shields.io/badge/Gradle-9.6.0-green.svg)](https://gradle.org)
[![Tests](https://img.shields.io/badge/Tests-192%20Passed-success.svg)](app/src/test/java)

**BuildVision AI** is a native Android application that turns floor plan drawings into instant material quantity take-offs, regional cost estimates, and multi-version architectural review workflows. Designed specifically for construction professionals, architects, contractors, and project owners.

---

## 🌟 Key Features

* 📐 **Interactive 2D Floor Plan Editor**
  * Touch gesture controls (pan, pinch-to-zoom, smooth fling).
  * Smart snapping engine (grid, alignment, wall connections, opening snap).
  * Dynamic wall graph topology & automatic room footprint detection.
  * Drag-and-drop openings (doors, windows) and elements (columns, stairs, furniture).
  * Full multi-level undo/redo command stack.

* 💰 **Automated Material Quantity Take-Off & Cost Estimation**
  * Automatic bill of quantities: walling blocks, cement, sand, ballast, reinforcement steel, roof trusses, finishes, and labor.
  * Regional Rate Packs covering all **47 Kenyan Counties** across **7 regional cost index zones**.
  * Customizable rates, contingency, preliminaries, overheads, and project overrides.
  * Real-time budget position indicators (under/over budget alerts).

* 🔄 **Design Versioning & Approval Workflow**
  * Version state lifecycle: `Draft` ➔ `Under Review` ➔ `Approved` ➔ `Frozen`.
  * Multi-version protection via Room database triggers preventing edits to frozen/approved versions.
  * Change proposals, comments, suggestions, and team role permissions (`Owner`, `Architect`, `Engineer`, `Reviewer`).

* 🤖 **AI Architectural Assistant**
  * On-device `OfflineAssistant` answering budget queries, checking drawing sanity, and proposing design revisions.
  * `BudgetAdvisor` integration for live re-estimations based on assistant suggestions.
  * Optional Retrofit cloud assistant endpoint support.

* 📄 **PDF & PNG Report Generation**
  * Asynchronous background generation of formatted PDF cost estimates and high-resolution PNG plan renders using WorkManager.
  * Android FileProvider integration for easy sharing via WhatsApp, Email, or Drive.

* 🌍 **Localization**
  * Full support for **English** and **Swahili** (`sw` locale).

---

## 🏗️ Tech Stack & Architecture

* **Language:** Java 17 (Pure Java core domain + Android Java shell)
* **SDK Targets:** `compileSdk 37`, `targetSdk 36`, `minSdk 24`
* **UI Architecture:** Material Design 3, Navigation Component, ViewBinding, Custom `View` / `Canvas` rendering
* **Persistence:** Room 2.8.5 with SQLite triggers and relational schema (21 tables)
* **Background Tasks:** WorkManager 2.12.0
* **Networking:** Retrofit 3.0.0 + Gson 2.14.0 (optional cloud AI)
* **Testing:** JUnit 4 (192 unit tests covering pure Java core)

### Package Overview

```
com.buildvisionai
 ├── ai            # On-device AI assistant, budget advisor, plan summarizer
 ├── collaboration # Roles, permissions, approval workflows, change proposals
 ├── data          # Room mappers and remote API services
 ├── database      # Room database, DAOs, entities, version guard triggers
 ├── domain        # Drawing sanity checker, project validation, version diffing
 ├── estimation    # Material estimator, Kenya rate packs, county multipliers
 ├── geometry      # Wall graph, room detection, spatial index, polygon math
 ├── models        # Plain domain models (Project, Floor, Wall, Room, Door...)
 ├── planner       # EditorController, tools, snapping engine, command stack
 ├── reports       # PDF report writer, image exporter, WorkManager worker
 ├── repository    # Repositories and executor thread pools
 ├── ui            # Editor fragment, custom views, estimate screens, versions
 └── utils         # Helper utilities (units, formatters, strings)
```

---

## 🚀 Getting Started

### Prerequisites

* **Android Studio:** Ladybug (2024.2) or newer
* **JDK:** Java 17
* **Android SDK:** API 37 (Android 17)

### Cloning & Building

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/BuildVisionAI.git
   cd BuildVisionAI
   ```

2. **Build debug APK with Gradle:**
   ```bash
   ./gradlew assembleDebug
   ```

3. **Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 📚 Detailed Documentation

Further technical documentation is available in the `docs/` folder:

* 📄 [Architecture Overview](docs/01-architecture.md) — Architectural principles and package structure.
* 🗄️ [Database Schema](docs/02-database-schema.md) — Room schema, 21 tables, and version protection triggers.
* 🎨 [FloorPlanView Design](docs/03-floorplanview-design.md) — Custom canvas view, touch handling, and rendering pipeline.
* ⚙️ [Quantity & Cost Engines](docs/04-engines.md) — Formulas for material take-off and Kenya rate packs.
* 📱 [Screens & Navigation](docs/05-screens-navigation.md) — Navigation graph, screen layout, and review workflows.
* 🗺️ [Roadmap & Verification](docs/06-roadmap.md) — Verification matrix and future roadmap.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
