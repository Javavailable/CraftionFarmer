# CraftionSpawner API provenance

- Repository: `Javavailable/CraftionSpawner`
- Commit: `f916c163abce80e7a78c97fe26eb680d501534b6`
- Upstream version: `1.7.0.1-craftion.5`
- Upstream module: `api`
- Upstream task: `./gradlew :api:clean :api:jar --no-daemon --stacktrace`
- Maven coordinates: `io.github.javavailable:craftionspawner-api:1.7.0.1-craftion.5`
- Scope in CraftionFarmer: `provided`
- JAR SHA-256: `93955c58d3d6f7ce37823c979a812db747cd0177cbc0d2dd05c99dae83581fec`

The vendored artifact contains only CraftionSpawner's public API module. It is excluded explicitly
from the shaded CraftionFarmer plugin JAR; the runtime implementation is supplied by CraftionSpawner.
