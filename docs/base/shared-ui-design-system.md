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

### Top-Bar Inline 4-Segment Sub-Navigation

Sub-navigation within workspaces evolves from legacy top-app-bar dropdown selectors into inline, compact 4-segment pill selectors in the `TopAppBar`:

- **Tasks**: `Tasks` (`任务`) | `Routines` (`例程`) | `Pomodoro` (`番茄`) | `Settings` (`设置`) via `TaskTopBarSubtabSelector`.
- **Finance**: `Cockpit` (`总览`) | `Ledger` (`明细`) | `Clearing` (`清算`) | `Settings` (`设置`) via `FinanceTopBarSubnav`.
- Active segment highlights in crisp white surface with bold monospace typography; inactive segments remain subdued on track background (`ThemeTokens.colors.subnavTrack`).

### Workspace-Scoped Settings Architecture

To preserve clean functional boundaries, settings are partitioned into module-owned workspaces versus global system configuration:

1. **Global Application Settings (`SettingsScreen`)**:
   - Focuses strictly on cross-cutting application configuration.
   - **Modules Bento**: Enable/disable optional app modules (`Tasks`, `Finance`, `Notes`, `Things`).
   - **Cloud & Sync Bento**: Sync daemon status, manual sync triggers, and sync timeline.
   - **Appearance & Language Bento**: Single-choice segmented controls for Theme (`System`, `Light`, `Dark`) and Language hot-switching (`System`, `En`, `SC`, `TC`).
   - **General Preferences Bento**: Date format choice, timezone override, and day rollover boundary time.
   - **Account & Session Bento**: Current authenticated identity, login actions, and logout button.
   - **Software Updates Bento**: OTA distribution client version, release channel, auto-update toggle, and manual check button.
   - **Typography & Precision**: Clean Swiss bento card architecture without redundant duplicate inner headers (removed oversized 24sp headers and redundant `SectionHeader`s), with standardized `12dp` to `14dp` vertical field spacing.

2. **Task Workspace Settings (`TaskSettingsScreen`)**:
   - Embedded directly as the 4th sub-surface of the Tasks workspace (`TaskSubTab.SETTINGS`).
   - **Task Defaults Bento (`settings-panel-task-defaults`)**: Default title prefix and alarm lead minutes.
   - **Focus Defaults Bento (`settings-panel-focus-defaults`)**: Focus work session, short break, and long break duration controls.

3. **Finance Workspace Settings (`FinanceSettingsScreen`)**:
   - Embedded directly as the 4th sub-surface of the Finance workspace (`FinanceTopBarSubnav` -> Settings).
   - **Account Presets Bento (`settings-panel-finance-presets`)**: Load standard starter predefined accounts for pristine ledgers, or reset all ledger accounts back to Default 5 roots.
   - **Full Account Hierarchy**: Tree-based inspection, business role configuration (Liquid Mainstay, Growth Asset, Pass-Through Bridge), commercial vs. personal debt classifications, and Auto-Pad reconciliation.

## Token Rules

### Theme Mode Support

- the app must support `System`, `Light`, and `Dark` theme modes through shared preferences
- `System` follows the platform setting
- `Light` and `Dark` force the shared color scheme across Desktop and Android
- new settings or onboarding UI must refer to these exact modes rather than inventing one-off theme toggles

### Internationalization (i18n) & Single-Language Content

- **Strict Single-Language Rule**:
  - Eliminate all bilingual compound labels (e.g. `English · 中文` or `English / 中文`).
  - All UI strings throughout all screens must be sourced through `ThemeTokens.strings` (backed by `LocalAppStrings`).
- **Supported Languages**:
  - English (`AppLanguage.EN`)
  - Simplified Chinese (`AppLanguage.SC` / 简体中文)
  - Traditional Chinese (`AppLanguage.TC` / 繁體中文)
- **Language Preference & Hot-Switching**:
  - `LanguagePreference`: `SYSTEM`, `EN`, `SC`, `TC`.
  - Stored in `LoginPreferenceStore` (`languagePreference()` / `setLanguagePreference(...)`) across Desktop (`Preferences`), Android (`SharedPreferences`), and iOS (`NSUserDefaults`).
  - In Settings, exposed via an inline 4-segment choice: `System`, `En`, `SC`, `TC` (when active language is English) or `系统`, `英文`, `简中`, `繁中` (Simplified Chinese) or `系統`, `英文`, `簡中`, `繁中` (Traditional Chinese).
  - Hot-switching immediately updates `LocalAppStrings` and `LocalAppLanguage` in `AppTheme`, reactively re-rendering the entire Compose hierarchy without application restart.

### Color

- **WCAG 2.1 AA Contrast Standard**:
  - All text colors (`textPrimary`, `textSecondary`, `textTertiary`) must achieve a contrast ratio of at least **4.5:1** against `surface`, `background`, `surfaceElevated`, and `surfaceMuted` in both Light and Dark themes.
  - Verified and guarded against regressions via automated mathematical tests in `ColorContrastTest.kt`.
- Light Theme Anchors (Warm Stone / Swiss Precision):
  - Canvas: `#F7F7F4`
  - Background: `#FAFAF7`
  - Surface: `#FFFFFF`
  - Surface Elevated: `#F7F6F1`
  - Surface Muted: `#F2F1EB`
  - Card Border: `#E2E8F0`
  - Subnav Track: `#EAE9E3`
  - Pill Surface: `#111315`
  - Text Primary: `#0F172A` (Slate 900, 17.5:1 contrast)
  - Text Secondary: `#475569` (Slate 600, 7.5:1 contrast)
  - Text Tertiary: `#5B6B82` (Slate 550, 5.4:1 contrast, strictly >= 4.5:1)
- Dark Theme Anchors:
  - Canvas: `#111315`
  - Background: `#111315`
  - Surface: `#181A1D`
  - Surface Elevated: `#202327`
  - Surface Muted: `#26292E`
  - Card Border: `#282C32`
  - Subnav Track: `#1E2227`
  - Pill Surface: `#22262C`
  - Text Primary: `#F8FAFC` (Slate 50, 14.0:1 contrast)
  - Text Secondary: `#CBD5E1` (Slate 300, 9.8:1 contrast)
  - Text Tertiary: `#94A3B8` (Slate 400, 6.8:1 contrast on surface, strictly >= 4.5:1)
- Semantic Status & Role Tokens (Never hardcode ad-hoc badge colors):
  - Pass-Through / Warning: `passThroughSoft` + `passThroughAccent` (Light: `#FEF3C7` / `#B45309`, Dark: `#382914` / `#FBBF24`)
  - Status Cleared: `statusClearedSoft` + `statusClearedAccent` (Light: `#F2F1EB` / `#5B6B82`, Dark: `#26292E` / `#94A3B8`)
  - Status Draft: `statusDraftSoft` + `statusDraftAccent`
  - Status Scheduled: `statusScheduledSoft` + `statusScheduledAccent` (Light: `#EFF6FF` / `#1D4ED8`, Dark: `#172554` / `#93C5FD`)
- Module Accents:
  - Tasks Accent: `#7B8CE8` (Dark) / `#3B4CCA` (Light)
  - Finance Accent: `#4DB89E` (Dark) / `#0F766E` (Light)
  - Notes Accent: `#FBBF24` (Dark) / `#B45309` (Light)
  - Things Accent: `#C97A96` (Dark) / `#8F4762` (Light)

### Bottom Action Pill Overlay & Toast Clearance

- The centered `FloatingCommandPill` sits at `Alignment.BottomCenter` with `.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))` and `padding(bottom = 24.dp)` to guarantee ample clearance above the mobile system gesture navigation bar (home indicator) as well as the bottom window edge on desktop.
- Any floating banner, toast, or action prompt (such as `OtaToastCard` or `FloatingCommandSheet`) anchored at `BottomCenter` applies `.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))` and `.padding(bottom = 88.dp)` (or higher) to sit cleanly above the pill with zero visual overlap.

### Screen Top Bar Conventions

- **Today / Dashboard**:
  - Deliberately omits the `TopAppBar`. Redundant top chrome (duplicate date, `DASHBOARD` label, `LOCAL-FIRST` tag, and redundant settings icon) is removed.
  - The page body begins cleanly with the daily quote and an enlarged, prominent date header (`20sp` monospace bold) paired with localized date-stepping buttons (`Previous` / `Today` / `Next`).
- **Feature Workspaces (Tasks, Finance, Settings)**:
  - Retain focused `TopAppBar` hosting inline segmented sub-navigation or back navigation.

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
