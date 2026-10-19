# Shape Items Model Browser

Fabric **Minecraft Java 26.3** client mod for browsing rename-controlled custom item models in the anvil.

## The supplied resource pack

The inspected `shape_s_food_pack.zip` contains **1,192 entries** in `assets/minecraft/items/apple.json`, each selected by `minecraft:component` / `minecraft:custom_name`. All entries use `anvilmodels:item/*` models. **Put an apple in the first anvil slot** to browse its models.

## Browser

* Adds a `Models` toggle beside the anvil. The browser appears **to the left** of the regular anvil.
* Reads model names from the active, loaded resource pack and builds collapsible name-prefix groups.
* Live text search, 7-row pages and pagination controls.
* Clicking an entry fills the vanilla anvil's name input, preserving ordinary anvil output logic.
* The mod is client-side and does not create or change model resources; the resource pack is still required.

**Implementation status:** initial untested source implementation. The catalogue currently supports `minecraft:select` branches which inspect the `minecraft:custom_name` component. It reads the *top resolved* item definition, and refreshes the catalogue when opening a fresh game process (resource reload after initial caching currently requires restarting). Model-specific 3D thumbnails are **not yet implemented**; the list is textual. The build has not been compiled or tested in Minecraft, so 26.3 API adjustments might be necessary.

## Build

Use **JDK 21**, **Gradle 9.6**, Fabric Loom 1.17 and loader 0.19.5. With Gradle installed run:

```sh
gradle build
```

The built jar is placed in `build/libs/`. Install it into the client `mods/` folder and install/enable the original resource pack separately. Fabric Loader is required. Server installation is not necessary because this only operates the client UI and submits rename text to the normal anvil.

## Improvement roadmap

1. Test and fix API incompatibilities against 26.3.
2. Draw in-panel custom 3D previews, using the renamed base item for the selected model.
3. Persist expanded groups and add user-defined group rules.
4. Listen to resource reloads for catalogue invalidation.
5. Improve clipping and focus handling for small window sizes, large UI scales and other modded screens.