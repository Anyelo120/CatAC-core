package dev.catac.testing;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.play.*;

import java.io.*;
import java.util.*;

/** Versioned CSV numeric corpus; exact native packet types, no arbitrary deserialization. */
public final class NativeReplayCodec {
    public static final String HEADER = "# CatAC native replay v1; Minecraft 1.21.11";

    private NativeReplayCodec() {}

    public static List<ReplayFrame<ClientPacket>> read(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader b ? b : new BufferedReader(source);
        if (!HEADER.equals(boundedLine(reader)))
            throw new IllegalArgumentException("Wrong replay version/protocol");
        List<ReplayFrame<ClientPacket>> frames = new ArrayList<>();
        String line;
        while ((line = boundedLine(reader)) != null) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (frames.size() >= 100_000)
                throw new IllegalArgumentException("Corpus exceeds 100000 packets");
            String[] a = line.split(",", -1);
            if (a.length < 3) throw new IllegalArgumentException("Malformed replay line");
            long sequence = Long.parseLong(a[0]), time = Long.parseLong(a[1]);
            ClientPacket packet;
            switch (a[2]) {
                case "MOVE" -> {
                    length(a, 10);
                    packet =
                            new ClientPlayerPositionAndRotationPacket(
                                    new Pos(
                                            Double.parseDouble(a[3]),
                                            Double.parseDouble(a[4]),
                                            Double.parseDouble(a[5]),
                                            Float.parseFloat(a[6]),
                                            Float.parseFloat(a[7])),
                                    bool(a[8]),
                                    bool(a[9]));
                }
                case "ROTATION" -> {
                    length(a, 7);
                    packet =
                            new ClientPlayerRotationPacket(
                                    Float.parseFloat(a[3]),
                                    Float.parseFloat(a[4]),
                                    bool(a[5]),
                                    bool(a[6]));
                }
                case "STATUS" -> {
                    length(a, 5);
                    packet = new ClientPlayerPositionStatusPacket(bool(a[3]), bool(a[4]));
                }
                case "INPUT" -> {
                    length(a, 4);
                    packet = new ClientInputPacket(Byte.parseByte(a[3]));
                }
                case "PONG" -> {
                    length(a, 4);
                    packet = new ClientPongPacket(Integer.parseInt(a[3]));
                }
                case "TELEPORT_CONFIRM" -> {
                    length(a, 4);
                    packet = new ClientTeleportConfirmPacket(Integer.parseInt(a[3]));
                }
                case "HELD" -> {
                    length(a, 4);
                    packet = new ClientHeldItemChangePacket(Short.parseShort(a[3]));
                }
                default -> throw new IllegalArgumentException("Unsupported packet type: " + a[2]);
            }
            frames.add(new ReplayFrame<>(sequence, time, packet));
        }
        ReplayRunner.run(frames, (p, n) -> Boolean.TRUE);
        return List.copyOf(frames);
    }

    private static String boundedLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1 && c != '\n') {
            if (c == '\r') continue;
            if (line.length() >= 1024)
                throw new IllegalArgumentException("Replay line exceeds 1024 characters");
            line.append((char) c);
        }
        return c == -1 && line.isEmpty() ? null : line.toString();
    }

    private static void length(String[] a, int expected) {
        if (a.length != expected) throw new IllegalArgumentException("Wrong field count");
    }

    private static boolean bool(String s) {
        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        throw new IllegalArgumentException("Invalid boolean");
    }
}
