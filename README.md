# CatAC Core

Núcleo anticheat embebible y de bajo coste para **Minestom 1.21.11**, escrito en
**Java 25**. Toma Mango Anti-Cheat como referencia inicial, pero reorganiza el
proyecto como una librería configurable, extensible y con estado compartido por
jugador.

> Estado: `1.0.0`. Núcleo estable para integración, calibración y
> pruebas en servidores reales; no sustituye límites de red del host.
> servidores reales; todavía no debe tratarse como una solución de autoban sin
> telemetría propia.

## Requisitos

- JDK 25
- Maven 3.9+
- Minestom `2026.05.11-1.21.11`

Minestom tiene alcance `provided`: el proyecto servidor debe aportar su propia
dependencia compatible.

## Compilar e instalar localmente

```bash
mvn clean verify
mvn install
```

Después puede consumirse desde otro proyecto Maven:

```xml
<dependency>
    <groupId>dev.catac</groupId>
    <artifactId>catac-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Integración mínima

Instálalo después de `MinecraftServer.init()` y antes de iniciar el servidor:

```java
import dev.catac.CatAC;
import dev.catac.api.EnforcementMode;
import dev.catac.config.CatACConfig;
import dev.catac.config.CheckPolicy;
import net.minestom.server.MinecraftServer;

MinecraftServer server = MinecraftServer.init();

CatACConfig config = CatACConfig.builder()
        .enforcementMode(EnforcementMode.SETBACK)
        .policy("movement.speed", new CheckPolicy(
                true, 4.0, 8.0, 24.0, 0.20, 1_000))
        .violationHandler(event -> System.out.printf(
                "[CatAC] %s %s buffer=%.2f action=%s evidence=%s%n",
                event.player().getUsername(), event.check().id(),
                event.buffer(), event.action(), event.evidence()))
        .build();

CatAC catac = CatAC.install(config);

// ...configuración del servidor...
server.start("0.0.0.0", 25565);
```

Conserva la instancia y llama a `catac.close()` durante un apagado controlado.
También se emite `CatViolationEvent` en el árbol global de eventos de Minestom.

## Seguridad del ciclo de vida

CatAC permite una sola instancia activa por JVM/servidor Minestom. El ciclo de
vida es `NEW → STARTED → STOPPED`; `close()` es terminal y una nueva instalación
requiere construir otra instancia. Puedes consultar la instancia activa con
`CatAC.current()` y el estado de una instancia con `catac.state()`.

Los checks personalizados deben implementar **exactamente una** interfaz:
`PacketCheck` o `MovementCheck`. Si un check lanza una excepción, CatAC lo
desactiva para esa instancia y conserva el servidor activo. La evidencia de un
check está limitada a 512 caracteres para evitar crecimiento de memoria por
datos externos.

## Modos de aplicación

| Modo | Comportamiento |
|---|---|
| `MONITOR` | Registra infracciones sin setback ni kick; los paquetes no finitos pueden desconectarse por seguridad si se mantiene la opción predeterminada. |
| `SETBACK` | Devuelve al jugador a la última posición segura al superar el umbral correspondiente. |
| `KICK` | Aplica setback y puede expulsar al superar `kickBuffer`, después de las advertencias configuradas para ese mismo check. |

Conviene comenzar en `MONITOR`, observar datos reales y pasar después a
`SETBACK`. El modo `KICK` requiere umbrales calibrados para cada servidor.

## Avisos tranquilos y control de kick

Las anomalías normales no expulsan de inmediato: por defecto CatAC requiere dos
avisos de ese mismo check, separados por cuatro segundos como mínimo. Los
paquetes malformados (NaN, infinito o coordenadas fuera de rango) se mantienen
como excepción de seguridad y pueden desconectarse inmediatamente.

Los textos y la cantidad de avisos se cambian enteramente desde la API. El
proveedor recibe el check, buffer, severidad y número de aviso; devolver `null`
silencia sólo ese caso.

```java
CatACConfig config = CatACConfig.builder()
        .enforcementMode(EnforcementMode.KICK)
        .warningsBeforeKick(3)
        .playerNoticeCooldown(Duration.ofSeconds(6))
        .playerMessageProvider(notice -> switch (notice.type()) {
            case WARNING -> Component.text("Movimiento inusual detectado. Por favor, juega normalmente.");
            case SETBACK -> Component.text("Tu posición fue corregida para mantener una partida justa.");
        })
        .kickMessage(Component.text("No pudimos validar varias acciones de tu cliente."))
        .build();
```

Esto no envía una línea por paquete: el cooldown del jugador es independiente
del cooldown de alertas de moderación y el contador se guarda por check. Por
ejemplo, un aviso de inventario nunca habilita un kick por movimiento.

## Señuelo privado de KillAura

Tras una anomalía de `combat.reach`, CatAC puede enviar por paquetes un jugador
señuelo exclusivamente al sospechoso: no se añade a la instancia, no es visible
para otros jugadores y se elimina en menos de un segundo. Sólo confirma
`combat.aura-decoy` si el cliente ataca el ID exacto del señuelo después del
tiempo de armado mientras dicho objetivo sigue detrás de su cámara. En modo
`KICK` la confirmación expulsa al jugador; en `MONITOR` y `SETBACK` genera una
alerta de alta confianza sin expulsar.

No se considera una certeza matemática: latencia extrema, modificaciones de
cliente y futuras variaciones de protocolo deben probarse antes de endurecer
producción. La combinación de ID privado, armado, distancia fuera de alcance
vanilla y orientación trasera evita que un ataque normal pueda confirmarlo.

```java
import dev.catac.config.AuraDecoyPolicy;

CatACConfig.builder()
        .auraDecoyPolicy(new AuraDecoyPolicy(
                true,
                1.5,                       // severidad mínima de reach para desplegar
                Duration.ofSeconds(12),     // cooldown por jugador
                Duration.ofMillis(175),     // espera a que llegue el spawn
                Duration.ofMillis(650),     // vida total del señuelo
                3.75,                       // detrás; fuera de alcance vanilla
                -0.35,                      // el jugador aún mira hacia delante
                Component.text("Se detectó ataque automatizado.")))
        .build();
```

Usa `AuraDecoyPolicy.disabled()` para desactivarlo por completo. La política se
valida al construir la configuración; no se aceptan distancias que un jugador
legítimo pueda golpear desde quieto.

## Protección de daño de combate

Cuando CatAC cancela un ataque de entidad (por ejemplo, alcance inválido), arma
una protección de un solo uso sobre la víctima exacta. Si Minestom recibe el
`EntityDamageEvent` correspondiente dentro de 500 ms, el daño se cancela antes
de modificar la vida de la víctima. Esto impide que una acción detectada como
trampa afecte a otros jugadores incluso si otra mecánica del servidor intentó
producir daño desde el mismo ataque.

El host conserva la decisión final y puede aplicar la misma protección desde
un check propio:

```java
import dev.catac.api.DamageDecision;
import dev.catac.config.DamageProtectionPolicy;

CatACConfig config = CatACConfig.builder()
        .damageProtectionPolicy(new DamageProtectionPolicy(
                true,
                Duration.ofMillis(500),
                context -> context.reason().startsWith("combat.")
                        ? DamageDecision.DENY
                        : DamageDecision.ALLOW))
        .build();

// Desde un check o una integración que ya invalidó el ataque:
catac.denyDamage(attacker, victim, Duration.ofMillis(300), "custom.invalid-hit");
```

La ventana está ligada al UUID de la víctima y se consume tras el primer evento,
por lo que no bloquea daño posterior ni daño a otras entidades. Para desactivar
esta capa usa `DamageProtectionPolicy.disabled()`.

## Defensa contra inundación de paquetes

CatAC usa `PlayerPacketEvent`, que Minestom emite antes de ejecutar el listener
vanilla del paquete. Por ello un paquete que supera su presupuesto se cancela
antes de modificar mundo, inventario, combate o lógica de plugins. Cada jugador
tiene dos token buckets acotados: 160 paquetes/s con ráfaga de 240 y, aparte,
24 paquetes costosos/s con ráfaga de 40. Los primeros excesos se descartan; ocho
excesos dentro de tres segundos expulsan al atacante.

Los paquetes desconocidos o custom se tratan como `NORMAL`, por lo que CatAC no
supone que una mecánica propia sea hostil. Clasifica sólo aquellos paquetes que
tu servidor sepa costosos:

```java
import dev.catac.api.PacketCost;
import dev.catac.config.PacketBudget;
import dev.catac.config.PacketFloodPolicy;

CatACConfig config = CatACConfig.builder()
        .packetFloodPolicy(new PacketFloodPolicy(
                true,
                new PacketBudget(180, 270),
                new PacketBudget(30, 50),
                8,
                Duration.ofSeconds(3),
                (player, packet) -> packet instanceof MyLargeCustomPacket
                        ? PacketCost.HEAVY : PacketCost.NORMAL,
                event -> logger.warning("Flood: " + event.packetType().getSimpleName()),
                Component.text("Demasiados paquetes recibidos.")))
        .build();
```

`CatAC.metrics()` expone `floodDrops` y `floodKicks`; también se publica
`PacketFloodEvent`. CatAC no reemplaza el decodificador ni la cola interna de
Minestom: para paquetes malformados, compresión y ancho de banda previos a la
decodificación, el host debe conservar límites de proxy/firewall y los límites
de conexión de Minestom.

## Checks incluidos

| ID | Área | Idea principal |
|---|---|---|
| `packet.invalid-movement` | Paquetes | Rechaza NaN, infinito, coordenadas inseguras y pitch imposible. |
| `packet.timer` | Paquetes | Balance temporal con tolerancia a ráfagas y lag del servidor. |
| `movement.speed` | Movimiento | Límite horizontal según atributo, inercia, superficie y latencia. |
| `movement.vertical` | Movimiento | Predicción de gravedad y detección de vuelo estacionario. |
| `movement.ground-spoof` | Movimiento | Contrasta el bit de suelo del cliente con colisiones reales. |
| `movement.phase` | Movimiento | Detecta movimiento dentro de formas de colisión sólidas. |
| `combat.reach` | Combate | Distancia ojo-AABB con historial y rewind por latencia. |
| `world.fast-break` | Mundo | Usa el cálculo real de rotura de Minestom, herramienta y latencia. |

## Ajustes más importantes

```java
CatACConfig config = CatACConfig.builder()
        .joinGrace(Duration.ofSeconds(2))
        .teleportGrace(Duration.ofMillis(400))
        .velocityGrace(Duration.ofMillis(900))
        .networkProbeInterval(Duration.ofSeconds(1))
        .networkAcknowledgementTimeout(Duration.ofSeconds(3))
        .lagCompensationThresholdMillis(85.0)
        .disableCheck("movement.vertical")
        .exemptionProvider((player, checkId) ->
                player.hasPermission("catac.bypass." + checkId))
        .build();
```

Cada `CheckPolicy` contiene, en orden: activación, buffer de alerta, buffer de
setback, buffer de kick, decaimiento por pase y cooldown de alertas en
milisegundos. También puede concederse una exención temporal:

```java
catac.exempt(player, Duration.ofSeconds(2));
```

## Sincronización de red

CatAC envía un `PingPacket` ligero cada segundo por defecto y consume el
`ClientPongPacket` correspondiente. Con ello mantiene RTT y jitter suavizados,
sin usar tareas por jugador ni colecciones crecientes. Tras una velocidad,
CatAC programa un ping al final del tick para saber cuándo el cliente ya recibió
el impulso; los movimientos permanecen temporalmente fuera de los checks
predictivos hasta el `Pong` o el timeout. Los teletransportes se vinculan al ID
de confirmación nativo de Minestom.

La información está disponible para integraciones, telemetría y futuros checks:

```java
catac.networkSnapshot(player).ifPresent(network ->
        System.out.printf("rtt=%.1fms jitter=%.1fms teleport=%s velocity=%d%n",
                network.roundTripMillis(), network.jitterMillis(),
                network.teleportPending(), network.pendingVelocities()));
```

`networkAcknowledgementTimeout` es un límite de seguridad: al vencer, CatAC
vuelve a evaluar movimiento en vez de bloquear el estado indefinidamente.

## Mundo, inventario y telemetría

`world.interaction` valida alcance de bloques y coordenadas de cursor de
placement antes de que Minestom procese la interacción. `inventory.invalid-click`
rechaza referencias de ventana, slot y hotbar imposibles; CatAC no reimplementa
la lógica normal de clics de Minestom. `inventory.move` es una señal de
telemetría, sin cancelación automática.

Las métricas acumuladas no conservan paquetes ni datos sensibles y permiten
calibrar por servidor:

```java
var metrics = catac.metrics();
System.out.printf("samples=%d alerts=%d cancelled=%d kicks=%d%n",
        metrics.violationSamples(), metrics.alerts(),
        metrics.cancelledPackets(), metrics.kicks());
```

Puedes desactivarlas con `.telemetryEnabled(false)` si no se usarán.

## Replay y calibración

El paquete `dev.catac.testing` ofrece `ReplayRunner`, `PacketFuzzer`,
`HotPathBenchmark` y `CalibrationAnalyzer`. Son herramientas offline y
reproducibles: no capturan tráfico ni se ejecutan en el hilo del servidor. Lee
[la guía de calibración](docs/CALIBRATION.md) antes de convertir alertas en
setbacks o kicks.

## Predictor de movimiento

Las comprobaciones de movimiento comparten un modelo por jugador que conserva
inercia horizontal, gravedad y drag vertical entre muestras. Usa el atributo de
velocidad, fricción y factor de superficie, estado de sneak y salto con
`JUMP_BOOST`; las sondas de red aportan una tolerancia pequeña y acotada.

El análisis de colisiones revisa el destino y barre la AABB cada 0,20 bloques
(máximo 32 subpasos). Así un paquete que intente atravesar una pared no queda
oculto sólo porque acaba al otro lado. Si el recorrido cruza un chunk sin cargar,
la muestra se marca incompleta y los checks se abstienen.

## Combate y rewind

Cada entidad con instancia mantiene un historial circular de 32 posiciones,
actualizado durante su tick y liberado al despawnear. Al atacar, CatAC calcula
el instante que probablemente vio el atacante: `padding + RTT/2 + jitter`, con
un máximo configurable de 350 ms. La posición objetivo se interpola para ese
instante; CatAC no busca la posición más cercana dentro de una ventana, porque
eso equivaldría a regalar alcance.

`combat.reach` valida distancia ojo-AABB, que el objetivo esté delante de la
vista y la línea de visión contra formas de colisión. Un chunk ausente hace que
la comprobación de visión se abstenga. Ajustes disponibles:

```java
CatACConfig.builder()
        .combatRewindPadding(Duration.ofMillis(50))
        .combatMaxRewind(Duration.ofMillis(350))
        .build();
```

Las exenciones físicas integradas cubren estados donde una predicción simple no
es fiable, como vehículos, vuelo, élitros, riptide, levitación, caída lenta,
gracia del delfín, líquidos, escaleras, telarañas y chunks incompletos. Las
interacciones con bloques se abstienen si el chunk no está cargado y el combate
no castiga una dirección de cámara posiblemente desfasada durante un tick.

## Crear un check propio

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

Registro:

```java
CatAC catac = CatAC.builder()
        .config(config)
        .addCheck(new LargeStepCheck())
        .build()
        .start();
```

Consulta [la guía de desarrollo](docs/DEVELOPER_GUIDE.md) y
[la arquitectura](docs/ARCHITECTURE.md) antes de crear checks que trabajen en el
hot path.

## Principios del núcleo

- Un único `EventNode` y una pasada compartida de colisiones por movimiento.
- Estructuras primitivas o preasignadas en el camino caliente.
- `System.nanoTime()` para tiempos monotónicos.
- Historial circular acotado, sin crecimiento por jugador.
- Sincronización Ping/Pong, teletransporte y velocidades con colas fijas.
- Predicción física por tick y barrido AABB acotado para movimiento.
- Rewind interpolado y validación de alcance, dirección y línea de visión.
- Validación de mundo/inventario y métricas de enforcement sin payloads.
- Replay determinista, fuzzing reproducible, benchmark y calibración offline.
- Umbrales con buffer y decaimiento para evitar castigos por una sola muestra.
- Setback sólo hacia una posición previamente considerada segura.
- Compensación de lag global y tolerancias acotadas por latencia.

## Límites de esta versión

El núcleo aún necesita pruebas de integración con clientes reales, perfiles por
versión/protocolo, reproducción determinista de trazas y más checks de combate.
No contiene autoban persistente ni almacenamiento de sanciones. Estas decisiones
se dejan fuera de la librería para que el servidor anfitrión conserve el control.

## Licencia y procedencia

MIT. Consulta `LICENSE` y `NOTICE`. La reescritura mantiene la atribución del
proyecto Mango Anti-Cheat que sirvió como referencia.
