package dev.catac.internal;

import dev.catac.state.CollisionSnapshot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.potion.PotionEffect;

public final class MovementExemptions {
    private MovementExemptions() {
    }

    public static boolean physicalBypass(Player player, CollisionSnapshot collision) {
        GameMode gameMode = player.getGameMode();
        if (gameMode != GameMode.SURVIVAL && gameMode != GameMode.ADVENTURE) {
            return true;
        }
        if (player.isAllowFlying() || player.isFlying() || player.isFlyingWithElytra() ||
                player.getVehicle() != null) {
            return true;
        }
        // These vanilla mechanics either replace normal gravity or make the
        // server-side motion envelope intentionally too broad to judge fairly.
        if (player.hasEffect(PotionEffect.LEVITATION) || player.hasEffect(PotionEffect.SLOW_FALLING) ||
                player.hasEffect(PotionEffect.DOLPHINS_GRACE)) {
            return true;
        }
        LivingEntityMeta meta = player.getLivingEntityMeta();
        if (meta != null && meta.isInRiptideSpinAttack()) {
            return true;
        }
        return !collision.complete() || collision.touchingLiquid() ||
                collision.touchingClimbable() || collision.touchingSlowBlock();
    }
}
