# Cambios

## 2.0.0 — 2026-10-03

Versión mayor por cambios en contratos de resultados, capacidades, política y records públicos. Objetivo fijado: Java 25 y Minestom `2026.05.11-1.21.11`.

- Resultados `NOT_APPLICABLE`, `UNCERTAIN`, `PASS`, `FAIL` y `MALFORMED`; dispatch por tipos y evidencia numérica versionada.
- Capacidades independientes de cancelación, corrección, expulsión y hardening. Modo global `MONITOR`, overrides por check y perfiles del host.
- Decaimiento temporal de buffers, ventanas de incidentes y avisos contados sólo cuando se envían. Sin kick normal por una sola detección.
- Reloj inyectable, ventanas compatibles con origen negativo y rollover; estado de jugador serializado y snapshots de historial sincronizados.
- Captura acotada de teletransporte y velocidad enviados; correlación exacta de IDs, control reservado ante flood y diagnósticos de timeout.
- Predictor alimentado sólo con candidatos validados y confirmados en el estado nativo final. Anclas con instancia y revalidación geométrica.
- Colisión continua con shapes nativas, presupuesto explícito, rutas por ejes y step; factores lentos conservados y tolerancia de contacto al aterrizar.
- Envolvente de velocidad vectorial, atributos de movimiento/salto y efectos modelados; calidad explícita para medios no modelados.
- Rewind acotado con instancia, generación, pose/AABB y calidad temporal. Validación independiente de objetivo, rango, oclusión y rayo.
- Excavación ligada a bloque, herramienta y contexto; interacción de bloques según atributo, con reconciliación de acciones canceladas.
- Inventario estructural: slots negativos, botones, drag y ventanas; el click válido sigue la ruta autoritativa nativa sin duplicar operaciones.
- Guarda de daño por identidad de acción del host; eliminación del armado automático para ataques nativos cancelados.
- Circuit breaker de checks e integraciones con degradación observable, logs limitados, métricas de acción confirmada y trazas opcionales acotadas.
- Checks experimentales de medio, descenso, no-slow, knockback y rayo; límites permanentes de observación. Señuelo de aura integrado en la política común.
- Replay conectado al pipeline nativo, corpus versionado, fuzz reproducible, regresiones de concurrencia, benchmark local y consumidor externo del JAR.
- Maven wrapper con checksum, compilación estricta, JAR reproducible y workflow de CI.

Consulta [MIGRATION_2_0.md](docs/MIGRATION_2_0.md) antes de sustituir el artefacto en un host existente. Los pendientes empíricos y las familias no implementadas están en [ESTADO_IMPLEMENTACION.md](docs/ESTADO_IMPLEMENTACION.md).

## Historial anterior conservado

Las entradas siguientes describen versiones anteriores; sus contratos pueden haber cambiado en 2.0.

## 1.0.0 — protección de inundación de paquetes

- `PacketFloodPolicy` con token buckets por jugador para el total de paquetes y
  para paquetes costosos; descarta antes del listener vanilla de Minestom.
- Clasificación extensible con `PacketCostClassifier`: los paquetes custom son
  normales por defecto para no interferir con servidores propios.
- Strikes acotados, expulsión configurable, `PacketFloodEvent`, callback aislado
  y métricas públicas `floodDrops`/`floodKicks`.
- Documentado el límite explícito: CatAC protege lógica Play ya decodificada;
  proxy/firewall deben proteger bytes, compresión y conexiones previos.

## 0.11.0-SNAPSHOT — barrera de daño cancelable

- `DamageProtectionPolicy` y `DamageDecisionProvider` permiten denegar o
  permitir mediante API el daño vinculado a una acción de combate invalidada.
- Los ataques de entidad cancelados arman una guarda de un solo uso para el UUID
  de la víctima; `EntityDamageEvent` se cancela antes de afectar su vida.
- API pública `CatAC.denyDamage(attacker, victim, duration, reason)` para
  checks e integraciones personalizados sin manipular salud directamente.
- Estado acotado por atacante y validaciones tipadas al iniciar CatAC.

## 0.10.0-SNAPSHOT — sonda privada de KillAura

- `AuraDecoyPolicy` con umbral de activación, cooldown, armado, vida, distancia,
  geometría trasera y mensaje de kick configurables por API.
- `AuraDecoyManager` envía un jugador señuelo mediante paquetes sólo a la
  conexión sospechosa; no registra una entidad ni lo expone a otros jugadores.
- La confirmación exige ataque al ID armado, efímero y detrás de la cámara.
  Respeta `MONITOR`/`SETBACK`; sólo expulsa en modo `KICK`.
- Limpieza garantizada del ID y del perfil al vencer, desconectar o cerrar CatAC.

## 0.9.0-SNAPSHOT — seguridad y experiencia del jugador

- Avisos de jugador contextuales con `PlayerMessageProvider`, configurables por
  API y limitados por check/jugador para no saturar el chat.
- Puerta de seguridad antes de un kick normal: por defecto requiere dos avisos
  del mismo check; los paquetes malformados se mantienen como excepción.
- Nuevos ajustes `warningsBeforeKick` y `playerNoticeCooldown`; el texto de
  kick sigue siendo configurable con `kickMessage`.
- Exenciones vanilla reforzadas para levitación, caída lenta y gracia del
  delfín, además de las exenciones de movimiento ya existentes.
- Menos decisiones agresivas: ángulo de ataque desfasado se abstiene, línea de
  visión bloqueada acumula evidencia y alcance de bloque no cancela de golpe.
- Interacciones de mundo se abstienen con chunks ausentes o spectator.

## 0.8.0-SNAPSHOT — validación y calibración

- `ReplayRunner` y `DeterministicClock` para escenarios con orden y tiempo
  reproducibles, sin depender del reloj del servidor.
- `PacketFuzzer` determinista por seed con casos de coordenadas, cursores,
  ventanas y slots hostiles.
- `HotPathBenchmark` con promedio, p50 y p95 para comparaciones locales.
- `CalibrationAnalyzer` para agregar muestras de monitor por check.
- Nueva guía `docs/CALIBRATION.md` con flujo de despliegue y criterios antes de
  endurecer enforcement.
- Pruebas de replay, reloj, fuzzing y agregación de calibración.

## 0.7.0-SNAPSHOT — mundo, inventario y telemetría

- `world.interaction`: valida alcance de bloques y cursores de placement no
  finitos o fuera de rango antes de modificar el mundo.
- `inventory.invalid-click`: valida ventana, slot, hotbar y swap imposibles sin
  sustituir el procesador de inventario nativo de Minestom.
- `inventory.move`: señal acumulativa de telemetría, sin cancelación automática.
- Nueva categoría `INVENTORY` para políticas por check.
- Métricas públicas `CatAC.metrics()` con muestras, alertas, cancelaciones,
  setbacks, kicks y jugadores rastreados.
- Opción `.telemetryEnabled(false)` para eliminar el coste de los contadores.
- Pruebas de métricas y configuración de telemetría.

## 0.5.0-SNAPSHOT — combate temporal

- Historial circular de 32 posiciones para entidades instanciadas, con limpieza
  al despawnear y protección contra reutilización de IDs.
- Rewind interpolado para combate: utiliza `padding + RTT/2 + jitter` del
  atacante y aplica un máximo configurado de 350 ms.
- `combat.reach` ya no selecciona la posición más favorable de una ventana.
- Validación adicional de dirección de vista y línea de visión contra formas de
  colisión, absteniéndose si faltan chunks.
- Nuevos ajustes `combatRewindPadding` y `combatMaxRewind`.
- Pruebas de interpolación, geometría de hitbox y ángulo de ataque.

## 0.4.0-SNAPSHOT — predictor y colisiones

- Modelo de movimiento compartido por jugador: inercia, fricción, aceleración,
  gravedad, drag, sneak y salto con `JUMP_BOOST`.
- Los checks de velocidad horizontal, vertical/fly y ground-spoof consumen el
  mismo límite predictivo en lugar de topes aislados.
- Barrido de AABB por subpasos de 0,20 bloques con máximo de 32 pasos para
  detectar atravesar formas sólidas entre el origen y destino.
- Recorridos que tocan chunks no cargados se consideran incompletos y no se
  usan para sancionar.
- Pruebas unitarias del modelo de inercia, sneak y gravedad.

## 0.3.0-SNAPSHOT — sincronización temporal

- Sondas estándar Ping/Pong de Minestom para RTT y jitter suavizados por jugador.
- API pública `CatAC.networkSnapshot(player)` para exponer estado de red acotado.
- Confirmación exacta de teletransportes mediante el ID nativo de Minestom.
- Cola fija de velocidades pendientes, armada con un Ping posterior al tick de
  la velocidad para respetar el orden de red.
- Los checks de movimiento se abstienen durante sincronización pendiente y
  vuelven a operar al confirmar o al vencer un timeout configurable.
- Nuevas opciones `networkProbeInterval` y `networkAcknowledgementTimeout`.
- Pruebas de RTT/jitter, expiración, teletransporte y velocidades pendientes.

## 0.2.0-SNAPSHOT — núcleo endurecido

- Renombrado público a CatAC (`dev.catac`, `CatAC`, `CatACConfig`, `CatCheck`).
- Instalación global única y ciclo de vida explícito `NEW → STARTED → STOPPED`.
- Registro de checks validado de forma atómica y congelado después de construir el motor.
- Aislamiento: un check que lance una excepción se desactiva una sola vez sin interrumpir el servidor.
- Aislamiento del proveedor de exenciones y de listeners externos de infracciones.
- Limpieza segura de estados ante sesiones distintas con el mismo UUID.
- Límite de tamaño para evidencia procedente de checks externos.

## 0.1.0-SNAPSHOT — núcleo inicial

- API embebible con ciclo de vida explícito (`start`, `stop`, `close`).
- Configuración inmutable, políticas por check, exenciones y callback de infracciones.
- Estado compacto por jugador, buffers con decaimiento y posición segura.
- Análisis compartido de colisiones y compensación de lag del servidor.
- Checks iniciales de paquetes inválidos, timer, velocidad horizontal, física vertical,
  ground spoof, phase, reach con rewind y fast-break.
- Extensión mediante `MovementCheck` y `PacketCheck` personalizados.
- Pruebas unitarias para configuración, buffers e historial de posiciones.
