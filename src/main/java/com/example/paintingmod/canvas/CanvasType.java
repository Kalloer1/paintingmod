package com.example.paintingmod.canvas;

/**
 * Canvas sizes. All current sizes are square. Width == height simplifies the GUI grid.
 */
public enum CanvasType {
    NORMAL(32, 32),
    LARGE(64, 64);

    public final int width;
    public final int height;

    CanvasType(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int size() {
        return width * height;
    }
}
