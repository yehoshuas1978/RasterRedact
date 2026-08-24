// Represents a validated device-pixel redaction rectangle.
// Created by RedactionCoordinateMapper and consumed by rendering verification.
package org.rasterredact.engine;

record PixelRectangle(int x, int y, int width, int height) {
}
