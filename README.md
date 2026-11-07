# Rewitchment

Independent compatibility source for Bewitchment 1.20.1 on Minecraft 1.21.1. Natively Fabric, with NeoForge + Sinytra Connector support.

## Source layout

- `startup/src/main/java`: automatic loading, cache preparation and loader bridges.
- `runtime/src/main/java`: Minecraft compatibility helpers, networking and Mixins.
- `translator/src/main/java`: bytecode and resource conversion used by startup.
- Each module's `src/main/resources`: production metadata and required API mappings.
- `startup/src/main/slot/fabric.mod.json`: source metadata for the nested discovery slot.
- `LICENSE` and `licenses/`: project license and third-party notices.

This is a source-only snapshot. Build tooling, scripts, tests, test fixtures,
research-only entry points, logs, binaries and generated mod JARs are deliberately
excluded. This folder cannot build the release JAR on its own.

Classes named `Preview` and `TranslationLab` are retained where automatic startup
calls them as conversion stages. Those names come from the project's development
history; they contain required production conversion logic. Scratch paths such
as `test-libs` and `reports/preview` are internal to that conversion pipeline.

The exported copy removes diagnostic startup selection, the soft-Mixin switch
and GameTest-only annotation conversion. Other production implementation is
preserved. The working project and released JAR are unchanged by this export.

The discovery-slot metadata is assembled into a nested metadata-only JAR during
release packaging. Runtime/engine JAR payloads, dependency binaries and generated
Mixin refmaps are build outputs and are not included here. Dependencies include
Fabric Loader/API, Minecraft mappings, NeoForge FML SPI, ASM and Gson.

## Original content and licensing

Only independently written compatibility source is included. Original Bewitchment
source, classes, assets and data are not included. Users obtain the original mod
and dependencies separately. Local converted originals must not be redistributed.

The original Bewitchment project retains its own authorship and ARR license:
https://modrinth.com/mod/bewitchment

Rewitchment source is MIT licensed; see `LICENSE`. ASM/Gson license texts and the
release's third-party attribution notice are retained in `licenses/`; their
library source and binaries are not vendored in this folder.
