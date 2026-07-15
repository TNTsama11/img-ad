# Room schemas

Room KSP writes versioned schema JSON files to this directory during a build
using the `room.schemaLocation` argument in `app/build.gradle.kts`. The current
database version is `2`; `MIGRATION_1_2` adds `assets.available`.
