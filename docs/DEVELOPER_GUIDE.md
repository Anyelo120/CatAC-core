# Guía de desarrollo

## Ciclo de vida

1. Inicializa Minestom.
2. Construye una única instancia de `CatAC`.
3. Registra checks personalizados antes de `build()`.
4. Llama a `start()` antes de aceptar jugadores.
5. Conserva la referencia y llama a `close()` al apagar.

`CatAC.install(config)` es el atajo para construir e iniciar. No debe instalarse
dos veces sobre el mismo servidor. La instancia sigue el estado `NEW → STARTED
→ STOPPED`; después de `close()` no puede reiniciarse. Construye una instancia
nueva si el host necesita volver a instalar CatAC.

## Estrategia de despliegue

Empieza con `EnforcementMode.MONITOR` y exporta `CatViolationEvent`. Separa los
datos por check, ping, modo de juego y mecánicas propias. Ajusta políticas sólo
después de observar percentiles de jugadores legítimos. Activa `SETBACK` por
familias; reserva `KICK` para señales repetidas y de alta confianza.

## Avisos al jugador

Los avisos de CatAC son intencionalmente escasos: se limitan por jugador y por
check, con cuatro segundos por defecto. Antes de un kick normal, CatAC exige
dos avisos para ese mismo check; los paquetes estructuralmente malformados
siguen siendo una excepción de seguridad. El host controla ambos textos y puede
silenciar casos concretos con `null`:

```java
.warningsBeforeKick(2)
.playerNoticeCooldown(Duration.ofSeconds(5))
.playerMessageProvider(notice -> switch (notice.type()) {
    case WARNING -> Component.text("Detectamos una acción irregular." );
    case SETBACK -> Component.text("Tu movimiento fue corregido." );
})
.kickMessage(Component.text("No pudimos validar varias acciones."))
```

No uses el proveedor para I/O, bases de datos o formatos costosos: se ejecuta
en el flujo del evento. Para mensajes por idioma, selecciona el `Component`
según el locale que el servidor ya conserve para ese jugador.

## Señuelo de KillAura

`AuraDecoyPolicy` activa una sonda privada sólo después de un `combat.reach`
anómalo. CatAC emite `PlayerInfoUpdatePacket` y `SpawnEntityPacket` únicamente
al sospechoso, con una entrada no listada, y después destruye el ID y elimina el
perfil. No registra un `Entity` en la instancia, por lo que ningún otro jugador
ni mecánica del mundo puede interactuar con el señuelo.

La confirmación requiere simultáneamente: ID exacto, tipo `Attack`, período de
armado completado, vida vigente y vector de cámara aún opuesto al señuelo. No
reduzcas `behindDistance` por debajo de 3.5 bloques ni omitas el armado. Respeta
`EnforcementMode`: en `MONITOR` y `SETBACK` la señal se publica como alerta; en
`KICK` usa el `kickMessage` propio de la política.

## Denegar daño protegido

`DamageProtectionPolicy` escucha `EntityDamageEvent` de Minestom. Sólo evalúa
el daño si CatAC había armado antes una guarda para ese atacante y esa víctima;
por defecto la decisión es `DENY`. El proveedor permite que el servidor
mantenga la autoridad final:

```java
.damageProtectionPolicy(new DamageProtectionPolicy(
        true,
        Duration.ofMillis(500),
        context -> context.reason().equals("combat.reach")
                ? DamageDecision.DENY : DamageDecision.ALLOW))
```

Los checks personalizados no deben intentar alterar la salud directamente.
Después de invalidar su propia acción pueden llamar
`catac.denyDamage(attacker, victim, duration, "custom.check-id")`. La razón
debe ser un ID estable (`segmento.segmento`); la guarda coincide con el UUID de
la víctima y se consume una sola vez.

## Inundación de paquetes

`PacketFloodPolicy` es una barrera previa a los listeners vanilla de Minestom,
no un reemplazo de su red. Mantiene dos token buckets por jugador y no crea
colas ni tareas: `totalBudget` para todo paquete de Play y `heavyBudget` para
inventario, plugin messages, libros, autocomplete y edición de carteles. Al
agotarse, CatAC cancela el paquete; al superar `strikesBeforeKick` en
`strikeWindow`, expulsa.

La clasificación se ejecuta en el hot path. Debe ser una comprobación de tipo
constante, sin mapas, I/O o logs. Los paquetes propios quedan en `NORMAL` por
defecto; usa `HEAVY` sólo cuando tu implementación tenga procesamiento costoso.
El callback `PacketFloodHandler` y `PacketFloodEvent` se producen únicamente
en una violación y también deben permanecer sin bloqueo.

No intentes usar esta API para interceptar bytes comprimidos o paquetes antes
de que Minestom los decodifique: eso corresponde al proxy/firewall del host. La
protección de CatAC asegura que el paquete de Play ya decodificado no alcance
la lógica de juego de Minestom.

## Políticas por check

```java
new CheckPolicy(
        true,  // enabled
        4.0,   // alertBuffer
        8.0,   // setbackBuffer
        24.0,  // kickBuffer
        0.20,  // decayPerPass
        1_000  // alertCooldownMillis
)
```

Una política personalizada se enlaza por ID:

```java
CatACConfig.builder()
        .policy("combat.reach", CheckPolicy.standard(2, 5, 20))
        .disableCheck("movement.vertical")
        .build();
```

## Exenciones

Usa `ExemptionProvider` para permisos, arenas, estados de minijuego o NPCs:

```java
.exemptionProvider((player, checkId) ->
        player.hasPermission("catac.bypass") ||
        myGameState.isInCinematic(player))
```

Para teletransportes o impulsos personalizados, usa además:

```java
catac.exempt(player, Duration.ofMillis(750));
```

CatAC ya observa los teletransportes y velocidades producidos por Minestom. La
exención manual sólo es necesaria para mecánicas personalizadas que no generen
esos eventos. Puedes consultar `catac.networkSnapshot(player)` para telemetría;
no conserves ese snapshot como estado de juego porque representa una lectura.

No desactives un check globalmente para resolver una mecánica local: expresa la
exención en el punto donde el servidor conoce esa mecánica.

## Eventos

Hay dos vías equivalentes para consumir una infracción:

- `CatACConfig.Builder.violationHandler(...)`, útil para una integración directa;
- listener global de Minestom para `CatViolationEvent`.

El evento incluye jugador, descriptor, severidad de la muestra, buffer acumulado,
evidencia y acción resuelta. La evidencia sirve para depuración; no la uses como
formato estable de persistencia.

## Calibración de combate

Empieza con los valores predeterminados de `combatRewindPadding` (50 ms) y
`combatMaxRewind` (350 ms). El rewind usa la mitad del RTT y el jitter medidos
por CatAC para elegir un instante objetivo; no incrementa
`ENTITY_INTERACTION_RANGE`. No subas el máximo para resolver falsos positivos
sin revisar primero pings reales, historial de entidades, paredes y la
configuración de visualización de tus instancias.

## Observabilidad

Usa `catac.metrics()` para decidir ajustes de `CheckPolicy` y del modo de
enforcement. Empieza con `MONITOR`, observa `violationSamples` y `alerts` por
check a través de tu `ViolationHandler`, y recién entonces configura acciones
de cancelación, setback o kick. Las métricas son acumulativas durante la vida
de la instancia; reiniciar CatAC inicia una serie nueva.

## Checks personalizados

Un check debe:

- tener un ID estable y único;
- ser determinista y no bloquear;
- evitar streams, colecciones temporales, logs síncronos e I/O en `evaluate`;
- usar el contexto existente antes de volver a consultar el mundo;
- devolver `pass()` ante una situación física que no puede modelar;
- reservar `cancel()` para una acción que no debe llegar al juego;
- reservar `disconnect()` para entradas hostiles que rompen invariantes.

Cada check debe implementar exactamente una interfaz, `PacketCheck` o
`MovementCheck`. CatAC desactiva un check que lance una excepción para no dejar
que una extensión interrumpa el hilo de juego. Esta es una protección de
disponibilidad, no una sustitución de pruebas: corrige el check y revisa el log.

El callback de infracciones y los listeners de `CatViolationEvent` se ejecutan
en el flujo del evento. Deben ser cortos y sin I/O bloqueante; entrega el trabajo
pesado a una cola o executor controlado por el servidor.

Los `MovementCheck` se ejecutan con un `MovementFrame` reutilizado. No conserves
una referencia al frame fuera de `evaluate`. Si necesitas memoria adicional,
propón primero extender `PlayerData` con almacenamiento acotado; no uses mapas
globales sin limpieza.

## Pruebas

```bash
mvn clean verify
```

Para un check nuevo añade, como mínimo:

- casos justo por debajo y por encima del umbral;
- decaimiento y acumulación;
- ping alto y lag del servidor;
- teletransporte, velocity y chunk incompleto;
- mecánicas de bloque relevantes;
- ausencia de acción en `MONITOR`.

Las pruebas de unidad no reemplazan capturas de tráfico legítimo. Usa el
paquete `dev.catac.testing` para reproducir una secuencia con marcas de tiempo
deterministas, generar entradas hostiles y resumir muestras de calibración sin
añadir trabajo al hilo de juego. La guía completa está en
[`CALIBRATION.md`](CALIBRATION.md).
