package com.mojang.blaze3d.opengl;

import java.nio.ByteBuffer;

public abstract class DirectStateAccess {
    abstract int createBuffer();

    abstract void bufferData(int handle, long size, int usage);

    abstract void bufferData(int handle, ByteBuffer data, int usage);

    abstract void bufferSubData(int handle, long offset, ByteBuffer data, int usage);

    abstract ByteBuffer mapBufferRange(int handle, long offset, long length, int access, int usage);

    abstract void unmapBuffer(int handle, int usage);
}
