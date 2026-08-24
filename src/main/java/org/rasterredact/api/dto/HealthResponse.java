// Returns the minimal liveness contract for internal service orchestration.
// Used only by the unauthenticated health endpoint.
package org.rasterredact.api.dto;

public record HealthResponse(String status, String service) {
}
