# Replay, fuzzing y calibración

Estas herramientas viven en `dev.catac.testing`. Son para pruebas, CI y análisis
offline; no se registran en el `EventNode` ni capturan paquetes de jugadores.

## Flujo recomendado

1. Ejecuta CatAC en `EnforcementMode.MONITOR`.
2. Desde tu `ViolationHandler`, exporta sólo las señales necesarias para cada
   caso de prueba: ID del check, severidad, acción, ping agrupado y mecánica.
   Evita nombres de jugador, chat, inventarios o payloads completos.
3. Convierte los escenarios relevantes en `ReplayFrame<T>` con timestamps
   monótonos y un adaptador `ReplayExecutor<T, R>` propio.
4. Ejecuta el mismo replay en CI y compara decisiones, buffers o resultados.
5. Resume muestras con `CalibrationAnalyzer.summarize(...)` y ajusta primero
   alertas; después setbacks; deja kicks para señales repetidas y revisadas.

## Replay determinista

```java
var result = ReplayRunner.run(frames, (sample, nowNanos) ->
        adapter.evaluate(sample, nowNanos));
```

El runner rechaza secuencias duplicadas o tiempo que retrocede. El adaptador es
del servidor anfitrión porque CatAC no debe serializar ni reconstruir paquetes
de Minestom en producción.

## Fuzzing

`PacketFuzzer.generate(seed, count)` entrega casos primitivos con NaN, infinito,
coordenadas límite, cursores fuera de rango y slots/ventanas anómalos. El mismo
seed genera exactamente la misma lista: guárdalo al encontrar un fallo y añádelo
como regresión. Nunca envíes estos casos contra un servidor público.

## Benchmarks

`HotPathBenchmark` informa promedio, p50 y p95 para un `Runnable`. Sirve para
comparaciones rápidas locales; para publicar números o cambiar decisiones de
arquitectura, usa JMH, CPU fijada, calentamiento suficiente y múltiples forks.

Objetivo inicial: no aumentar el p95 de una evaluación de check de forma
apreciable al añadir una detección. Mide por separado replay, colisiones,
historial y callbacks; los últimos dos no pertenecen al hot path de detección.

## Criterios antes de endurecer enforcement

- Replays legítimos de ping bajo, medio y alto sin nuevas cancelaciones.
- Escenarios de teletransporte, velocidad, chunk incompleto y GUIs propios.
- Casos fuzz sin excepciones ni crecimiento de estado.
- Métricas de `CatAC.metrics()` revisadas por check y modo de juego.
- Activación gradual: `MONITOR` → `SETBACK` → `KICK`.
