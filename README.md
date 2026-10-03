# CatAC Core 2.0

Antitrampas embebible para **Minestom `2026.05.11-1.21.11` y Java 25**. El host conserva la autoridad sobre el mundo, las mecánicas, el daño y los permisos. CatAC valida acciones y movimiento mediante 20 checks, sincronización acotada y geometría nativa de Minestom.

Esta versión implementa las mejoras del núcleo del plan original y añade regresiones sobre el flujo real de Minestom. Las detecciones de medios especiales, no-slow, descenso, knockback y dirección del ataque son experimentales: sus capacidades permiten únicamente observar. El estado por tarea y los límites pendientes están en [ESTADO_IMPLEMENTACION.md](docs/ESTADO_IMPLEMENTACION.md). La precisión con jugadores reales todavía requiere calibración.

## Compilar y consumir

Requisitos: JDK **25**, Maven **3.9.11** mediante el wrapper y acceso a Maven Central para la primera compilación. Minestom es una dependencia `provided`; el JAR de CatAC no lo incluye. El build exige Java 25 y falla si el compilador emite warnings del proyecto.

```bash
chmod +x mvnw
./mvnw -B -ntp clean install
./mvnw -B -ntp -f examples/consumer/pom.xml clean verify
```

`install` publica únicamente en el repositorio Maven local. **La versión 2.0.0 de este paquete no se ha publicado en Maven Central ni JitPack.** En el ZIP entregado, `dist/` contiene el JAR compilado, el JAR de fuentes y sus SHA-256. Para consumir el artefacto instalado:

```xml
<dependency>
    <groupId>dev.catac</groupId>
    <artifactId>catac-core</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>net.minestom</groupId>
    <artifactId>minestom</artifactId>
    <version>2026.05.11-1.21.11</version>
</dependency>
```

Para un consumidor Gradle, después de `./mvnw install`:

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation("dev.catac:catac-core:2.0.0")
    implementation("net.minestom:minestom:2026.05.11-1.21.11")
}
```

## Integración mínima

Instala CatAC después de inicializar Minestom y antes de aceptar jugadores. Configura también las instancias y el spawn del host.

```java
MinecraftServer server = MinecraftServer.init();
CatAC catac = CatAC.install(CatACConfig.builder()
        .enforcementMode(EnforcementMode.MONITOR)
        .traceCapacity(16)
        .violationHandler(event -> System.out.printf(
                "[CatAC] check=%s buffer=%.2f decision=%s%n",
                event.check().id(), event.buffer(), event.action()))
        .build());
Runtime.getRuntime().addShutdownHook(new Thread(catac::close, "catac-shutdown"));
server.start("0.0.0.0", 25565);
```

Sólo puede haber una instancia activa por JVM. `close()` es idempotente; una instancia detenida no se reinicia. El ejemplo consumidor en `examples/consumer/` compila contra el JAR instalado y prueba su instalación, un check del host y el cierre.

## Políticas y modos

| Modo | Acciones normales de gameplay |
|---|---|
| `MONITOR` — predeterminado | Observa y registra evidencia. |
| `SETBACK` | Puede cancelar acciones y corregir movimiento si el check tiene esas capacidades. |
| `KICK` | Añade expulsión para checks autorizados, con umbral, detecciones y avisos suficientes. |

La seguridad estructural y el presupuesto de paquetes se configuran por separado. También en `MONITOR` se rechazan datos malformados peligrosos, se reconcilian ventanas obsoletas y se descarta flood. `disconnectMalformedPackets(false)` suprime la desconexión inmediata por formato, conservando la cancelación. Los kicks por flood requieren `PacketFloodPolicy.defaults().withKicks(true)`; están desactivados de fábrica. Los checks con `OBSERVE` nunca cancelan, corrigen o expulsan por su evidencia.

```java
CatACConfig config = CatACConfig.builder()
        .enforcementMode(EnforcementMode.MONITOR)
        .checkMode("movement.phase", EnforcementMode.SETBACK)
        .checkMode("combat.reach", EnforcementMode.SETBACK)
        .policy("movement.speed", CheckPolicy.standard(4, 8, 24).withTimeDecay(0.4))
        .warningsBeforeKick(2)
        .minimumDetectionsBeforeKick(4)
        .incidentWindow(Duration.ofSeconds(120))
        .build();
```

Los IDs desconocidos de overrides se rechazan al construir el motor. Los buffers decaen **por tiempo transcurrido**, independientemente de la cantidad de paquetes. `PASS`, no aplicable e incertidumbre no descuentan evidencia por paquete. Un mensaje suprimido con `null` o un proveedor que falla no cuentan como aviso entregado.

## Cobertura de los 20 checks

Las capacidades de esta tabla son límites; la acción efectiva también depende del resultado, perfil, modo, umbral y existencia de un ancla válida.

| ID | Validación | Límite de acción |
|---|---|---|
| `packet.invalid-movement` | Coordenadas, ángulos y finitud | Hardening; puede desconectar formato peligroso |
| `packet.timer` | Balance acotado de paquetes de movimiento | Cancelación/kick según política |
| `packet.input` | Bits reservados; pistas de entrada no confiables | Hardening; cancelación, sin kick |
| `packet.ground-status` | Flags de suelo en status/rotación | Cancelación/kick según política |
| `movement.speed` | Envolvente horizontal vectorial y atributos | Movimiento: cancelación, setback, kick |
| `movement.vertical` | Gravedad, salto, atributos y efectos modelados | Movimiento: cancelación, setback, kick |
| `movement.ground-spoof` | Suelo declarado frente a soporte real | Movimiento: cancelación, setback, kick |
| `movement.phase` | Barrido continuo, shapes, rutas por ejes y step | Movimiento: cancelación, setback, kick |
| `combat.target` | Objetivo, instancia, visibilidad y estructura | Hardening; cancelación, sin kick |
| `combat.reach` | AABB histórica, rango del atributo y oclusión | Cancelación/kick según política |
| `world.interaction` | Rango del atributo, cursor, secuencia y oclusión | Hardening de formato; cancelación/kick normal |
| `world.fast-break` | Tiempo nativo y continuidad de bloque/herramienta | Cancelación/kick según política |
| `inventory.invalid-click` | Slots, botones, drag, creatividad y ventana | Hardening/reconciliación; sin kick |
| `inventory.move` | Movimiento con inventario abierto | Sólo observación |
| `combat.ray` | Rayo de cámara contra AABB histórica | Sólo observación experimental |
| `combat.aura-decoy` | Ataque a señuelo privado | Sólo observación; sonda desactivada de fábrica |
| `movement.medium` | Envolventes conservadoras de agua/escalada/ralentización | Sólo observación experimental |
| `movement.descent` | Anomalías de descenso | Sólo observación experimental |
| `movement.no-slow` | Velocidad durante uso de objetos | Sólo observación experimental |
| `movement.knockback` | Respuesta a impulso enviado por el servidor | Sólo observación experimental |

Los medios no modelados suspenden la física predictiva, sin eximir automáticamente la geometría de phase. Un Pong prueba recepción en la conexión; no demuestra que el cliente haya aplicado el impulso.

## API del host y diagnóstico

```java
catac.exempt(player, "movement.speed", Duration.ofSeconds(2));
catac.exempt(player, Duration.ofMillis(400));
catac.networkSnapshot(player);                 // RTT, jitter y sincronización pendiente
catac.synchronizationDiagnostics(player);      // timeouts y Pongs ignorados
catac.diagnostics();                          // evaluaciones, resultados, fallos y coste por check
catac.health();                               // integraciones degradadas y overflow de salida
catac.traces(player);                         // ring opcional de detecciones numéricas
catac.metrics();                              // acciones confirmadas y contadores acumulados
```

Las exenciones no anulan hardening. El proveedor de perfiles usa metadatos confiables del host; perfiles `TRANSLATED`, `CUSTOM` y `UNKNOWN` conservan hardening y limitan gameplay a monitorización. No hay certificación de compatibilidad con Geyser en este paquete.

Para daño propio o diferido del host, correlaciona una acción positiva única:

```java
catac.denyDamage(attacker, victim, actionId, Duration.ofMillis(500), "host.combat");
if (!catac.consumeDamageDenial(attacker, victim, actionId)) {
    // Aplicar el daño de esta acción en el host.
}
```

CatAC no crea una guarda de daño genérica al cancelar un ataque nativo. Así evita que un ataque rechazado bloquee un ataque legítimo posterior. El overload sin `actionId` es para el siguiente daño síncrono compatible; lee su alcance en la guía de desarrollo.

## Documentación y verificación

- [Estado del plan y límites pendientes](docs/ESTADO_IMPLEMENTACION.md).
- [Arquitectura y contratos de confianza](docs/ARCHITECTURE.md).
- [Integración, extensiones y operación](docs/DEVELOPER_GUIDE.md).
- [Migración desde 1.0](docs/MIGRATION_2_0.md).
- [Replay y calibración](docs/CALIBRATION.md).
- [Resultados verificables de esta entrega](docs/VALIDACION.md).
- [Plan original conservado](docs/PLAN_MEJORA_BASELINE.md): describe el ZIP inicial; sus hallazgos no son el estado actual.

La CI incluida ejecuta el build y el consumidor con Java 25. Se ha verificado localmente la secuencia del workflow; no se ha ejecutado ni publicado en GitHub. El corpus sintético y el benchmark de un jugador no sustituyen una campaña real de falsos positivos, cobertura y carga.
