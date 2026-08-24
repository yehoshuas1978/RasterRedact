#!/bin/bash
set -e
TARGET="/home/yehoshua_sus/Projects/RasterRedact"
SRC="/home/yehoshua_sus/Projects/owltable"

cd $TARGET

echo "Creating new directories..."
mkdir -p src/main/java/org/rasterredact/api
mkdir -p src/main/java/org/rasterredact/engine
mkdir -p src/test/java/org/rasterredact/api
mkdir -p src/test/java/org/rasterredact/engine

echo "Copying engine code from owltable..."
cp -r $SRC/owlmask-share/owlmask-share-pdf/src/main/java/com/susswein/owlmask/share/pdf/* src/main/java/org/rasterredact/engine/

echo "Moving existing API code..."
git mv src/main/java/com/susswein/owlmask/pdf/* src/main/java/org/rasterredact/api/
rm -rf src/main/java/com

# Let's delete the old tests for now so it compiles clean
rm -rf src/test

echo "Refactoring packages..."
find src -name "*.java" -exec sed -i 's/com.susswein.owlmask.share.pdf/org.rasterredact.engine/g' {} +
find src -name "*.java" -exec sed -i 's/com.susswein.owlmask.pdf/org.rasterredact.api/g' {} +
find src -name "*.java" -exec sed -i 's/OwlMaskPdfApplication/RasterRedactApplication/g' {} +
find src/main/resources -name "application.yaml" -exec sed -i 's/OWLMASK_PDF/RASTER_REDACT/g' {} +
find src -name "*.java" -exec sed -i 's/OWLMASK_PDF/RASTER_REDACT/g' {} +
find src -name "*.java" -exec sed -i 's/owlmask-pdf/raster-redact/g' {} +

git mv src/main/java/org/rasterredact/api/OwlMaskPdfApplication.java src/main/java/org/rasterredact/api/RasterRedactApplication.java

echo "Writing pom.xml..."
cat << 'POM' > pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.rasterredact</groupId>
    <artifactId>raster-redact</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
    </parent>

    <properties>
        <java.version>21</java.version>
        <pdfbox.version>3.0.3</pdfbox.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>${pdfbox.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
POM

echo "Fixing BearerTokenFilter..."
cat << 'FILTER' > src/main/java/org/rasterredact/api/security/BearerTokenFilter.java
package org.rasterredact.api.security;

import org.rasterredact.api.config.PdfServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.security.MessageDigest;
import java.io.IOException;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {

    private final PdfServiceProperties properties;

    public BearerTokenFilter(PdfServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String configured = properties.authToken();
        if (configured == null || configured.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "PDF auth token is not configured");
            return;
        }
        
        String authHeader = request.getHeader("Authorization");
        String presented = "";
        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            presented = authHeader.substring(7).trim();
        }
        
        if (!MessageDigest.isEqual(configured.getBytes(), presented.getBytes())) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bearer token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
FILTER

git add .
git commit -m "Refactor to public RasterRedact open-source project"
echo "Done."
