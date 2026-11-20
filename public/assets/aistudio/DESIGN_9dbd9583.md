---
name: Zen Dark Minimalist
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353534'
  on-surface: '#e5e2e1'
  on-surface-variant: '#c3c9b2'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#8d937e'
  outline-variant: '#434938'
  surface-tint: '#a4d64c'
  primary: '#fefff1'
  on-primary: '#233600'
  primary-container: '#bef264'
  on-primary-container: '#4b6e00'
  inverse-primary: '#476800'
  secondary: '#7bd0ff'
  on-secondary: '#00354a'
  secondary-container: '#00a6e0'
  on-secondary-container: '#00374d'
  tertiary: '#fffdff'
  on-tertiary: '#490081'
  tertiary-container: '#efd9ff'
  on-tertiary-container: '#8046ba'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#bff365'
  primary-fixed-dim: '#a4d64c'
  on-primary-fixed: '#131f00'
  on-primary-fixed-variant: '#354e00'
  secondary-fixed: '#c4e7ff'
  secondary-fixed-dim: '#7bd0ff'
  on-secondary-fixed: '#001e2c'
  on-secondary-fixed-variant: '#004c69'
  tertiary-fixed: '#f0dbff'
  tertiary-fixed-dim: '#ddb8ff'
  on-tertiary-fixed: '#2c0051'
  on-tertiary-fixed-variant: '#62259b'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-sm:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  title-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 14px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system delivers an ultra-focused, high-utility dark aesthetic tailored for collegiate daily workflows. Built around an offline-first mobile product architecture, the visual environment emphasizes low eye-fatigue, ultra-fast glanceability, and tactile feedback.

The aesthetic fuses Material Design 3 ergonomics with a sleek cyber-minimalism:
- **Atmosphere:** Deep OLED blacks and charcoal containers eliminate extraneous visual noise, directing cognitive focus exclusively to deadlines, attendance metrics, and schedules.
- **Accent Philosophy:** High-voltage neon lime acts as the singular primary driver for interactive states, floating action buttons, and active route markers.
- **Semantic Differentiation:** Precise secondary cues (amber for attendance caution/holidays, cyan for lectures/theory, and soft violet for laboratory sessions) establish instant cognitive parsing across recurring weekly routines.
- **Personality:** Decisive, disciplined, razor-sharp, and distraction-free.

## Colors

The palette uses strict tonal layers optimized for dark-mode screens, ensuring high contrast without harsh glare.

### Primary & Accent
- **Primary / Active Accent (`#BEF264` / `#A3E635`):** The primary signal color. Used for active navigation pills, full-state segment switchers, key floating action buttons (FAB), and attendance progress bars. Content on primary uses deep charcoal `#0D1505` to guarantee AAA legibility.
- **Surface Tint / Hover State (`#B2FF59`):** Bright peak lime reserved for momentary press states, active ripple feedback, and high-priority status counters.

### Semantic Tones
- **Theory / Lecture (`#38BDF8`):** Muted cyan for academic lecture modules, theory chip badges, and time indicators.
- **Lab / Practical (`#C084FC`):** Soft violet for laboratory experiments, code labs, and hands-on session chips.
- **Holiday / Warning / Caution (`#F59E0B`):** Warm amber for marked holidays, attendance warnings falling below institutional thresholds (e.g., <75%), and focused card borders.

### Surfaces & Backgrounds
- **Background Canvas (`#121212`):** Primary canvas tone providing deep OLED black depth.
- **Surface Container Low (`#18181B`):** Base layer for inactive cards, segmented tab bars, and search surfaces.
- **Surface Container High (`#1F2220`):** Elevated class/attendance cards, dialogs, and persistent bottom navigation bars.
- **Outline / Border (`#27272A` / `#3F3F46`):** Low-contrast structural perimeter strokes (1px) defining card geometry and inactive button outlines.

## Typography

Typography relies on a crisp grotesque hierarchy using `Inter`. Monospaced or tabular numerical features (`tnum`) should be enabled for time ranges, dates, and attendance percentage scores to avoid layout shifting.

- **Uppercase Overlines (`label-sm`):** Rendered with a tracking of `+0.08em` for section headers (e.g., `ENROLLED SUBJECTS`, `ACTIVE TASKS`, `ATTENDANCE TRACKER`) in muted zinc (`#71717A`).
- **Headlines (`headline-sm`, `title-md`):** High contrast white (`#F4F4F5`) ensuring zero strain in high-glare or late-night study sessions.
- **Subtext & Metadata (`body-sm`):** Medium-contrast silver (`#A1A1AA`) for classroom room numbers, credit hours, and timestamps.

## Layout & Spacing

The layout is grounded in a baseline 8dp grid system with 4dp sub-increments for compact chip padding and tight inline badges.

- **Canvas Margins:** Mobile viewport maintains an edge padding of `16px` (`margin`), maximizing screen real estate while guarding against false edge gestures.
- **Card Stacking Rhythm:** Primary list items and class blocks maintain a strict vertical inter-element gap of `12px` to `16px` (`space-md`), creating discernible chunking without consuming excessive vertical height.
- **Component Touch Bounds:** Interactive elements (attendance toggle buttons, checkboxes, bottom navigation pills, action icons) strictly enforce a minimum interactive target of `48px × 48px`.
- **Adaptive Layout:** On mobile portrait viewports, the interface behaves as a single vertical feed. For tablet and foldable devices (>600dp), a dual-pane master-detail pattern activates, docking attendance metrics on the left pane and today's schedule/tasks on the right pane.

## Elevation & Depth

Depth is conveyed through dark tonal layering supplemented by crisp hairline borders rather than heavy drop shadows:

- **Surface Tiers:**
  - **Level 0 (Canvas):** Pure base (`#121212`).
  - **Level 1 (Card Containers):** Raised to `#18181B` with a subtle 1px border (`#27272A`).
  - **Level 2 (Active Cards & Floating Bar):** `#1F2220` with optional highlight borders (e.g., `#F59E0B` for marked holiday cards or `#BEF264` for current ongoing sessions).
  - **Level 3 (Modals & Bottom Sheets):** `#242724` with an ambient glow blur (`box-shadow: 0 16px 32px rgba(0, 0, 0, 0.6)`).
- **Floating Action Button (FAB):** Employs an ambient neon backlight halo (`box-shadow: 0 8px 24px rgba(190, 242, 100, 0.25)`), making it the undisputed focal anchor for creating quick entries.

## Shapes

The design system adopts Material Design 3's expressive roundedness (`roundedness: 3`):

- **Pill Geometry (`9999px` / `rounded-full`):** Universally applied to active tab indicators, primary action buttons, filter tags, and segmented navigation bars.
- **Large Components (`rounded-3xl` / `24px`):** Applied to class cards, attendance cards, floating navigation containers, and search bars.
- **FAB Architecture (`rounded-2xl` / `16px` or `rounded-3xl` / `24px`): Squircle geometry creating a tactile stamp in the bottom-right corner.
- **Badges & Chips (`rounded-lg` / `8px` to `12px`):** Applied to theory, lab, and day badges for a clean, modular appearance.

## Components

### Segmented Tabs & Navigation
- **Navigation Bar:** Elevated dock situated above system navigation buttons, featuring an outer shell (`#18181B`) housing pill tabs (`rounded-full`). The active item transitions into a full neon lime pill (`#BEF264`) with dark contrasting glyph and text (`#0D1505`). Inactive destinations display muted silver icons and labels (`#A1A1AA`).
- **Horizontal Time/Category Filter:** Flat, scrollable row of pills with 1px border outlines (`#27272A`). Selected filter fills with `#BEF264` or bordered stroke highlighting.

### Class & Attendance Cards
- **Container:** `rounded-3xl` container (`#18181B` or `#1F2220`) bordered with a precise 1px outline. 
- **Active State Highlights:** Cards with marked exceptions (e.g., Holiday, Attendance Caution) illuminate a complete 1.5px border in semantic amber (`#F59E0B`).
- **Attendance Progress Bar:** Sleek 4px track (`#27272A`) filled by a neon lime (`#BEF264`) track indicator reflecting current percentage.
- **Action Triggers:** Three-button split segmented bar (`Present`, `Absent`, `Holiday`) embedded directly inside the card footer. Marked state receives solid outline and tone fill.

### Tags & Chips
- **Theory Badge:** Pill chip with background `rgba(56, 189, 248, 0.12)`, text `#38BDF8`, zero border.
- **Lab Badge:** Pill chip with background `rgba(192, 132, 252, 0.12)`, text `#C084FC`, zero border.
- **Day / Count Badges:** Compact pills with dark green tint (`rgba(163, 230, 53, 0.15)`) and vibrant lime label (`#A3E635`).

### Floating Action Button (FAB)
- Prominent `56px × 56px` or `64px × 64px` rounded squircle (`rounded-2xl` or `rounded-3xl`) filled with `#BEF264` and centered `#121212` icon. Floating position placed 16px from right and 80px from bottom screen edge.

### Inputs & Task Items
- **Search Bar:** Pill-shaped (`rounded-full`) field with surface color `#18181B`, placeholder text in `#71717A`, and leading icon.
- **Checklist Item:** Deep container card with an interactive rounded square checkbox (`20px × 20px`, 2px border `#3F3F46`). When marked, checkbox fills with `#BEF264` and task title renders strikethrough with muted color `#71717A`.