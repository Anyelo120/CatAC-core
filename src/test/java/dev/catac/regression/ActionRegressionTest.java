package dev.catac.regression;

import static org.junit.jupiter.api.Assertions.*;

import dev.catac.CatAC;
import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.check.builtin.*;
import dev.catac.config.*;
import dev.catac.engine.CatEngine;
import dev.catac.internal.*;
import dev.catac.state.*;
import dev.catac.support.MinestomFixture;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.*;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.EntityDamage;
import net.minestom.server.event.*;
import net.minestom.server.event.entity.*;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.block.*;
import net.minestom.server.inventory.*;
import net.minestom.server.item.*;
import net.minestom.server.network.packet.client.play.*;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.potion.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ActionRegressionTest {
    private static ClientClickWindowPacket click(
            int slot, int button, ClientClickWindowPacket.ClickType type) {
        return new ClientClickWindowPacket(
                0,
                0,
                (short) slot,
                (byte) button,
                type,
                Map.of(),
                ItemStack.Hash.of(ItemStack.AIR));
    }

    private static ClientPlayerActionPacket dig(ClientPlayerActionPacket.Status status, int x) {
        return new ClientPlayerActionPacket(status, new Vec(x, 1, 0), BlockFace.NORTH, 1);
    }

    @Test
    void everyNegativeInventorySlotExceptTheOutsideSentinelIsRejected() {
        for (int slot = -1200; slot < 0; slot++)
            if (slot != -999)
                for (var type : ClientClickWindowPacket.ClickType.values())
                    assertFalse(InventorySanityCheck.validSlotAndButton(slot, 0, type, 46));
        assertTrue(
                InventorySanityCheck.validSlotAndButton(
                        -999, 0, ClientClickWindowPacket.ClickType.PICKUP, 46));
        assertFalse(
                InventorySanityCheck.validSlotAndButton(
                        -999, 0, ClientClickWindowPacket.ClickType.SWAP, 46));
    }

    @Test
    void buttonsAreCheckedByClickTypeAndDragPhase() {
        for (var type : ClientClickWindowPacket.ClickType.values())
            for (int button = -128; button < 128; button++) {
                boolean valid = InventorySanityCheck.validSlotAndButton(12, button, type, 46);
                if (type == ClientClickWindowPacket.ClickType.SWAP)
                    assertEquals(button >= 0 && button <= 8 || button == 40, valid);
                if (type == ClientClickWindowPacket.ClickType.CLONE)
                    assertEquals(button == 2, valid);
                if (type == ClientClickWindowPacket.ClickType.QUICK_CRAFT)
                    assertEquals(button == 1 || button == 5 || button == 9, valid);
            }
        assertTrue(
                InventorySanityCheck.validSlotAndButton(
                        -999, 10, ClientClickWindowPacket.ClickType.QUICK_CRAFT, 46));
    }

    @Test
    void staleWindowIsCancelledWithoutScoringACheat() {
        try (var f = new MinestomFixture();
                var ac = CatAC.install(f.config().build())) {
            var p =
                    new ClientClickWindowPacket(
                            17,
                            0,
                            (short) 1,
                            (byte) 0,
                            ClientClickWindowPacket.ClickType.PICKUP,
                            Map.of(),
                            ItemStack.Hash.of(ItemStack.AIR));
            MinecraftServer.getPacketListenerManager().processClientPacket(p, f.connection);
            EventDispatcher.call(new EntityTickEvent(f.player));
            assertEquals(0, ac.metrics().violationSamples());
            assertEquals(1, ac.metrics().cancelledPackets());
            assertTrue(
                    f.connection.packets.stream()
                            .anyMatch(packet -> packet instanceof WindowItemsPacket));
        }
    }

    @Test
    void nativePickupAndPlacementConserveItems() {
        try (var f = new MinestomFixture();
                var ac = CatAC.install(f.config().build())) {
            f.player.getInventory().setItemStack(0, ItemStack.of(Material.STONE, 10));
            MinecraftServer.getPacketListenerManager()
                    .processClientPacket(
                            click(36, 0, ClientClickWindowPacket.ClickType.PICKUP), f.connection);
            assertEquals(10, f.player.getInventory().getCursorItem().amount());
            assertTrue(f.player.getInventory().getItemStack(0).isAir());
            MinecraftServer.getPacketListenerManager()
                    .processClientPacket(
                            click(37, 0, ClientClickWindowPacket.ClickType.PICKUP), f.connection);
            assertEquals(10, f.player.getInventory().getItemStack(1).amount());
            assertTrue(f.player.getInventory().getCursorItem().isAir());
            assertEquals(0, ac.metrics().violationSamples());
        }
    }

    @Test
    void nativeDragSequenceConservesItemsWithoutADuplicateTransactionEngine() {
        try (var f = new MinestomFixture();
                var ac = CatAC.install(f.config().build())) {
            f.player.getInventory().setItemStack(0, ItemStack.of(Material.STONE, 10));
            for (var p :
                    List.of(
                            click(36, 0, ClientClickWindowPacket.ClickType.PICKUP),
                            click(-999, 0, ClientClickWindowPacket.ClickType.QUICK_CRAFT),
                            click(37, 1, ClientClickWindowPacket.ClickType.QUICK_CRAFT),
                            click(38, 1, ClientClickWindowPacket.ClickType.QUICK_CRAFT),
                            click(-999, 2, ClientClickWindowPacket.ClickType.QUICK_CRAFT)))
                MinecraftServer.getPacketListenerManager().processClientPacket(p, f.connection);
            assertEquals(5, f.player.getInventory().getItemStack(1).amount());
            assertEquals(5, f.player.getInventory().getItemStack(2).amount());
            assertTrue(f.player.getInventory().getCursorItem().isAir());
            assertEquals(0, ac.metrics().violationSamples());
        }
    }

    @Test
    void customContainerClicksUseTheNativeContainerSlotMapping() {
        try (var f = new MinestomFixture();
                var ac = CatAC.install(f.config().build())) {
            var inventory = new Inventory(InventoryType.CHEST_1_ROW, "Custom");
            inventory.setItemStack(0, ItemStack.of(Material.DIAMOND, 7));
            f.player.openInventory(inventory);
            var packet =
                    new ClientClickWindowPacket(
                            inventory.getWindowId(),
                            0,
                            (short) 0,
                            (byte) 0,
                            ClientClickWindowPacket.ClickType.PICKUP,
                            Map.of(),
                            ItemStack.Hash.of(ItemStack.AIR));
            MinecraftServer.getPacketListenerManager().processClientPacket(packet, f.connection);
            assertEquals(7, f.player.getInventory().getCursorItem().amount());
            assertTrue(inventory.getItemStack(0).isAir());
            assertEquals(0, ac.metrics().violationSamples());
        }
    }

    @Test
    void worldReachUsesTheServerAttribute() {
        try (var f = new MinestomFixture()) {
            var check = new WorldInteractionCheck();
            var data = f.data(1);
            var packet = dig(ClientPlayerActionPacket.Status.STARTED_DIGGING, 5);
            f.player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).setBaseValue(2);
            assertTrue(
                    check.evaluate(
                                    new PlayerPacketEvent(f.player, packet),
                                    data,
                                    f.clock.nanoTime())
                            .failed());
            f.player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).setBaseValue(10);
            assertTrue(
                    check.evaluate(
                                    new PlayerPacketEvent(f.player, packet),
                                    data,
                                    f.clock.nanoTime())
                            .passed());
        }
    }

    @Test
    void refusedPlacementSendsAnAckAndActualBlockStates() {
        try (var f = new MinestomFixture();
                var ac =
                        CatAC.install(
                                f.config().enforcementMode(EnforcementMode.SETBACK).build())) {
            f.world.setBlock(10, 1, 0, Block.STONE);
            var packet =
                    new ClientPlayerBlockPlacementPacket(
                            PlayerHand.MAIN,
                            new Vec(10, 1, 0),
                            BlockFace.TOP,
                            .5f,
                            1,
                            .5f,
                            false,
                            false,
                            8);
            MinecraftServer.getPacketListenerManager().processClientPacket(packet, f.connection);
            EventDispatcher.call(new EntityTickEvent(f.player));
            assertEquals(Block.STONE, f.world.getBlock(10, 1, 0));
            assertTrue(
                    f.connection.packets.stream()
                            .anyMatch(
                                    p ->
                                            p instanceof AcknowledgeBlockChangePacket a
                                                    && a.sequence() == 8));
            assertTrue(f.connection.packets.stream().anyMatch(p -> p instanceof BlockChangePacket));
            assertEquals(1, ac.metrics().cancelledPackets());
        }
    }

    @Test
    void fastBreakCancelsAnEarlyFinishAndBoundsLatencyTolerance() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(1, 1, 0, Block.STONE);
            f.player.refreshOnGround(true);
            var check = new FastBreakCheck(new TickHealth(80));
            var data = f.data(1);
            assertTrue(
                    check.evaluate(
                                    new PlayerPacketEvent(
                                            f.player,
                                            dig(
                                                    ClientPlayerActionPacket.Status.STARTED_DIGGING,
                                                    1)),
                                    data,
                                    f.clock.nanoTime())
                            .passed());
            assertTrue(
                    check.evaluate(
                                    new PlayerPacketEvent(
                                            f.player,
                                            dig(
                                                    ClientPlayerActionPacket.Status
                                                            .FINISHED_DIGGING,
                                                    1)),
                                    data,
                                    f.clock.advanceNanos(50_000_000L))
                            .cancelImmediately());
        }
    }

    @Test
    void diggingToolOrBlockChangesInvalidateTheOperationWithoutScoring() {
        try (var f = new MinestomFixture()) {
            f.world.setBlock(1, 1, 0, Block.STONE);
            f.player.refreshOnGround(true);
            var check = new FastBreakCheck(new TickHealth(80));
            var d = f.data(1);
            check.evaluate(
                    new PlayerPacketEvent(
                            f.player, dig(ClientPlayerActionPacket.Status.STARTED_DIGGING, 1)),
                    d,
                    f.clock.nanoTime());
            f.player.setItemInMainHand(ItemStack.of(Material.DIAMOND_PICKAXE));
            var changed =
                    check.evaluate(
                            new PlayerPacketEvent(
                                    f.player,
                                    dig(ClientPlayerActionPacket.Status.FINISHED_DIGGING, 1)),
                            d,
                            f.clock.advanceNanos(50_000_000L));
            assertEquals(CheckResult.Outcome.UNCERTAIN, changed.outcome());
            assertTrue(changed.cancelImmediately());
            assertFalse(changed.failed());
            check.evaluate(
                    new PlayerPacketEvent(
                            f.player, dig(ClientPlayerActionPacket.Status.STARTED_DIGGING, 1)),
                    d,
                    f.clock.nanoTime());
            f.world.setBlock(1, 1, 0, Block.OBSIDIAN);
            assertEquals(
                    CheckResult.Outcome.UNCERTAIN,
                    check.evaluate(
                                    new PlayerPacketEvent(
                                            f.player,
                                            dig(
                                                    ClientPlayerActionPacket.Status
                                                            .FINISHED_DIGGING,
                                                    1)),
                                    d,
                                    f.clock.nanoTime())
                            .outcome());
        }
    }

    @Test
    void creativeAndInstantBreakAreNotMistakenForMissingStarts() {
        try (var f = new MinestomFixture()) {
            var check = new FastBreakCheck(new TickHealth(80));
            var d = f.data(1);
            f.player.setGameMode(GameMode.CREATIVE);
            assertFalse(
                    check.evaluate(
                                    new PlayerPacketEvent(
                                            f.player,
                                            dig(
                                                    ClientPlayerActionPacket.Status
                                                            .FINISHED_DIGGING,
                                                    1)),
                                    d,
                                    f.clock.nanoTime())
                            .failed());
            f.player.setGameMode(GameMode.SURVIVAL);
            f.world.setBlock(1, 1, 0, Block.TORCH);
            assertTrue(
                    check.evaluate(
                                    new PlayerPacketEvent(
                                            f.player,
                                            dig(
                                                    ClientPlayerActionPacket.Status
                                                            .FINISHED_DIGGING,
                                                    1)),
                                    d,
                                    f.clock.nanoTime())
                            .passed());
        }
    }

    @Test
    void cancellingAttackADoesNotDenyTheDamageFromValidAttackB() {
        try (var f = new MinestomFixture()) {
            AtomicInteger attacks = new AtomicInteger(), healthLoss = new AtomicInteger();
            PacketCheck once =
                    new PacketCheck() {
                        public CheckDescriptor descriptor() {
                            return new CheckDescriptor(
                                    "test.first-attack",
                                    "First",
                                    CheckCategory.COMBAT,
                                    new CheckPolicy(true, 1, 2, 8, 0, 1000),
                                    false);
                        }

                        public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
                            return e.getPacket() instanceof ClientInteractEntityPacket
                                            && attacks.getAndIncrement() == 0
                                    ? CheckResult.cancel(1, "fixture first attack")
                                    : CheckResult.skip();
                        }
                    };
            var engine =
                    new CatEngine(
                            f.config().enforcementMode(EnforcementMode.SETBACK).build(),
                            List.of(once));
            var mob = new LivingEntity(EntityType.ZOMBIE);
            mob.setInstance(f.world, new Pos(2.5, 1, .5)).join();
            mob.addViewer(f.player);
            var host = EventNode.all("host-damage");
            host.addListener(
                    EntityAttackEvent.class,
                    e -> {
                        var damage =
                                new EntityDamageEvent(mob, new EntityDamage(f.player, 1), null);
                        EventDispatcher.call(damage);
                        if (!damage.isCancelled()) healthLoss.incrementAndGet();
                    });
            MinecraftServer.getGlobalEventHandler().addChild(engine.eventNode());
            MinecraftServer.getGlobalEventHandler().addChild(host);
            try {
                var attack =
                        new ClientInteractEntityPacket(
                                mob.getEntityId(), new ClientInteractEntityPacket.Attack(), false);
                MinecraftServer.getPacketListenerManager()
                        .processClientPacket(attack, f.connection);
                MinecraftServer.getPacketListenerManager()
                        .processClientPacket(attack, f.connection);
                assertEquals(1, healthLoss.get());
            } finally {
                MinecraftServer.getGlobalEventHandler().removeChild(host);
                MinecraftServer.getGlobalEventHandler().removeChild(engine.eventNode());
                engine.clear();
                mob.remove();
            }
        }
    }

    @Test
    void customDamageDenialRequiresTheExactActionAndIsOneShot() {
        try (var f = new MinestomFixture();
                var ac = CatAC.install(f.config().build())) {
            var mob = new LivingEntity(EntityType.ZOMBIE);
            ac.denyDamage(f.player, mob, 10, Duration.ofMillis(500), "combat.custom");
            assertFalse(ac.consumeDamageDenial(f.player, mob, 11));
            assertTrue(ac.consumeDamageDenial(f.player, mob, 10));
            assertFalse(ac.consumeDamageDenial(f.player, mob, 10));
        }
    }
}
