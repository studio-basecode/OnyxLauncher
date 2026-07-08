package com.mojang.blaze3d.buffers;

public abstract class GpuBuffer implements AutoCloseable {
    private final int usage;
    private final long size;

    public GpuBuffer(int usage, long size) {
        this.usage = usage;
        this.size = size;
    }

    public long size() {
        return size;
    }

    public int usage() {
        return usage;
    }
}
