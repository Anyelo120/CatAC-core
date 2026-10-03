package dev.catac.state;

import dev.catac.api.DetectionTrace;

import java.util.ArrayList;
import java.util.List;

public final class TraceBuffer {
    private final DetectionTrace[] records;
    private int next, size;

    public TraceBuffer(int capacity) {
        records = new DetectionTrace[capacity];
    }

    public void add(DetectionTrace record) {
        if (records.length == 0) return;
        records[next] = record;
        next = (next + 1) % records.length;
        size = Math.min(size + 1, records.length);
    }

    public List<DetectionTrace> snapshot() {
        List<DetectionTrace> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
            list.add(records[(next - size + i + records.length) % records.length]);
        return List.copyOf(list);
    }

    public boolean enabled() {
        return records.length > 0;
    }
}
