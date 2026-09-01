<h1 align="center">
  <img src="assets/.aistudio/logo_2.png" width="120" height="120" alt="ZEN App Icon">
  <br>
  ZEN
</h1>

<p align="center">
  <strong>A minimalist, offline-first productivity workspace for Android.</strong>
</p>

<p align="center">
  <a href="https://zenwebsite-teal.vercel.app/#hero"><strong>Visit the Official ZEN Website</strong></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Java-007396?style=flat-square&logo=java&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Database-Room-039BE5?style=flat-square&logo=sqlite&logoColor=white" alt="Room" />
  <img src="https://img.shields.io/badge/Min_SDK-24-blue?style=flat-square" alt="Min SDK 24" />
</p>

---

ZEN replaces disconnected planners by combining rich-text notes, scheduled tasks, and a university attendance tracker into a single, distraction-free application. Built strictly for offline use, it prioritizes speed, local data ownership, and a highly customizable dark-theme aesthetic.

## Core Modules

### 1. Notes Workspace
*   **Rich Text Editor:** Apply inline formatting, color highlights, and priority tags directly within the editor interface.
*   **Target Dates:** Attach specific deadlines to notes, rendering a timeline badge in the list view.
*   **Local Storage:** All data is written asynchronously to the local Room database via background thread executors.

### 2. Task Management
*   **Time Windows:** Set explicit start times, end times, and calculated durations for time-blocked work sessions.
*   **Exact Alarms:** Utilizes `SCHEDULE_EXACT_ALARM` to trigger high-priority status bar notifications exactly when a task target time is reached.

### 3. Smart Attendance Tracker
*   **Timetable Configuration:** Build a recurring Monday–Friday schedule capturing subject names, room numbers, class types (Theory/Lab), and credit hours.
*   **Advance Alerts:** Automatically triggers a local push notification exactly 5 minutes before any scheduled class begins.
*   **Weighted Analytics:** Calculates attendance percentages using a relational SQL query weighted by credit hours. "Holiday" statuses are mathematically excluded from the final percentage denominator but retained in the history logs.

> **Note on Multi-Day Subjects:**  
> If a subject has lectures of varying credit hours across the week (e.g., a 2-credit lecture on Monday and a 1-credit lab on Wednesday), create two separate schedule entries using the **exact same Subject Name**. The Analytics Engine will automatically merge them into a single cumulative attendance percentage.

### 4. Backup, Sync & Cohort Sharing
*   **Granular Exports:** Export your entire database, just notes, or just attendance logs as serialized JSON files via the Android Storage Access Framework (SAF).
*   **Peer-to-Peer Sharing:** Export your week's class timetable as a specific configuration file and share it directly to cohort WhatsApp groups or emails via `Intent.ACTION_SEND`.
*   **Seamless Import:** Classmates can import shared configuration files to instantly populate their local timetables.

### 5. Personalization
*   **Dynamic Accent Colors:** Customize the entire application's active state elements (Navigation, Floating Action Buttons, Chips, and Progress Bars) using a built-in Settings module. Choose from high-contrast presets (Mint, Neon Teal, Cyan Blue, Sage Green, Electric Lime) that persist via `SharedPreferences`.

## Architecture & Tech Stack

*   **Language:** Java & XML
*   **Minimum SDK:** Android 7.0 (API 24)
*   **Database:** Room Persistence Library (SQLite)
*   **Serialization:** Google Gson
*   **UI Framework:** Material Design 3 (MaterialTimePicker, BottomSheetDialog, CoordinatorLayout)
*   **Background Tasks:** `AlarmManager` and `BroadcastReceiver` for precise, exact-time local notifications (Android 12+ compliant).

## Installation & Build Instructions

1.  Clone the repository:
    ```bash
    git clone https://github.com/MrSpark714/Zen-Mobile-Application.git
    ```
2.  Open the directory in **Android Studio**.
3.  Allow Gradle to sync all dependencies.
4.  Ensure you have a physical device or emulator running API 24 or higher.
5.  Click **Run** (`Shift + F10`) to build and deploy the APK.

## Development Status & Feedback

**Status: Active Beta**  
ZEN is in continuous development. While the core modules (Notes, Tasks, and Attendance Tracker) are fully operational, you may encounter bugs, unpolished edge cases, or incomplete UI elements during this phase.

If you experience unexpected behavior or have suggestions for improvement, please report them using one of the following methods:
*   Submit a report directly through the **Feedback Form** located at the bottom of the [Official ZEN Website](https://zenwebsite-teal.vercel.app/#hero).
*   Open a detailed ticket in the **Issues** section of this GitHub repository.

## License

Distributed under the MIT License. See `LICENSE` for more information.
