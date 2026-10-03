# Estado de implementación del plan

**Entrega:** CatAC 2.0.0, 2026-10-03. **Objetivo:** Java 25 y Minestom `2026.05.11-1.21.11`.

El núcleo del plan está implementado y verificado localmente. La entrega incorpora 20 checks, sincronización con el flujo nativo, geometría continua, estado de confianza, políticas, reconciliación y diagnóstico. La suite pasa **130 pruebas del núcleo + 1 consumidor externo**, sin fallos ni pruebas omitidas. No se han conectado clientes reales ni medido un servidor de producción.

“Implementado” describe código y regresiones locales, no certifica cobertura universal ni calibración de enforcement. B20 permanece parcial; B10 utiliza un modelo base conservador. Las campañas empíricas del plan siguen pendientes y no se sustituyen por el benchmark sintético.

## Backlog B01–B20

| ID | Estado | Resultado / límite | Evidencia automatizada |
|---|---|---|---|
| B01 | Implementado | Toolchain y dependencia fijados; wrapper, harness nativo, consumidor externo y CI. | NativeEngineRegressionTest; ConsumerTest |
| B02 | Implementado | Dispatch cacheado, no aplicable e incertidumbre; decaimiento independiente del paquete. | FoundationRegressionTest; NativeEngineRegressionTest |
| B03 | Implementado | Capacidades verificadas; techos observacionales; decisión y aplicación separadas. | FoundationRegressionTest; AcceptanceRegressionTest; AuraDecoyRegressionTest |
| B04 | Implementado | Candidato observado/validado, confirmación nativa, ancla por instancia y revalidación. | NativeEngineRegressionTest; AcceptanceRegressionTest; CorpusAndModelTest |
| B05 | Implementado en core/API | Sin guarda automática de ataque cancelado; actionId explícito para daño diferido del host. | ActionRegressionTest; FoundationRegressionTest |
| B06 | Implementado | NanoClock y TimeWindow; límites, origen negativo, rollover e incidentes. | FoundationRegressionTest; SynchronizationRegressionTest |
| B07 | Implementado | Señales de salida acotadas; IDs exactos, vectores, Pong, confirmaciones y timeouts tipados. | SynchronizationRegressionTest; CorpusAndModelTest; FuzzAndConcurrencyTest |
| B08 | Implementado | Serialización por jugador, retiro/cierre y snapshots propios del historial. | FuzzAndConcurrencyTest; GeometryRegressionTest |
| B09 | Implementado | Barrido continuo, presupuesto 512, shapes vecinas, rutas por ejes y step. | GeometryRegressionTest; AcceptanceRegressionTest |
| B10 | Modelo base implementado | Vector, atributos y contactos; envolvente conservadora. Simulación completa de cliente pendiente. | CorpusAndModelTest; GeometryRegressionTest; AcceptanceRegressionTest |
| B11 | Implementado | Exención específica y calidad por causa; medio no modelado no exime phase. | NativeEngineRegressionTest; CorpusAndModelTest |
| B12 | Implementado | Rewind con AABB, instancia, generación y calidad; sin clamping de extremos. | GeometryRegressionTest; CorpusAndModelTest |
| B13 | Implementado; ray observacional | Objetivo, reach y oclusión separados; rayo exacto sin autoridad punitiva. | GeometryRegressionTest; CorpusAndModelTest; ActionRegressionTest |
| B14 | Implementado | Contexto de dig con herramienta/bloque/tiempo nativo; tolerancia acotada y reconciliación. | ActionRegressionTest |
| B15 | Implementado en core | Atributo de rango, formato/oclusión y actualización de bloques. Permisos y placement custom quedan en el host. | ActionRegressionTest |
| B16 | Implementado | Estructura, ventana obsoleta, drag y GUI; operaciones válidas por inventario nativo. | ActionRegressionTest; FuzzAndConcurrencyTest |
| B17 | Implementado; carga pendiente | Buckets normal/heavy, control reservado, notificación limitada y kicks opt-in. | SynchronizationRegressionTest; NativeEngineRegressionTest |
| B18 | Implementado con circuit breaker | Fallos visibles, fallbacks, overrides, limpieza y lifecycle; no recuperación automática de checks. | FuzzAndConcurrencyTest; CatACLifecycleTest; AcceptanceRegressionTest |
| B19 | Infraestructura implementada | Métricas confirmadas, motivos, traces numéricos, corpus y denominadores etiquetados. Calibración real pendiente. | AcceptanceRegressionTest; CorpusAndModelTest; NativePipelineBenchmarkTest |
| B20 | Parcial / experimental | Medio, descenso, no-slow, knockback, ray y perfiles. Sin modelo completo ni validación sancionadora de estas familias. | CorpusAndModelTest; FuzzAndConcurrencyTest |

## Hallazgos H01–H24

El [plan original](PLAN_MEJORA_BASELINE.md) corresponde al ZIP inicial y se conserva como referencia histórica. Esta tabla es el estado actual, no una reedición de sus hallazgos.

| Hallazgos | Situación en 2.0 |
|---|---|
| H01/H02/H16 | Contratos de resultado, dispatch y límites de capacidades corregidos; aura usa el registro/política común. |
| H03 | Observación separada de confianza; confirmación post-evento y revalidación de ancla implementadas. |
| H04 | Sin guarda heredada de un ataque cancelado; daño diferido requiere actionId del host. |
| H05 | Reloj y ventanas sin presuponer origen positivo; límites y rollover cubiertos. |
| H06 | Captura de envío, ID exacta y diagnóstico implementados; ampliar pruebas de interleavings de host. |
| H07 | Predictor vectorial e impulso del servidor implementados; antiknockback y medios especiales sólo experimentales. |
| H08 | Exenciones por check/causa; medio físico no desactiva automáticamente phase. |
| H09/H10 | Barrido continuo acotado y factores lentos corregidos; contacto de aterrizaje cubierto. |
| H11 | Historial con calidad, AABB, instancia y generación; discontinuidades sin interpolación inventada. |
| H12 | Objetivo/rango/oclusión separados; rayo de cámara observacional. Corpus real pendiente. |
| H13/H14 | Contexto de dig, atributo de rango y reconciliación implementados; reglas custom permanecen en el host. |
| H15 | Estructura de inventario corregida y secuencias nativas probadas; ampliar matriz de GUIs. |
| H17 | Reserva de control, budgets y notificación acotada implementados; carga real pendiente. |
| H18 | Serialización por jugador e historial coherente implementados; concurrencia probada localmente, sin prueba formal de todos los eventos. |
| H19 | Replay sobre pipeline nativo y fuzz de checks implementados; codec aún cubre sólo parte de familias. |
| H20 | Mitigado: circuit breaker visible y fallbacks conservadores; un check que falla sigue desactivado hasta reinstalar. |
| H21 | Métricas de acciones confirmadas, motivos y utilidad de calidad con denominadores implementados; datos reales pendientes. |
| H22 | Registro/overrides, exclusividad y cleanup reforzados; completar inyección de fallos de bootstrap. |
| H23 | Wrapper/checksum, CI definida, consumidor y artefactos verificables; workflow remoto no ejecutado. |
| H24 | Perfiles y observadores adicionales incorporados; cobertura avanzada todavía parcial. |

## Regresiones del plan T01–T30

Las clases indicadas están en `src/test/java/dev/catac/regression/`, salvo `SynchronizationRegressionTest` en `state/`, `CatACLifecycleTest` en el paquete raíz y `ConsumerTest` en `examples/consumer/`. “Cubierto localmente” describe el escenario automatizado concreto; las variantes reales siguen formando parte de calibración.

| ID | Cobertura | Caso y límite |
|---|---|---|
| T01 | Contrato local | FoundationRegressionTest: 10.000 paquetes irrelevantes no borran evidencia. Dispatch nativo verificado; ampliar mezcla real de combate/chat/control. |
| T02 | Cubierto localmente | FoundationRegressionTest, NativeEngineRegressionTest y AuraDecoyRegressionTest: observación impide acciones aun con buffer alto/KICK; sonda nativa registra evaluación y respeta exención. |
| T03 | Cubierto localmente | AcceptanceRegressionTest y NativeEngineRegressionTest: gracia/lag/skip no confirman predictor; falta de historial explícita. |
| T04 | Cubierto localmente | CorpusAndModelTest y NativeEngineRegressionTest: el delta sospechoso no enseña el siguiente límite ni el ancla. |
| T05 | Cubierto localmente | NativeEngineRegressionTest: MONITOR, formato sin desconexión y reserva de control; kicks de flood son independientes/opt-in. |
| T06 | Cubierto localmente | ActionRegressionTest: ataque A impedido, ataque B con daño, y consumo one-shot de actionId del host. |
| T07 | Cubierto localmente | FoundationRegressionTest y SynchronizationRegressionTest: reloj negativo, rollover y primer probe. |
| T08 | Parcial | IDs viejas/nuevas, timeout y teleport sin confirmación cubiertos. Ampliar cancelación/orden concurrente de todos los envíos de host. |
| T09 | Cubierto localmente | FuzzAndConcurrencyTest: snapshots durante escrituras/resets concurrentes; GeometryRegressionTest: instancia y pose. |
| T10 | Cubierto localmente | GeometryRegressionTest: pared fina en desplazamiento largo y consulta que supera presupuesto. |
| T11 | Parcial | Slab, fence, stairs y corner-slide cubiertos. Todas las variantes de wall/door y shapes custom pendientes. |
| T12 | Cubierto localmente | GeometryRegressionTest y AcceptanceRegressionTest: factor 0,4, hielo y fricción de contactos mixtos. |
| T13 | Cubierto localmente | GeometryRegressionTest: AABB/pose no interpolada y consulta histórica con geometría temporal. |
| T14 | Cubierto localmente | GeometryRegressionTest: discontinuidad de instancia/pose; historial reiniciado y generación explícita. |
| T15 | Cubierto localmente | GeometryRegressionTest y CorpusAndModelTest: historia ausente/fuera de rango no produce reach punitivo. |
| T16 | Cubierto localmente | SynchronizationRegressionTest y CorpusAndModelTest: ID de Pong, vector reconocido, límites y timeout; sin afirmar aplicación física. |
| T17 | Parcial | Objetivo detrás, pared fina, rayo paralelo y oclusión cubiertos. Más casos de visibilidad parcial nativa pendientes. |
| T18 | Parcial | Tool/bloque cambiantes, creativo, instant y tolerancia cubiertos. Ampliar matriz de efectos/modo/instancia durante excavación. |
| T19 | Cubierto localmente | ActionRegressionTest: slots negativos, centinela, botones y fases de drag. |
| T20 | Parcial | Pickup/placement, drag y GUI custom nativos conservan items. Todos los click types y carreras con cierre de ventana pendientes. |
| T21 | Parcial | Flood más Pong/reserva, alertas acotadas y límite de control cubiertos. Carga multiusuario con transporte real pendiente. |
| T22 | Cubierto localmente | FuzzAndConcurrencyTest y NativeEngineRegressionTest: check/provider fallido visible; hardening conserva su independencia. |
| T23 | Parcial | CatACLifecycleTest y FuzzAndConcurrencyTest: exclusividad, stop y workers contra clear. Inyección de todos los fallos de bootstrap pendiente. |
| T24 | Cubierto localmente | FoundationRegressionTest: descriptor/PacketCheck incompatible se rechaza; overrides desconocidos también. |
| T25 | Cubierto localmente | FoundationRegressionTest y AcceptanceRegressionTest: mensaje null/fallido no suma avisos; fallos de callbacks visibles y logs limitados. |
| T26 | Cubierto localmente | FoundationRegressionTest y AcceptanceRegressionTest: resultados distintos; host revierte cancelación; setback confirmado en estado nativo. |
| T27 | Parcial | 10.000 entradas fuzz con seed, codec limitado y mailbox bounded. La exploración de IDs/tiempos/ventanas no es exhaustiva. |
| T28 | Cubierto localmente | NativeEngineRegressionTest, AcceptanceRegressionTest y ActionRegressionTest: cancelación/modificación del host y daño nativo no heredado. |
| T29 | Parcial | FuzzAndConcurrencyTest: perfil traducido limita gameplay a MONITOR. Ningún cliente Geyser real probado. |
| T30 | Cubierto localmente | NativeEngineRegressionTest y AcceptanceRegressionTest: status/rotación sin frame XYZ, soporte real y ground claim cancelado. |

## Trabajo pendiente para una siguiente versión

| Prioridad | Trabajo | Condición para cerrarlo |
|---|---|---|
| P1 | Corpus real consentido de Java 1.21.11 por latencia/mecánica | Sesiones independientes etiquetadas, denominadores, falsos positivos/negativos y abstenciones revisados. |
| P1 | Carga nativa multiusuario con conexión real | CPU, GC, asignaciones, tick p95/p99, budgets y sincronización bajo flood medidos en hardware documentado. |
| P1 | Matrices de colisión, dig e inventarios ampliadas | Todas las variantes/efectos/GUI y casos concurrentes relevantes para el host, con fixtures reproducibles. |
| P1 | Simulación completa de líquidos, climb, web/honey/slime, uso de objetos e impulsos | Modelo temporal explícito, posiciones/velocidades/colisiones verificadas y corpus legítimo/adversarial por medio. |
| P2 | Elytra, riptide, vehículos, vuelo y mecánicas custom | Perfil físico o adaptador del host validado; ningún aprendizaje desde datos sospechosos. |
| P2 | Promoción de knockback/no-slow/descenso/ray | Evidencia temporal suficiente y límite de falsos positivos acordado; nuevas capacidades explícitas tras revisión. |
| P2 | Geyser/traducciones | Clientes reales y corpus propios; mantener gameplay en MONITOR hasta validación. |
| P2 | Scaffold, aim assist, autoclicker y KillAura multiseñal | Diseño específico y correlación de señales con denominadores; no castigar una sola heurística. |
| P2 | Ampliar codec/replay y exportación opt-in | Contexto de mundo/entidades/inventario y versiones de esquema; límites de memoria y privacidad verificados. |
| P2 | Recuperación operativa avanzada | Reinicio controlado de checks y razones de degradación, sin reabrir confianza sobre estado incierto. |

No se implementan detección garantizada de X-ray/freecam ni prevención de datos de mundo ya enviados. Para restringir esa información se necesitan medidas del host sobre exposición de chunks/entidades. Tampoco se incorporan inspección del dispositivo cliente, driver, ML entrenado, ban permanente automático ni una lista universal de “todos los cheats”.

## Activación recomendada

El paquete parte de `MONITOR`, hardening y budgets propios. Activa `SETBACK` por familia después de revisar el corpus del host; `KICK` añade el requisito de evidencia repetida y avisos enviados. El estado experimental no se elimina mediante un cambio de modo. Las guías de [migración](MIGRATION_2_0.md), [integración](DEVELOPER_GUIDE.md) y [calibración](CALIBRATION.md) describen los contratos para operar y revertir.
