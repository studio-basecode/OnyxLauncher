package org.lwjgl.glfw;

public abstract class GLFWIMEStatusCallback implements GLFWIMEStatusCallbackI, AutoCloseable {
    public static GLFWIMEStatusCallback create(GLFWIMEStatusCallbackI callback) {
        return callback instanceof GLFWIMEStatusCallback ? (GLFWIMEStatusCallback) callback : null;
    }

    public static GLFWIMEStatusCallback createSafe(long address) {
        return null;
    }

    public void free() {
    }

    @Override
    public void close() {
        free();
    }
}
