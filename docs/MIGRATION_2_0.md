# Migración a CatAC 2.0

Recompila el host y los checks personalizados contra el nuevo JAR. No se garantiza compatibilidad binaria con 1.0: varios records públicos han cambiado y la semántica de aplicación es distinta. Conserva la configuración anterior para poder revertir y cambia una familia por vez.

## Cambios que afectan al host

| Antes | Ahora | Acción de migración |
|---|---|---|
| Coordenadas publicadas para 1.0/JitPack | `dev.catac:catac-core:2.0.0` local | Ejecutar `./mvnw install` o publicar en un repositorio propio; 2.0 no está publicado externamente. |
| Requisitos abiertos de Java/Maven | Java 25; wrapper Maven 3.9.11 | Fijar el toolchain del host y la misma versión de Minestom. |
| Un paquete irrelevante podía devolver `pass()` | `skip()` o dispatch por clase | Declarar `packetTypes()`; usar `pass()` sólo tras comprobar una muestra aplicable. |
| El buffer dependía de pases por paquete | `decayPerSecond` por tiempo | Recalibrar umbrales. El constructor antiguo deriva `legacyDecay * 2` por segundo; no replica la dinámica antigua. |
| Descriptor de cinco argumentos | Descriptor con `CheckCapabilities` | Usar explícitamente `OBSERVE`, `PACKET`, `MOVEMENT`, `CORRECT_ONLY` o `PROTOCOL`. |
| Check de telemetría sin límite real | Capacidad `OBSERVE` impide sanción | No esperar sanciones de inventory.move, ray, aura o familias físicas experimentales. |
| Configuración de corrección incompatible | PacketCheck con corrección se rechaza | Mover la detección a MovementCheck o emitir sólo cancelación del paquete. |
| Modo global podía sancionar al instalar | Predeterminado `MONITOR` | Declarar la activación de familias de forma explícita. |
| Kicks de flood ligados al presupuesto | Kicks de flood requieren `.withKicks(true)` | Mantener drops y calibrar antes de habilitar expulsión. |
| Aviso suprimido contaba para expulsión | Sólo mensajes enviados incrementan avisos | Si suprimes todos los avisos, el kick normal con `warningsBeforeKick > 0` queda bloqueado. |
| Daño armado por ataque nativo cancelado | Sin guarda automática; acción del host explícita | Cancelar la acción antes del daño; para daño diferido usar actionId único y consumir la guarda exacta. |
| Historial devolvía extremos ante tiempo ausente | Calidad explícita y abstención | No tratar historial incompleto como prueba válida de rango. |
| Métricas reflejaban decisiones | Cancel/setback confirmados en tick o entrada posterior | Esperar esa confirmación al leer métricas; `CatViolationEvent.action()` sigue siendo intención. |
| Provider/check fallido se ocultaba | Salud y diagnóstico acumulados | Alertar sobre `active=false`, faults, callbacks y overflows; reinstalar tras corregir un check desactivado. |
| Overrides desconocidos podían ignorarse | Rechazo al construir | Corregir IDs de políticas y modos; no dejar nombres de checks inexistentes. |

Los constructores antiguos de `CheckDescriptor`, `CheckPolicy`, `CatViolationEvent` y algunas políticas se conservan donde es razonable para facilitar recompilación. Eso no implica compatibilidad binaria ni equivalencia de comportamiento.

## Secuencia de actualización

1. Guarda el artefacto y configuración del host anterior. Ejecuta el núcleo y `examples/consumer` con Java 25.
2. Recompila extensiones. El descriptor se captura una vez al registrar; no debe mutar según el jugador. Un check implementa exactamente una interfaz.
3. Configura `clientProfileProvider` con metadatos de la conexión. El predeterminado Java presupone un host vanilla 1.21.11; no atribuyas ese perfil a una traducción de protocolo sin validación.
4. Integra los diagnósticos y conserva callbacks sin bloqueo. Audita mecánicas propias: teleports, impulsos, atributos, vuelo, GUIs y daño diferido.
5. Empieza en `MONITOR`. Hardening, reconciliación y drops por presupuesto permanecen activos; sus políticas tienen controles propios.
6. Reproduce fallos como fixtures y calibra con sesiones etiquetadas. Activa `SETBACK` por check; después considera `KICK` para señales repetidas fiables.
7. Ante regresión, vuelve a `MONITOR` para la familia afectada, corrige el corpus y recompila. Un cambio de configuración requiere crear una instancia nueva: cerrar la anterior y arrancar otra bajo control del host.

## Revisión de extensiones

Un resultado `UNCERTAIN` no es un pase. `uncertainCancel(reason)` reconcilia una acción que no se puede aplicar con seguridad, sin sumar una infracción. `reject(...)` devuelve formato inválido con cancelación; `disconnect(...)` recomienda desconectar sólo si el descriptor es hardening y la configuración lo permite. Elegir `OBSERVE` elimina todas esas acciones.

Un MovementCheck con capacidad de corrección participa en la confianza del modelo. Si se desactiva o falla, la física dependiente deja de aprender candidatos. Un observador desactivado no bloquea el modelo principal. No uses un check meramente estadístico como validador obligatorio del predictor.
