package dev.catac.internal;

import dev.catac.check.CatCheck;
import dev.catac.check.MovementCheck;
import dev.catac.check.PacketCheck;
import dev.catac.config.CatACConfig;
import dev.catac.config.CheckPolicy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CheckRegistry {
    private final CatACConfig config;
    private final Set<String> ids = new HashSet<>();
    private final List<RegisteredPacketCheck> packetChecks = new ArrayList<>();
    private final List<RegisteredMovementCheck> movementChecks = new ArrayList<>();
    private int size;
    private boolean frozen;

    public CheckRegistry(CatACConfig config) {
        this.config = config;
    }

    public void register(CatCheck check) {
        if (frozen) {
            throw new IllegalStateException("Check registry is frozen");
        }
        Objects.requireNonNull(check, "check");
        boolean packetCheck = check instanceof PacketCheck;
        boolean movementCheck = check instanceof MovementCheck;
        if (packetCheck == movementCheck) {
            throw new IllegalArgumentException("A check must implement exactly one of PacketCheck or MovementCheck: " +
                    check.getClass().getName());
        }

        var descriptor = Objects.requireNonNull(check.descriptor(), "check.descriptor()");
        String id = descriptor.id();
        if (ids.contains(id)) {
            throw new IllegalArgumentException("Duplicate check id: " + id);
        }
        CheckPolicy policy = config.policyFor(id, descriptor.defaultPolicy());
        int slot = size;
        if (packetCheck) {
            PacketCheck packet = (PacketCheck) check;
            packetChecks.add(new RegisteredPacketCheck(packet, slot, policy, new CheckRuntime()));
        } else {
            MovementCheck movement = (MovementCheck) check;
            movementChecks.add(new RegisteredMovementCheck(movement, slot, policy, new CheckRuntime()));
        }
        ids.add(id);
        size++;
    }

    public void freeze() {
        frozen = true;
    }

    public List<RegisteredPacketCheck> packetChecks() {
        return List.copyOf(packetChecks);
    }

    public List<RegisteredMovementCheck> movementChecks() {
        return List.copyOf(movementChecks);
    }

    public int size() {
        return size;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
