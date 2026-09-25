package dev.catac.internal;

import dev.catac.check.PacketCheck;
import dev.catac.config.CheckPolicy;

public record RegisteredPacketCheck(PacketCheck check, int slot, CheckPolicy policy, CheckRuntime runtime) {
}
