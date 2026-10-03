package dev.catac.internal;

import dev.catac.state.CollisionSnapshot;

import net.minestom.server.entity.*;
import net.minestom.server.potion.PotionEffect;

/** Geometry and uncertain physical media have different exemption scopes. */
public final class MovementExemptions {
    private MovementExemptions() {}

    public static boolean geometryBypass(Player p) {
        return p.getGameMode() == GameMode.SPECTATOR;
    }

    public static boolean physicalBypass(Player p, CollisionSnapshot c) {
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE)
            return true;
        if (p.isAllowFlying() || p.isFlying() || p.isFlyingWithElytra() || p.getVehicle() != null)
            return true;
        if (p.hasEffect(PotionEffect.DOLPHINS_GRACE)) return true;
        var meta = p.getLivingEntityMeta();
        if (meta != null && meta.isInRiptideSpinAttack()) return true;
        return !c.complete()
                || c.touchingLiquid()
                || c.touchingClimbable()
                || c.touchingSlowBlock();
    }
}
