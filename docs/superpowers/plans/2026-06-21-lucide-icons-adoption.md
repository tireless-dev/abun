# Lucide Icons Adoption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adopt Lucide through `compose-icons` in the shared KMP UI, replace the current textual add affordance with a Lucide icon, and align shared UI documentation with the new icon source.

**Architecture:** The change stays inside `app/sharedUI` and shared docs. Lucide is added to `commonMain`, then used directly in the existing shared UI files without an app-local wrapper. Tests prove the add affordance now renders through a shared `Icon` node instead of the old text-only placeholder, and screenshot fixtures are refreshed through the existing JVM screenshot test surface.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, compose-icons Lucide, JVM Compose UI tests, Roborazzi screenshots.

---

### Task 1: Add Lucide Dependency Metadata

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/sharedUI/build.gradle.kts`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/SharedUICommonTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `shared UI source declares lucide usage for add affordances`() {
    val appSource = java.io.File(
        "src/commonMain/kotlin/dev/tireless/abun/App.kt",
    ).readText()

    kotlin.test.assertTrue(appSource.contains("Lucide"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.SharedUICommonTest.shared UI source declares lucide usage for add affordances"`
Expected: FAIL because `App.kt` does not contain `Lucide`

- [ ] **Step 3: Write minimal implementation**

```toml
[versions]
composeIcons = "<resolved version>"

[libraries]
compose-icons-lucide = { module = "br.com.devsrsouza.compose.icons:lucide", version.ref = "composeIcons" }
```

```kotlin
commonMain.dependencies {
    implementation(libs.compose.icons.lucide)
}
```

- [ ] **Step 4: Run test to verify it still fails for the intended reason**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.SharedUICommonTest.shared UI source declares lucide usage for add affordances"`
Expected: FAIL because dependency exists but `App.kt` still does not contain `Lucide`

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/sharedUI/build.gradle.kts app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/SharedUICommonTest.kt
git commit -m "build: add lucide icons dependency"
```

### Task 2: Replace Textual Add Affordances With Lucide Icons

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ComponentScreenshotTest.kt`
- Test: `app/sharedUI/src/commonTest/kotlin/dev/tireless/abun/SharedUICommonTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `shared UI source renders lucide plus icon instead of text plus`() {
    val appSource = java.io.File(
        "src/commonMain/kotlin/dev/tireless/abun/App.kt",
    ).readText()

    kotlin.test.assertTrue(appSource.contains("Icon("))
    kotlin.test.assertTrue(appSource.contains("Lucide.Plus"))
    kotlin.test.assertFalse(appSource.contains("icon = { Text(\"+\") }"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.SharedUICommonTest.shared UI source renders lucide plus icon instead of text plus"`
Expected: FAIL because `App.kt` still uses `Text("+")`

- [ ] **Step 3: Write minimal implementation**

```kotlin
import androidx.compose.material3.Icon
import compose.icons.Lucide
import compose.icons.lucide.Plus

icon = {
    Icon(
        imageVector = Lucide.Plus,
        contentDescription = null,
    )
}
```

Apply the same `Icon(Lucide.Plus)` replacement in the shared JVM screenshot scaffolds that currently use `Text("+")`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.SharedUICommonTest.shared UI source renders lucide plus icon instead of text plus"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ComponentScreenshotTest.kt app/sharedUI/src/commonTest/kotlin/dev/tireless/abun/SharedUICommonTest.kt
git commit -m "feat: adopt lucide plus icon in shared ui"
```

### Task 3: Align Shared Documentation

**Files:**
- Modify: `docs/base/shared-ui-design-system.md`
- Modify: `docs/base/technical-architecture.md`
- Modify: `docs/base/information-architecture.md`
- Test: `app/sharedUI/src/commonTest/kotlin/dev/tireless/abun/SharedUICommonTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `docs mention lucide as shared icon family`() {
    val designDoc = java.io.File("../../docs/base/shared-ui-design-system.md").readText()
    val architectureDoc = java.io.File("../../docs/base/technical-architecture.md").readText()
    val informationDoc = java.io.File("../../docs/base/information-architecture.md").readText()

    kotlin.test.assertTrue(designDoc.contains("Lucide"))
    kotlin.test.assertTrue(architectureDoc.contains("Lucide"))
    kotlin.test.assertTrue(informationDoc.contains("Lucide"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.SharedUICommonTest.docs mention lucide as shared icon family"`
Expected: FAIL because the docs do not yet mention `Lucide`

- [ ] **Step 3: Write minimal implementation**

```md
- use Lucide as the default shared icon family for app-owned iconography in `app/sharedUI`
```

Add matching one-line references in the technical architecture and information architecture docs describing Lucide as the shared icon family used by the shared KMP UI.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.SharedUICommonTest.docs mention lucide as shared icon family"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add docs/base/shared-ui-design-system.md docs/base/technical-architecture.md docs/base/information-architecture.md app/sharedUI/src/commonTest/kotlin/dev/tireless/abun/SharedUICommonTest.kt
git commit -m "docs: document lucide shared iconography"
```

### Task 4: Verify Shared UI and Desktop Surfaces

**Files:**
- Modify: `app/sharedUI/src/jvmTest/screenshots/components/component_gallery.png`
- Modify: `app/sharedUI/src/jvmTest/screenshots/components/scaffold.png`
- Modify: any other refreshed screenshot fixtures produced by the existing shared UI JVM screenshot tests

- [ ] **Step 1: Run the shared UI JVM tests**

Run: `./gradlew :app:sharedUI:jvmTest`
Expected: PASS

- [ ] **Step 2: Run the desktop and core verification suites**

Run: `./gradlew :app:desktopApp:test :core:jvmTest`
Expected: PASS

- [ ] **Step 3: Run the desktop app for visual sanity checking**

Run: `./gradlew :app:desktopApp:run`
Expected: the shared task creation FAB shows a Lucide plus icon instead of a text `+`

- [ ] **Step 4: Commit**

```bash
git add app/sharedUI/src/jvmTest/screenshots docs/base/shared-ui-design-system.md docs/base/technical-architecture.md docs/base/information-architecture.md
git commit -m "test: refresh shared ui lucide verification artifacts"
```
