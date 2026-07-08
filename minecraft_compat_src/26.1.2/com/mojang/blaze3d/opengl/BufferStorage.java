package com.mojang.blaze3d.opengl;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.function.Supplier;
import org.lwjgl.opengl.GLCapabilities;

public abstract class BufferStorage {
    public BufferStorage() {
    }

    public static BufferStorage create(GLCapabilities caps, Set<String> enabledExtensions) {
        if (enabledExtensions != null) {
            enabledExtensions.remove("GL_ARB_buffer_storage");
        }
        return new Mutable();
    }

    public abstract GlBuffer createBuffer(DirectStateAccess dsa, Supplier<String> label, int usage, long size);

    public abstract GlBuffer createBuffer(DirectStateAccess dsa, Supplier<String> label, int usage, ByteBuffer data);

    public abstract GlBuffer.GlMappedView mapBuffer(DirectStateAccess dsa, GlBuffer buffer, long offset, long length, int access);

    private static final class Mutable extends BufferStorage {
        public GlBuffer createBuffer(DirectStateAccess dsa, Supplier<String> label, int usage, long size) {
            int handle = dsa.createBuffer();
            dsa.bufferData(handle, size, usage);
            return new GlBuffer(label, dsa, usage, size, handle, null);
        }

        public GlBuffer createBuffer(DirectStateAccess dsa, Supplier<String> label, int usage, ByteBuffer data) {
            int handle = dsa.createBuffer();
            dsa.bufferData(handle, data, usage);
            return new GlBuffer(label, dsa, usage, data.remaining(), handle, null);
        }

        public GlBuffer.GlMappedView mapBuffer(DirectStateAccess dsa, GlBuffer buffer, long offset, long length, int access) {
            final ByteBuffer localBuffer = ByteBuffer.allocateDirect((int)length);
            return new GlBuffer.GlMappedView(() -> {
                localBuffer.clear();
                dsa.bufferSubData(buffer.handle, offset, localBuffer, buffer.usage());
            }, buffer, localBuffer);
        }
    }
}
