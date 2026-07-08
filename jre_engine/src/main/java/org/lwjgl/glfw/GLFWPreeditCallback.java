package org.lwjgl.glfw;

public abstract class GLFWPreeditCallback implements GLFWPreeditCallbackI, AutoCloseable {
    public static GLFWPreeditCallback create(GLFWPreeditCallbackI callback) {
        return callback instanceof GLFWPreeditCallback ? (GLFWPreeditCallback) callback : null;
    }

    public static GLFWPreeditCallback createSafe(long address) {
        return null;
    }

    public void free() {
    }

    @Override
    public void close() {
        free();
    }
}
