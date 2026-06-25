# `:features:browse-by-platform:api`

> Route key for the "browse by platform" Library screen.

The `:api` half of the `browse-by-platform` feature: a single parameterless
Voyager route key. As an `:api` module it carries only the type another module
needs to navigate to this screen, with no implementation and no UI dependencies.

## Contents

| File | What it is |
| --- | --- |
| `BrowseByPlatform.kt` | `@Serializable data object BrowseByPlatform` — the parameterless route key for the screen. |

## Why depend on this module

Depend on `:features:browse-by-platform:api` when you need to navigate to the
browse-by-platform screen without pulling in its implementation — the Library tab
depends on this module to route here. The app wires the actual screen by including
`:features:browse-by-platform:real`, which maps the key to a Voyager `Screen` in
`ChipboxScreens.kt`. The route takes no arguments.

## Using it

```kotlin
onEvent(NavigateTo(BrowseByPlatform))
```

## Module facts

- **Plugin:** `chipbox.plugins.feature.api`
- **Targets:** Android + JVM; JS (Node) when built with `-Pchipbox.js`
- **Source set:** `commonMain`
- **SAGE/module dependencies:** none (leaf; only `kotlinx.serialization`)
