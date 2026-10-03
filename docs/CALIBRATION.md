# Replay, fuzz y calibración

El objetivo es reproducir decisiones del núcleo y separar corrección del software de precisión en jugadores reales. Los fixtures de esta entrega son sintéticos. No equivalen a sesiones reales ni permiten publicar una tasa de falsos positivos.

## Harness nativo

`MinestomReplayHarness` instala CatAC y llama al `PacketListenerManager` de la versión fijada, con sus listeners nativos y `EntityTickEvent` del jugador. Por tanto ejecuta el mismo motor, políticas, cancelación y actualización de estado que la integración. Requiere un proceso Minestom inicializado **sin arrancar**, una instancia/chunks cargados y un Player con conexión de pruebas. No lo instales sobre un servidor productivo.

La configuración debe usar exactamente la misma instancia de `DeterministicClock`. El harness no carga el mundo ni inventa la conexión: los escenarios aportan la instancia, bloques, entidades, AABB, atributos, inventario y acknowledgments. En el árbol de tests, `MinestomFixture` proporciona un Player real, chunks cargados, suelo y transporte de captura en memoria. Sólo se sustituye el transporte; la aplicación de paquetes usa Minestom real.

```java
var clock = new DeterministicClock(0);
var config = CatACConfig.builder().clock(clock)
        .enforcementMode(EnforcementMode.MONITOR).build();
try (var harness = new MinestomReplayHarness(testPlayer, clock, config)) {
    var frames = NativeReplayCodec.read(reader);
    var report = harness.run(frames);
    // Cada FrameResult incluye posición y deltas de detecciones/acciones.
}
```

`testPlayer` y `reader` son elementos del escenario. Un replay numérico de paquetes sin el contexto del mundo no permite reproducir una colisión, inventario o combate por sí solo. No captures paquetes de jugadores automáticamente: `traceCapacity` registra detecciones numéricas acotadas y no crea este corpus.

## Formato del corpus

Primera línea obligatoria:

```text
# CatAC native replay v1; Minecraft 1.21.11
```

Después, CSV estricto con secuencia creciente y timestamp monotónico en nanosegundos. Se permiten tiempos con origen negativo. Cada línea admite hasta 1.024 caracteres y cada lectura hasta 100.000 entradas. El lector no deserializa clases arbitrarias. Versiones, tipos, campos, booleans u orden incorrectos se rechazan.

| Tipo | Campos después de `sequence,timeNanos,type` |
|---|---|
| `MOVE` | x,y,z,yaw,pitch,onGround,horizontalCollision |
| `ROTATION` | yaw,pitch,onGround,horizontalCollision |
| `STATUS` | onGround,horizontalCollision |
| `INPUT` | flags byte firmado |
| `PONG` | ID entero |
| `TELEPORT_CONFIRM` | ID entero |
| `HELD` | slot short |

Ejemplo:

```text
# CatAC native replay v1; Minecraft 1.21.11
0,0,MOVE,0.5,1,0.5,0,0,true,false
1,50000000,MOVE,0.65,1,0.5,0,0,true,false
2,100000000,STATUS,true,false
```

Los fixtures incluidos en `src/test/resources/replays/` cubren suelo, salto con gravedad/aterrizaje y formato hostil. Inventario, excavación y combate usan escenarios Java nativos con estado completo. El codec CSV aún no exporta ni codifica todas esas familias; usa `ReplayFrame<ClientPacket>` y el harness directamente para ampliarlas.

## Fuzz y concurrencia

`PacketFuzzer.generate(seed, count)` produce datos límite de manera reproducible. `FuzzAndConcurrencyTest` ejecuta 10.000 casos a través de checks reales, con entradas no finitas, bounds, cursores y slots. Otras regresiones hacen escrituras/lecturas concurrentes de historial y compiten entradas de paquetes con shutdown. Estas pruebas comprueban aislamiento de excepciones y cotas; no exploran todos los interleavings posibles ni son una prueba formal de ausencia de carreras.

Guarda el seed y el caso mínimo de cualquier error y conviértelo en fixture. Mantén el contexto del mundo necesario y las expectativas de decisiones **y acciones aplicadas**. Un check desactivado no debe contar como un pase del caso. Ejecuta fuzz sólo en procesos y servidores de prueba propios.

## Denominadores y calidad

`CalibrationAnalyzer.summarize(samples)` resume muestras; no puede inferir precisión o tasa de falsos positivos a partir de alertas solas. Para eso existe `quality(List<LabeledTrial>)`: cada trial corresponde a una sesión/escenario independiente, etiquetado externamente como `LEGITIMATE`, `CHEAT` o `UNKNOWN`, con detección sí/no.

| Dato | Cómputo |
|---|---|
| Verdadero positivo / falso negativo | Trial CHEAT con/sin detección |
| Falso positivo / verdadero negativo | Trial LEGITIMATE con/sin detección |
| Precision | TP / (TP + FP) |
| Recall | TP / (TP + FN) |
| Tasa de falso positivo | FP / (FP + TN) |
| `legitimateUpper95Wilson()` | Cota superior de Wilson al 95 % sobre trials legítimos |

Los trials UNKNOWN se cuentan aparte. Si el denominador es cero, los ratios son NaN. No conviertas miles de paquetes correlacionados de una misma sesión en miles de ensayos independientes, ni etiquetes como cheat toda alerta de CatAC. No publiques cifras de precisión a partir de los tests sintéticos.

## Campaña pendiente para cada host

Recoge sesiones consentidas y minimizadas por perfil, mecánica y rango de latencia. Conserva denominadores de sesiones sin alerta, no sólo infracciones. Pseudonimiza cualquier identificador de sesión y define retención/acceso en el host. El núcleo no incorpora una base de datos ni una política de conservación externa.

| Estrato | Casos que deben añadirse |
|---|---|
| Suelo y aire | Walk/sprint/crouch, cambios de dirección, salto repetido, caídas, atributos/effects |
| Colisión | Todas las variantes de stairs/walls/fences, puertas, shapes custom, chunks y cambios de bloques |
| Medios | Swim, bubble columns, ladder/vines, web/honey/slime, hielo y transiciones entre contactos |
| Mecánicas especiales | Elytra, riptide, vehículos, vuelo/teleports/impulsos propios |
| Red | RTT/jitter, agrupación de paquetes, confirmaciones demoradas, timeouts y lag del servidor |
| Combate | Distancias límite, AABB/pose custom, visibilidad parcial, entidades rápidas, instancias y daño diferido |
| Mundo e inventario | Tools/effects cambiantes, permisos/placement del host, todos los clicks, GUIs y cambios de ventana |
| Clientes | Java 1.21.11 verificado; perfiles traducidos/custom por separado |

Define objetivos de precisión/cobertura antes de activar acciones. Para cada familia registra falsos positivos, falsos negativos, abstenciones y coste, con revisión externa de casos. Cero falsos positivos en una muestra pequeña no demuestra riesgo cero; la cota superior conserva esa incertidumbre.

## Promoción y rendimiento

1. `MONITOR`: confirmar que checks/proveedores están activos y observar casos con denominadores.
2. Validación: reproducir anomalías legítimas y adversariales; revisar históricos ausentes, lag y medios no modelados.
3. `SETBACK` por check: grupo controlado, acciones confirmadas y reversión preparada.
4. `KICK` sólo para familias validadas con evidencia repetida y avisos realmente entregados.

Las familias `OBSERVE` no pueden promoverse cambiando el modo; necesitan un modelo validado y una modificación explícita de capacidades. No uses la calibración como justificación para castigar autoclicker, X-ray, aim assist o freecam con una heurística no implementada.

`NativePipelineBenchmarkTest` calienta 500 frames y mide 3.000 en la ruta real, con un jugador y transporte en memoria. Informa mean/p50/p95/p99/max y exige cero detecciones/fallos para ese movimiento legítimo. No fija un umbral temporal frágil en CI. El informe está en `validation/benchmark.json` del paquete y se regenera en `target/benchmark.json` al ejecutar tests.

Para capacidad del servidor, añade una prueba independiente de varios jugadores/conexiones, mundos densos y packets pesados; mide CPU, GC, asignaciones, cola de eventos y tick p95/p99. Repite con JVM/CPU documentadas, múltiples ejecuciones y comparaciones antes/después. Los resultados de esta entrega no son un SLA ni una medición de carga máxima.
