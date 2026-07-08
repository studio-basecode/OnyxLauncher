package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class GlBuffer extends GpuBuffer {
    protected final int handle;

    protected GlBuffer(Supplier<String> label, DirectStateAccess dsa, int usage, long size, int handle, ByteBuffer persistentBuffer) {
        super(usage, size);
        this.handle = handle;
    }

    public boolean isClosed() {
        return false;
    }

    public void close() {
    }

    public static class GlMappedView {
        protected GlMappedView(Runnable closeAction, GlBuffer buffer, ByteBuffer data) {
        }
    }
}
