# Onboarding Screen: Welcome to the New ZEN!

Transform the first-launch migration popup alert dialog into a dedicated, high-polish full-screen onboarding experience (`OnboardingActivity`) featuring ZEN's brand logo, bold typography, explanatory context, and stacked primary/secondary actions.

## User Review & Critical Decisions

> [!IMPORTANT]
> The architectural and design decisions below incorporate your confirmed choices from the planning questionnaire.

- **Confirmed Decision 1 (Navigation Architecture)**: Dedicated `OnboardingActivity` as the first-launch entry gateway that conditionally intercepts first-time launches and seamlessly routes to `MainActivity` (or `BackupActivity` on top of `MainActivity`).
- **Confirmed Decision 2 (Button Arrangement)**: Stacked action buttons at the bottom — high-contrast filled primary button ("Import Data") placed directly above the refined outlined button ("Start Fresh").
- **Confirmed Decision 3 (Visual Density)**: Minimalist layout focused strictly on ZEN's app logo, welcome heading, copy paragraph, and action buttons without unnecessary visual clutter or extra badge elements.

---

## 1. Overview & Core Concept

- **What It Does**: Replaces the disruptive `MaterialAlertDialogBuilder` dialog on first launch with an immersive, full-screen onboarding screen that greets users, explains the v9.3 database architecture upgrade, and guides them either to restore their data via Smart Import or to begin with a fresh workspace.
- **Target Audience / Persona**: Existing users migrating from previous builds or brand new users opening the ZEN productivity suite for the first time.
- **Key Value**: First impressions define app quality. Replacing an abrupt system dialog with a branded, edge-to-edge welcome screen provides a reassuring, calm, and distraction-free entry experience fitting ZEN's minimalist aesthetic.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **First-Launch Detection**:
   - User opens the app. `MainActivity` checks migration preferences (`is_first_launch_v9_3_migration`).
   - If `true`, the app directs immediately to `OnboardingActivity` with smooth transition.
2. **Onboarding Presentation**:
   - The user sees a centered ZEN brand emblem, followed by "Welcome to the New ZEN!" and the migration description paragraph.
   - At the bottom of the screen, the user is presented with two clear choices.
3. **Action Pathways**:
   - **Path A: "Import Data"**: Sets the first-launch flags to `false`, launches `MainActivity` with `BackupActivity` immediately on top, allowing users to select and restore their `.json`/`.zen` backup, and returns directly to the populated dashboard upon completion.
   - **Path B: "Start Fresh"**: Sets first-launch flags to `false`, transitions directly to `MainActivity` dashboard ready for note-taking, task management, and attendance tracking.

### Visual Identity & Theme

- **Aesthetic Direction**: Minimalist, zen-focused dark theme with intentional spatial breathing room and high visual contrast.
- **Color Palette**:
  - Background: Deep Obsidian (`#121212`, `@color/zen_background`)
  - Primary Action: Vibrant Neon Mint/Lime (`#A7FF67`, `@color/zen_accent`) with dark on-accent text (`#121212`)
  - Secondary Action: Translucent dark surface with crisp border stroke (`#27272A`, `@color/zen_border`) and off-white text (`#E0E0E0`, `@color/zen_text_primary`)
  - Text Hierarchy: Primary title (`#E0E0E0`), Body paragraph (`#A1A1AA` / `#71717A`)
- **Typography & Hierarchy**:
  - Title: `28sp`, Bold (`sans-serif-black` or `sans-serif-medium`), `includeFontPadding="false"`, centered, with 12dp top margin from logo.
  - Body: `15sp`, Regular (`sans-serif`), line spacing multiplier `1.3`, `textColor="@color/zen_text_secondary"`, max width constrained for optimal reading rhythm.
- **Component Styling & Layout**:
  - Edge-to-edge padding respecting system navigation and status bar insets.
  - Logo centered in a subtle rounded surface container with smooth elevation and gentle rim highlight.
  - Buttons have a minimum height of `54dp` for generous touch targets (>48dp) and 16dp corner radius.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Activity Separation vs. View Overlay**
  - *Chosen Approach*: Dedicated `OnboardingActivity` registered in `AndroidManifest.xml`.
  - *Why*: Eliminates complex overlay state handling in `MainActivity`, guarantees clean memory cleanup when dismissed, and ensures back-press cannot accidentally expose an uninitialized dashboard before choice confirmation.
  - *Alternatives Considered*: DialogFragment or FrameLayout overlay inside `MainActivity` — rejected due to lifecycle clutter and potential backstack leaks.

- **Decision 2: Migration Flag Storage**
  - *Chosen Approach*: Maintain exact preference keys (`is_first_launch_v9_3_migration` across `zen_prefs` and default preferences).
  - *Why*: Full backward compatibility with the existing architecture without risking migration state divergence or repeat prompts.

- **Decision 3: "Import Data" Backstack Handling**
  - *Chosen Approach*: `startActivities(new Intent[]{mainIntent, backupIntent})` followed by `finish()`.
  - *Why*: When the user finishes or cancels their backup import in `BackupActivity`, pressing back takes them straight to the initialized `MainActivity`, creating a natural and predictable navigation loop.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    App Launch Lifecycle                      │
└──────────────────────────────┬───────────────────────────────┘
                               │
                Is First Launch / Migration?
                               │
            ┌──────────────────┴──────────────────┐
            ▼ Yes                                 ▼ No
┌───────────────────────────────┐   ┌───────────────────────────┐
│      OnboardingActivity       │   │       MainActivity        │
│   (activity_onboarding.xml)   │   │     (Full Dashboard)      │
└───────────────┬───────────────┘   └───────────────────────────┘
                │
     User Selection at Bottom
                │
     ┌──────────┴──────────┐
     ▼                     ▼
[Import Data]        [Start Fresh]
     │                     │
     │ Update Prefs        │ Update Prefs
     │ Flag = false        │ Flag = false
     │                     │
     ▼                     ▼
┌─────────────────────────┐│
│   MainActivity (Base)   ││
│           +             ││
│ BackupActivity (Top)    ││
└─────────────────────────┘│
                           ▼
              ┌─────────────────────────┐
              │      MainActivity       │
              └─────────────────────────┘
```

### Data Model & State

- Key SharedPreferences keys:
  - File: `zen_prefs` & `PreferenceManager.getDefaultSharedPreferences`
  - Key: `is_first_launch_v9_3_migration` (`boolean`)
  - Initial value: `true` (prompts onboarding)
  - Post-interaction value: `false` (never prompts again)

### Interactive Component & State Mapping

| UI Component | ID | Action / Interaction | Outcome |
| :--- | :--- | :--- | :--- |
| **App Logo** | `iv_onboarding_logo` | Static decorative | Displays ZEN emblem with padding & rim |
| **Welcome Heading** | `tv_onboarding_title` | Static typography | "Welcome to the New ZEN!" |
| **Description** | `tv_onboarding_desc` | Static typography | Upgraded database architecture explanation |
| **Import Button** | `btn_onboarding_import` | Click | Flips prefs to `false`, launches `MainActivity` + `BackupActivity`, finishes `OnboardingActivity` |
| **Start Fresh Button** | `btn_onboarding_fresh` | Click | Flips prefs to `false`, launches `MainActivity`, finishes `OnboardingActivity` |
