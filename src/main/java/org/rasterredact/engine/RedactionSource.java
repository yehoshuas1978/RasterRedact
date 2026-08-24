// Identifies whether a redaction rectangle came from a text layer or image OCR.
// Preserved in the contract so future hybrid modes always rasterize OCR regions.
package org.rasterredact.engine;

public enum RedactionSource {
    TEXT,
    OCR
}
