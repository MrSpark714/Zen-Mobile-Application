<h1 align="center">ZEN</h1>

<p align="center">
  <strong>A minimalist, offline-first productivity workspace for Android.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Java-007396?style=flat-square&logo=java&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Database-Room-039BE5?style=flat-square&logo=sqlite&logoColor=white" alt="Room" />
  <img src="https://img.shields.io/badge/Min_SDK-24-blue?style=flat-square" alt="Min SDK 24" />
</p>

---

ZEN replaces disconnected planners by combining rich-text notes, scheduled tasks, and a university attendance tracker into a single, distraction-free application. Built strictly for offline use, it prioritizes speed, local data ownership, and a consistent dark-theme aesthetic.

## Architecture & Tech Stack

*   **Language:** Java & XML
*   **Minimum SDK:** Android 7.0 (API 24)
*   **Database:** Room Persistence Library (SQLite)
*   **UI Framework:** Material Design 3 (MaterialTimePicker, BottomSheetDialog, CoordinatorLayout)
*   **Background Tasks:** `AlarmManager` and `BroadcastReceiver` for precise, exact-time local notifications (Android 12+ compliant).

## Core Modules

### 1. Notes Workspace
*   **Rich Text Editor:** Apply inline formatting, color highlights, and priority tags directly within the editor interface.
*   **Target Dates:** Attach specific deadlines to notes, rendering a timeline badge in the list view.
*   **Local Storage:** All data is written asynchronously to the local Room database via background thread executors.

### 2. Task Management
*   **Time Windows:** Set explicit start times, end times, and calculated durations for time-blocked work sessions.
*   **Exact Alarms:** Utilizes `SCHEDULE_EXACT_ALARM` to trigger high-priority status bar notifications exactly when a task target time is reached.

### 3. Attendance Tracker
*   **Timetable Configuration:** Build a recurring Monday–Friday schedule capturing subject names, room numbers, class types (Theory/Lab), and credit hours.
*   **Advance Alerts:** Automatically triggers a local push notification exactly 5 minutes before any scheduled class begins.
*   **Weighted Analytics:** Calculates attendance percentages using a relational SQL query weighted by credit hours. "Holiday" statuses are recorded in the history log but mathematically excluded from the final percentage denominator.

## Installation & Build Instructions

1.  Clone the repository:
    ```bash
    git clone https://github.com/MrSpark714/Zen-Mobile-Application.git
    ```
2.  Open the directory in **Android Studio**.
3.  Allow Gradle to sync all dependencies.
4.  Ensure you have a physical device or emulator running API 24 or higher.
5.  Click **Run** (`Shift + F10`) to build and deploy the APK.

## License

Distributed under the MIT License. See `LICENSE` for more information.
