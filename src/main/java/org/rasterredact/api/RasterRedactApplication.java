// Boots the private OwlMask raster-first PDF redaction HTTP service.
// Scans configuration properties used by authentication and resource controls.
package org.rasterredact.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RasterRedactApplication {

    public static void main(String[] args) {
        SpringApplication.run(RasterRedactApplication.class, args);
    }
}
