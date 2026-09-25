# CatAC Core

Embeddable anti-cheat core for **Minestom 1.21.11**, written in **Java 25**.

CatAC is designed as a library, not a standalone server plugin. The host application owns configuration, enforcement, persistence and integrations.

## Requirements

- Java 25+
- Minestom `2026.05.11-1.21.11`
- Maven 3.9+ or Gradle
- CatAC Core `1.0.0v`

Minestom is not bundled with CatAC and must be provided by the host project.

## Installation

### Gradle Kotlin DSL

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Anyelo120:CatAC-core:1.0.0v")
}
```

### Gradle Groovy

```groovy
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation "com.github.Anyelo120:CatAC-core:1.0.0v"
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.Anyelo120</groupId>
        <artifactId>CatAC-core</artifactId>
        <version>1.0.0v</version>
    </dependency>
</dependencies>
```

For local development:

```bash
mvn clean install
```

Then use:

```xml
<dependency>
    <groupId>dev.catac</groupId>
    <artifactId>catac-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick start

Install CatAC after `MinecraftServer.init()` and before starting the server.

```java
import dev.catac.CatAC;
import dev.catac.api.EnforcementMode;
import dev.catac.config.CatACConfig;
import net.minestom.server.MinecraftServer;

MinecraftServer server = MinecraftServer.init();

CatACConfig config = CatACConfig.builder()
        .enforcementMode(EnforcementMode.MONITOR)
        .violationHandler(event -> System.out.printf(
                "[CatAC] %s %s buffer=%.2f action=%s%n",
                event.player().getUsername(),
                event.check().id(),
                event.buffer(),
                event.action()))
        .build();

CatAC catac = CatAC.install(config);

server.start("0.0.0.0", 25565);
```

Keep the returned instance and close it during controlled shutdown:

```java
catac.close();
```

Only one CatAC instance may be active per JVM/Minestom server.

## Enforcement modes

| Mode | Behavior |
|---|---|
| `MONITOR` | Detect and emit violations without normal setback/kick enforcement. |
| `SETBACK` | Returns the player to the last accepted safe position when configured thresholds are reached. |
| `KICK` | Enables configured setback/kick enforcement after the required warnings and thresholds. |

Start production calibration in `MONITOR`.

## Configuration

Example:

```java
CatACConfig config = CatACConfig.builder()
        .enforcementMode(EnforcementMode.SETBACK)
        .joinGrace(Duration.ofSeconds(2))
        .teleportGrace(Duration.ofMillis(400))
        .velocityGrace(Duration.ofMillis(900))
        .lagCompensationThresholdMillis(85.0)
        .disableCheck("movement.vertical")
        .exemptionProvider((player, checkId) ->
                player.hasPermission("catac.bypass." + checkId))
        .build();
```

Temporary exemption:

```java
catac.exempt(player, Duration.ofSeconds(2));
```

Network state:

```java
catac.networkSnapshot(player).ifPresent(network -> {
    System.out.println("RTT: " + network.roundTripMillis());
    System.out.println("Jitter: " + network.jitterMillis());
});
```

Metrics:

```java
var metrics = catac.metrics();

System.out.printf(
        "samples=%d alerts=%d cancelled=%d kicks=%d%n",
        metrics.violationSamples(),
        metrics.alerts(),
        metrics.cancelledPackets(),
        metrics.kicks());
```

## Included checks

| ID | Area |
|---|---|
| `packet.invalid-movement` | Invalid movement packets |
| `packet.timer` | Packet timing / timer |
| `movement.speed` | Horizontal movement |
| `movement.vertical` | Vertical prediction |
| `movement.ground-spoof` | Client ground-state spoofing |
| `movement.phase` | Solid collision / phase |
| `combat.reach` | Reach with latency rewind |
| `world.fast-break` | Block-breaking timing |

CatAC also includes validation and protection layers for world interaction, inventory input, combat damage and packet flooding.

## Custom checks

Implement exactly one check interface:

- `PacketCheck`
- `MovementCheck`

Example:

```java
public final class LargeStepCheck implements MovementCheck {

    private static final CheckDescriptor INFO = new CheckDescriptor(
            "custom.large-step",
            "Large vertical step",
            CheckCategory.MOVEMENT,
            CheckPolicy.standard(3, 6, 18),
            true
    );

    @Override
    public CheckDescriptor descriptor() {
        return INFO;
    }

    @Override
    public CheckResult evaluate(MovementFrame frame, PlayerData data) {
        return frame.deltaY() > 1.25
                ? CheckResult.fail(1.0, "dy=" + frame.deltaY())
                : CheckResult.pass();
    }
}
```

Register it during construction:

```java
CatAC catac = CatAC.builder()
        .config(config)
        .addCheck(new LargeStepCheck())
        .build()
        .start();
```

CatAC disables a custom check for the current instance if that check throws an exception, keeping the host server alive.

## Events and API

Useful integration points include:

- `CatViolationEvent`
- `PacketFloodEvent`
- `CatAC.metrics()`
- `CatAC.networkSnapshot(player)`
- `CatAC.exempt(player, duration)`
- `CatAC.denyDamage(...)`
- `CatAC.current()`
- `catac.state()`

Violation evidence is bounded to avoid unbounded memory growth.

## Architecture

The core is optimized for the Minestom hot path:

- Shared movement/collision processing.
- Bounded per-player state.
- Circular entity history for combat rewind.
- Ping/Pong based RTT and jitter tracking.
- Velocity and teleport acknowledgement tracking.
- Deterministic replay/fuzzing/calibration utilities.
- No persistent punishment storage.
- No host-wide firewall/proxy replacement.

See:

- [`docs/DEVELOPER_GUIDE.md`](docs/DEVELOPER_GUIDE.md)
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/CALIBRATION.md`](docs/CALIBRATION.md)

## Production notes

CatAC is an anti-cheat core, not an automatic punishment system.

Before enabling aggressive enforcement:

1. Run in `MONITOR`.
2. Collect real server telemetry.
3. Calibrate thresholds for your mechanics and latency profile.
4. Enable `SETBACK`.
5. Enable `KICK` only for sufficiently validated checks.

The host remains responsible for persistent bans, moderation policy, proxy/firewall limits and protocol-level protections outside Minestom's decoded packet pipeline.

## Build

```bash
mvn clean verify
```

Install locally:

```bash
mvn install
```

## License

MIT. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).

CatAC Core retains attribution to Mango Anti-Cheat as the original reference used during the rewrite.
