package com.colombus.snapshot.glb.model;

public class Box {
    public final int x0, y0, z0;  // 최소 좌표 (inclusive)
    public final int x1, y1, z1;  // 최대 좌표 (exclusive)
    public final float r, g, b;   // 평균 색상

    public Box(int x0, int y0, int z0, int x1, int y1, int z1, float r, float g, float b) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public int sizeX() { return x1 - x0; }
    public int sizeY() { return y1 - y0; }
    public int sizeZ() { return z1 - z0; }
    public int volume() { return sizeX() * sizeY() * sizeZ(); }


    public boolean colorMatches(float r2, float g2, float b2, float epsilon) {
        return Math.abs(r - r2) <= epsilon &&
               Math.abs(g - g2) <= epsilon &&
               Math.abs(b - b2) <= epsilon;
    }


    public boolean colorMatches(Box other, float epsilon) {
        return colorMatches(other.r, other.g, other.b, epsilon);
    }

    @Override
    public String toString() {
        return String.format("Box[(%d,%d,%d)->(%d,%d,%d) rgb=(%.2f,%.2f,%.2f)]",
                x0, y0, z0, x1, y1, z1, r, g, b);
    }
}