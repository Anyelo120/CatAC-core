# Validación de esta entrega

**Fecha:** 2026-10-03. **Artefacto:** `dev.catac:catac-core:2.0.0`. **Minestom:** `2026.05.11-1.21.11` (`provided`). **Toolchain:** OpenJDK 25.0.4.1 y Maven 3.9.11 mediante el wrapper oficial 3.3.4.

## Build y pruebas ejecutadas

Se compiló primero el ZIP inicial: **46 tests, 0 fallos, 0 errores, 0 omitidos**. El núcleo actualizado compila con `-Xlint:all` y `failOnWarning=true`. Se verificó además un consumidor separado que usa el JAR instalado, declara la misma dependencia Minestom e instala/cierra CatAC con una extensión del host.

| Ejecución | Resultado | Evidencia en el paquete |
|---|---|---|
| Baseline inicial, `clean verify` | 46/46 correctos | `validation/baseline-build.log` |
| Wrapper, `clean install` | 130/130 correctos | `validation/root-build.log`, `validation/junit/core/` |
| Wrapper, consumidor `clean verify` | 1/1 correcto | `validation/consumer-build.log`, `validation/junit/consumer/` |
| Segundo `clean package -DskipTests` | JAR binario y fuentes idénticos a la primera compilación | `validation/reproducibility.json`, `validation/reproducibility-build.log` |

**Total final: 131 tests, sin fallos, errores u omisiones.** La segunda compilación comprueba reproducibilidad, sin volver a ejecutar la suite ya superada. `validation/test-summary.json` conserva el desglose de suites. El workflow de GitHub está escrito, pero no se ha ejecutado remotamente ni se ha publicado el artefacto.

La entrega contiene 116 archivos Java principales, 33 archivos Java de tests del núcleo y un consumidor externo. El baseline tenía 85 archivos Java principales. Los 46 tests previos se conservan, ajustando dos expectativas ligadas a los contratos nuevos: capacidad del historial y avisos realmente enviados.

## Suite de regresiones añadida

| Suite | Tests | Áreas |
|---|---:|---|
| AuraDecoyRegressionTest | 2 | Sonda nativa, diagnóstico común, techo OBSERVE y exención específica |
| AcceptanceRegressionTest | 11 | Acciones finales, ancla obstruida, observer opcional, status, stairs, slide y provider |
| ActionRegressionTest | 13 | Click/drag/GUI nativos, dig, rango de bloques, reconciliación y daño correlacionado |
| CorpusAndModelTest | 14 | Corpus, predictor, atributos, impulso, medios, rewind y denominadores etiquetados |
| FoundationRegressionTest | 8 | Decaimiento, resultados, capacidades, avisos, tiempo e incidentes |
| FuzzAndConcurrencyTest | 8 | 10.000 casos fuzz, historial concurrente, shutdown, fallos, perfiles y cotas |
| GeometryRegressionTest | 11 | Barrido continuo, pane, landing, slab/fence, factor lento, presupuesto y AABB |
| NativeEngineRegressionTest | 9 | Pipeline nativo, eventos posteriores, confianza, hardening y reserva Pong |
| NativePipelineBenchmarkTest | 1 | Benchmark nativo y movimiento legítimo sin detecciones |
| SynchronizationRegressionTest | 7 | IDs de teleport/Pong, vector, timeout, capacity y reserva de control |

Los tests usan Player, bloques, chunks cargados, packet listeners y eventos reales de la dependencia fijada. El transporte se captura en memoria: **no hubo conexión de un cliente Minecraft real ni sockets multiusuario**. Los casos de provider/check fallido generan logs de fallo deliberados; sus suites prueban el aislamiento y pasan. Maven/Minestom emiten avisos de runtime por uso de `sun.misc.Unsafe`; no son warnings de compilación de CatAC.

## Benchmark observado

Caso: un jugador, suelo plano, movimiento circular legítimo, listeners nativos, transporte en memoria. Calentamiento: 500 frames; muestras: 3000. Incluye el harness y su tick, no sólo la función de un check. La prueba exige cero detecciones y cero fallos de runtime para este escenario.

| Métrica | Tiempo observado |
|---|---:|
| Media | 17.735 µs |
| p50 | 13.560 µs |
| p95 | 38.768 µs |
| p99 | 76.656 µs |
| Máximo | 264.508 µs |

Fuente reproducible: `validation/benchmark.json` y `NativePipelineBenchmarkTest`. Una ejecución dentro de un contenedor compartido no mide capacidad del servidor ni ofrece un SLA. No se midieron red real, asignaciones con profiler, GC, throughput multiusuario ni tick de un mundo productivo. El máximo no se oculta como outlier; la suite no impone un gate temporal frágil.

## Artefactos y reproducibilidad

Las dos compilaciones limpias usan el mismo toolchain y `project.build.outputTimestamp=2026-10-03T00:00:00Z`. Los hashes del JAR y sources JAR coinciden byte por byte. Esto prueba reproducibilidad en este entorno; no prueba identidad entre todos los sistemas operativos/JDK vendors.

| Archivo | SHA-256 |
|---|---|
| `dist/catac-core-2.0.0-sources.jar` | `ebeefb6c9876c685e6089e3f749e3f20ced3177fa8a1098533f77e52b5a78ba6` |
| `dist/catac-core-2.0.0.jar` | `f293b65184841561bdd29a8227f92edbad1c73571f8fa6306e89ed5a9959d452` |

`dist/SHA256SUMS` permite comprobar los JARs. Se inspeccionó el JAR binario: 133 clases del proyecto, major de classfile 69 (Java 25) y **0 clases `net/minestom/` incluidas**. El manifest declara la versión Minestom y replay schema 1. No hay ensamblado sombreado de Minestom ni JDK dentro del ZIP. El ejemplo `examples/MinimalServerIntegration.java` también se compiló por separado con `-Xlint:all -Werror` contra el classpath del consumidor (`validation/example-compilation.json`).

SHA-256 del ZIP inicial usado como baseline: `e28177b2c2c8f613b534b567bc533144647560ef9305a8516d144792f70a4343`.

## Alcance pendiente

La matriz [ESTADO_IMPLEMENTACION.md](ESTADO_IMPLEMENTACION.md) distingue regresiones completas del escenario local y cobertura parcial. No se han medido precisión/recall/FPR en usuarios, compatibilidad Geyser, simulación completa de elytra/riptide/vehículos/medios, ni recuperación de todos los fallos de bootstrap. La utilidad de calidad etiquetada está probada con datos sintéticos; sus resultados no se presentan como precisión del antitrampas.

La activación del host sigue [CALIBRATION.md](CALIBRATION.md): MONITOR, corpus etiquetado, SETBACK por familia y KICK tras validación. Los checks con techo OBSERVE permanecen experimentales. Esta entrega es una mejora verificable del núcleo; no acredita un antitrampas invulnerable ni la finalización empírica de todo el plan.
