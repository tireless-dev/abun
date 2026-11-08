# Shared UI Design System

## Purpose

This document defines the shared Swiss Precision & Restraint (B1) editorial Material 3 design system used by `app/sharedUI` across Desktop and Android.

## Design Direction: Swiss Precision & Restraint (B1)

- calm over decorative
- whitespace and grid alignment first
- typography-led hierarchy with high-contrast monospace accents
- warm stone canvas with subtle hairline borders
- dark high-contrast command surfaces (`FloatingCommandPill`)
- flat outlined surfaces with zero faux-elevation shadows

The goal is to keep the UI professional, breathable, and information-focused rather than colorful or ornamental.

## Iconography

- use Lucide as the default shared icon family for app-owned iconography in `app/sharedUI`
- icons should stay simple, editorial, and stroke-led rather than filled or decorative by default
- shared icons should inherit existing Material 3 content colors and normal component sizing instead of introducing feature-local icon styling

## Navigation Architecture

### Floating Command Pill (`FloatingCommandPill`)

The application navigation eliminates traditional persistent bottom tab bars and floating action buttons (FABs), replacing them with a unified centered bottom **Floating Command Pill**:

- **Left Section (Workspace Switcher)**:
  - Displays small colored status dots strictly for configured `AppModule`s in order (`Tasks`, `Finance`, `Notes`, `Things`).
  - Active workspace title in monospace bold typography.
  - Expansion toggle chevron (`▾` collapsed, `▴` expanded).
- **Right Section (Primary Action Button)**:
  - High-contrast pill action button contextually wired to the active workspace:
    - `Today`: `+ Capture`
    - `Tasks`: `+ 新建任务` (or `+ 新建例程` / `+ 开始专注` based on active subtab)
    - `Finance`: `+ 记一笔`
    - `Settings`: `← 返回主页`
- **Expanded Command Sheet (`FloatingCommandSheet`)**:
  - Clicking the workspace switcher opens a floating bottom sheet above the pill over a dimmed backdrop.
  - **Quick Capture row**: Instant capture buttons for `Today`, plus any enabled modules (`+ Task`, `+ Expense`, `+ Note`, `+ Thing`).
  - **Workspaces section**: 2-column bento grid for all enabled `AppModule`s showing live metrics (e.g. open tasks count, safe-to-spend balance).
  - **Footer**: Monospace link to `Settings & Preferences`.

### Top-Bar Inline 3-Segment Sub-Navigation

Sub-navigation within workspaces evolves from legacy top-app-bar dropdown selectors into inline, compact 3-segment pill selectors in the `TopAppBar`:

- **Tasks**: `任务` (Tasks) | `例程` (Routines) | `番茄` (Pomodoro) via `TaskTopBarSubtabSelector`.
- **Finance**: `总览` (Cockpit) | `明细` (Ledger) | `清算` (Clearing Hub) via `TopBarSegmentedSubnav`.
- Active segment highlights in crisp white surface with bold monospace typography; inactive segments remain subdued on track background (`ThemeTokens.colors.subnavTrack`).

## Token Rules

### Theme Mode Support

- the app must support `System`, `Light`, and `Dark` theme modes through shared preferences
- `System` follows the platform setting
- `Light` and `Dark` force the shared color scheme across Desktop and Android
- new settings or onboarding UI must refer to these exact modes rather than inventing one-off theme toggles

### Color

- Light Theme Anchors (Warm Stone / Swiss Precision):
  - Canvas: `#F4F1EA`
  - Background: `#FAFAF7`
  - Surface: `#FFFFFF`
  - Surface Elevated: `#F8F6F0`
  - Card Border: `#E5E0D8`
  - Subnav Track: `#EBE7DE`
  - Pill Surface: `#181A1D`
  - Text Primary: `#111315`
  - Text Secondary: `#5C6470`
- Dark Theme Anchors:
  - Canvas: `#161719`
  - Background: `#111315`
  - Surface: `#1A1C1E`
  - Surface Elevated: `#22252A`
  - Card Border: `#282A2E`
  - Subnav Track: `#22252A`
  - Pill Surface: `#181A1D`
  - Text Primary: `#EDEDED`
  - Text Secondary: `#8A919E`
- Module Accents:
  - Tasks Accent: `#4F46E5` (Indigo)
  - Finance Accent: `#059669` / `#10B981` (Emerald)
  - Notes Accent: `#D97706` (Amber)
  - Things Accent: `#7C3AED` (Purple)

### Typography

- Preferred hierarchy:
  - Page/Display: `32sp`
  - Section title: `24sp`
  - Card title: `18sp`
  - Body: `14sp` - `16sp`
  - Secondary / Caption: `12sp`
  - Monospace Labels & Overlines: `10sp` - `11sp` (tracked letter-spacing `1sp`)
- Hierarchy should come from size, weight, and spacing before color or filled backgrounds.

### Spacing and Shape

- Use the 4dp grid
- Preferred spacing values: `4`, `8`, `12`, `16`, `24`, `32`
- Radii:
  - Small / Tags / Subnav items: `6dp` - `8dp`
  - Medium / Bento Cards: `12dp` - `16dp`
  - Large / Command Sheet: `20dp`
  - Full Pill / Capsule: `999dp`

## Swiss Precision Shared Primitives

Use the shared primitives in `dev.tireless.abun.ui.*` for repeatable layout components:

- `SwissBentoCard`: Flat outlined container card with hairline border (`cardBorder`) and clean padding on warm stone canvas.
- `SwissCardHeader`: Monospace uppercase header with colored accent dot and trailing controls.
- `SwissHairlineDivider`: Subtle 1dp divider (`cardBorder`).
- `SwissMetricStrip`: 3-column hairline metric strip with bold numeric figures and monospace uppercase labels.
- `SegmentedTickBar`: Discrete progress indicator with customizable segment count and fill colors.
- `TopBarSegmentedSubnav`: Generic segmented pill container for top app bar sub-navigation.
- `FloatingCommandPill`: Floating capsule navigation and contextual primary action trigger.
- `FloatingCommandSheet`: Expanded workspace switcher and quick capture overlay.

Use raw Material 3 controls directly for:
- Buttons
- Text fields
- Modal bottom sheets
- Segmented controls
- App bars

## Visual Verification & Snapshot Inspection

To inspect the Swiss Precision B1 layout visually without UI regressions or git repository pollution:

- Run the on-demand snapshot dump workflow:
  ```bash
  ./gradlew :app:sharedUI:dumpSnapshots
  ```
- All core application screens are rendered at `390.dp x 844.dp` and dumped directly into the git-ignored `app/sharedUI/build/snapshots/` directory:
  - `today.png` (Dashboard)
  - `command_sheet_expanded.png` (Dashboard with expanded `FloatingCommandSheet`)
  - `tasks_populated.png` (Tasks subtab)
  - `pomodoro_active.png` (Pomodoro subtab with active session)
  - `finance_cockpit.png` (Finance Cockpit subtab)
  - `finance_ledger.png` (Finance Ledger subtab)
  - `finance_clearing.png` (Finance Clearing Hub subtab)
  - `settings.png` (Settings screen)
- The default test task (`./gradlew :app:sharedUI:jvmTest`) deliberately excludes `SnapshotDumpTest` to keep standard unit test runs fast and avoid generating artifacts unless explicitly invoked.
