// Boots the private OwlMask raster-first PDF redaction HTTP service.
// Scans configuration properties used by authentication and resource controls.
package com.susswein.owlmask.pdf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OwlMaskPdfApplication {

    public static void main(String[] args) {
        SpringApplication.run(OwlMaskPdfApplication.class, args);
    }
}
