# Integración y desarrollo

## Ciclo de vida y configuración

Inicializa Minestom, construye CatAC, registra checks adicionales antes de `build()`, llama a `start()` y conserva la referencia para `close()`. `CatAC.install(config)` es el atajo. Sólo una instancia puede estar activa; start/stop están serializados. Un bootstrap fallido limpia su EventNode y estado y deja la instancia detenida. Para reinstalar, crea otra instancia.

La configuración es inmutable. Los overrides se validan contra el registro congelado. No existen hot reload, comandos de moderación, base de datos ni panel web incorporados: corresponden al host. El logger del host debe proporcionar su binding de SLF4J; los tests aislados no añaden uno al JAR.

Configura el perfil desde metadatos verificados de la conexión:

```java
.clientProfileProvider(player -> trustedHostMetadata.profileOf(player))
```

Ese identificador representa una función propia del host. No uses brand ni un plugin message enviado por el cliente como prueba del perfil. Si no conoces la compatibilidad, devuelve `ClientProfile.UNKNOWN` y recoge evidencia en monitorización. Las exenciones específicas se solicitan con `catac.exempt(player, checkId, duration)`; no eximen hardening.

## Extensiones

Un check implementa exactamente `PacketCheck` o `MovementCheck`. Define un descriptor constante y capacidades explícitas. El registro invoca `descriptor()` una vez y cachea `packetTypes()`; evita resultados variables. Los checks se ejecutan bajo el estado del jugador y deben ser baratos y sin I/O.

```java
public final class HostObserver implements PacketCheck {
    private static final CheckDescriptor INFO = new CheckDescriptor(
            "host.observer", "Host observer", CheckCategory.PACKET,
            CheckPolicy.standard(2, 4, 8), false, CheckCapabilities.OBSERVE);

    @Override public CheckDescriptor descriptor() { return INFO; }

    @Override public Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of(ClientHeldItemChangePacket.class);
    }

    @Override public CheckResult evaluate(PlayerPacketEvent event,
                                         PlayerData data, long nowNanos) {
        return CheckResult.skip(); // Sustituir por una evaluación del host.
    }
}
```

El ejemplo consumidor contiene una extensión compilada contra el JAR. Para un fallo numérico, `CheckEvidence(observed, limit, tolerance, model)` usa esquema 1 y números finitos; sólo debe crearse ante detección. Conserva IDs/modelos estables. `skip()` significa que no aplicó; `uncertain(reason)` que faltó confianza; ninguno es un pase.

No teletransportes, kicks o mutaciones de inventario desde `evaluate()`: la capacidad declarada debe gobernar la aplicación. Las extensiones son código confiable del host, no un sandbox que impida realizar llamadas directas a Minestom. No conserves `MovementFrame` mutable, `PlayerData` o eventos para procesarlos en un worker. Exporta snapshots propios inmutables a una cola acotada del host.

Si un check lanza una excepción, se desactiva globalmente para esa instancia. Consulta `diagnostics()` y corrige el fallo antes de reinstalar. No asumas que `health().healthy()` demuestra que todos los checks siguen activos: los estados se consultan por separado.

## Orden de eventos y acciones

CatAC evalúa el destino antes de que Minestom lo confirme. Otro listener puede cancelarlo o modificarlo; sólo el destino original que termina aplicado alimenta el modelo. Una modificación del host no convierte automáticamente el nuevo destino en una muestra validada. Cancela o usa la API nativa de teleport cuando el host pretende una transición autoritativa.

`CatViolationEvent.action()` y `DetectionTrace.decision()` son decisiones. Los contadores de cancelación y setback se confirman en la siguiente entrada/EntityTick del jugador. Una cancelación revertida por un listener posterior no incrementa acciones aplicadas. Los listeners que deban observar eventos ya cancelados necesitan la opción nativa `.ignoreCancelled(false)`; no reviertas hardening de forma indiscriminada.

Las acciones de mundo canceladas envían actualización de target/vecino y acknowledgment de secuencia; inventarios obsoletos o inválidos se actualizan desde el estado nativo. CatAC no ejecuta el click de nuevo. El host sigue siendo responsable de permisos de construcción, reglas de sus GUIs, ownership de objetos y acciones custom que no pasan por esos listeners.

## Daño propio y diferido

Si el host impide una acción antes de aplicar daño, no necesita armar una guarda para el ataque que nunca ejecutará. Para daño que se ejecutará después, crea un actionId positivo y único por atacante/sesión, vincula la invalidación y consume esa misma acción antes de aplicar daño:

```java
catac.denyDamage(attacker, victim, actionId,
        Duration.ofMillis(500), "host.invalid-action");
// En el ejecutor del host, dentro de la duración acordada:
boolean denied = catac.consumeDamageDenial(attacker, victim, actionId);
if (!denied) {
    // Aplicar exclusivamente el daño correspondiente a actionId.
}
```

La guarda es one-shot, acotada y validada antes de modificar estado. No reutilices IDs ni interpretes `false` tras expiración como aprobación del antitrampas: sólo indica ausencia de esa guarda vigente. El host debe conservar su decisión autoritativa si la tarea puede exceder la duración.

El overload legado `denyDamage(attacker, victim, duration, reason)` protege el siguiente `EntityDamageEvent` síncrono coincidente; se limpia al inicio del siguiente ataque nativo. **No lo uses para tareas diferidas:** un evento de daño no contiene por sí mismo identidad del ataque. El overload correlacionado no se consume mediante ese evento genérico.

`DamageProtectionPolicy` decide sobre guardas explícitas del host en la ruta síncrona. Si el proveedor falla, el estado degradado es visible. Los proveedores/callbacks deben permanecer sin bloqueo y no adquirir el estado de otro jugador mientras tienen el actual.

## Flood y disponibilidad

El presupuesto actúa tras decodificar el paquete y antes de su lógica nativa de juego. No limita por sí mismo bytes de login, framing, compresión, memoria de decodificación o ataques de red anteriores al core.

| Presupuesto predeterminado | Reposición por segundo | Burst |
|---|---:|---:|
| Total normal | 160 | 240 |
| Adicional para paquetes pesados | 24 | 40 |
| Reserva de control independiente | 32 | 24 |

Control incluye Pong, confirmación de teleport, KeepAlive y acuse de batch de chunks. Se conserva incluso cuando agotan los buckets de gameplay. También es acotado; no admite una inundación ilimitada de controles. El callback/evento de flood tiene cooldown de 1 s predeterminado; no se publica una alerta por cada descarte. Kicks de flood están desactivados por defecto y se habilitan expresamente con `.withKicks(true)`.

`PacketCostClassifier` debe ser una comprobación de tipo o metadatos baratos. Un error vuelve a NORMAL y degrada la salud; no concede una exención. No coloques logs, SQL, HTTP o una cola sin límite en el handler. Las excepciones de callbacks se contabilizan y los logs se limitan por categoría a una vez cada 5 s.

## Señuelo y observadores

`AuraDecoyPolicy` está desactivada de fábrica. Al activarla, la sonda privada utiliza paquetes dirigidos sólo al sospechoso; no crea un objetivo real en la instancia. Su check sigue la política registrada y `OBSERVE`, incluso en modo `KICK`; su antigua ruta especial de expulsión ya no se usa. Un ataque al ID virtual se impide porque no existe un objetivo nativo legítimo.

Los checks ray, medio, descenso, no-slow y knockback también son observadores. No basta cambiar sus umbrales/modo para convertirlos en sancionadores. Una futura promoción exige un modelo y corpus propios, revisión de falsos positivos y un descriptor con nuevas capacidades explícitas.

## Operación y reversión

1. Empieza en `MONITOR`; recoge denominadores de sesiones además de alertas. Consulta salud, estados activos, motivos de abstención y overflows.
2. Comprueba que teleports, velocidades, atributos y GUIs propios no produzcan errores. Añade fixtures al corpus y fija la configuración del host.
3. Activa por familia `SETBACK` con un grupo controlado. Investiga cada corrección legítima y compara decisiones con acciones confirmadas.
4. Sólo habilita `KICK` para evidencia repetida de checks validados, sin desactivar los avisos por accidente. Los valores de fábrica no son una calibración de tu servidor.
5. Ante fallos de check, inflación de incertidumbre, overflows o aumento de acciones sobre sesiones legítimas: desciende la familia a monitorización, conserva un caso reproducible y corrige antes de promover otra vez.

Para revertir un cambio de versión, cierra CatAC, sustituye el JAR/configuración y reinicia el host de forma controlada. No mezcles clases 1.x/2.x ni cambies Minestom sin volver a ejecutar el consumidor y las regresiones.
