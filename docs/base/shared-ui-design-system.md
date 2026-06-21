# Shared UI Design System

## Purpose

This document defines the shared editorial Material 3 language used by `app/sharedUI` across Desktop and Android.

## Design Direction

- calm over decorative
- whitespace first
- typography-led hierarchy
- neutral-first palette with muted accents
- flat outlined surfaces with minimal elevation

The goal is to keep the UI professional, breathable, and information-focused rather than colorful or ornamental.

## Iconography

- use Lucide as the default shared icon family for app-owned iconography in `app/sharedUI`
- icons should stay simple, editorial, and stroke-led rather than filled or decorative by default
- shared icons should inherit existing Material 3 content colors and normal component sizing instead of introducing feature-local icon styling

## Token Rules

### Theme Mode Support

- the app must support `System`, `Light`, and `Dark` theme modes through shared preferences
- `System` follows the platform setting
- `Light` and `Dark` force the shared color scheme across Desktop and Android
- new settings or onboarding UI must refer to these exact modes rather than inventing one-off theme toggles

### Color

- light theme anchors:
  - primary `#3F4EAE`
  - secondary `#4F8F83`
  - tertiary `#C79A52`
  - background `#FAFAF7`
  - surface `#FFFFFF`
  - outline `#D8D8D2`
  - text primary `#1A1A1A`
  - text secondary `#6B7280`
- dark theme anchors:
  - background `#111315`
  - surface `#181A1D`
  - outline `#34373B`
  - text primary `#F5F5F5`
  - text secondary `#A0A4AA`
  - primary `#B8C3FF`
- avoid pure black, pure white framing, and highly saturated feedback surfaces

### Typography

- preferred hierarchy:
  - page/display `32`
  - section title `24`
  - card title `18`
  - body `16`
  - secondary `14`
  - caption/label `12`
- allowed weights:
  - regular `400`
  - medium `500`
  - semibold `600`
- hierarchy should come from size, weight, and spacing before color or filled backgrounds

### Spacing and Shape

- use the 4dp grid
- preferred spacing values:
  - `4`
  - `8`
  - `16`
  - `24`
  - `32`
  - `48`
- primary rhythm:
  - `8` for closely related elements
  - `16` for component spacing
  - `24` for dense section groupings
  - `32` for major section separation
- radii:
  - small `8`
  - medium `12`
  - large `20`

## Surface Rules

- default content surfaces are flat, outlined cards
- prefer 1dp outlines over shadows
- keep elevation at `0-1dp`
- internal card padding should default to `24dp`
- status treatments should use muted tonal fills plus outline, not loud pills

## Shared Primitive Usage

Use the shared editorial primitives for repeated structural patterns:

- screen/page padding container
- section framing and heading rhythm
- flat outlined cards/panels
- muted status tags
- compact top-app-bar dropdown selectors when switching between screen-scoped sub-views

Use raw Material 3 controls directly for:

- buttons
- text fields
- modal sheets
- segmented controls
- app bars
- tabs/navigation

Do not create broad pass-through wrappers around standard Material 3 controls unless there is a clear cross-app behavior or styling rule that cannot be expressed through tokens and the existing primitive layer.
