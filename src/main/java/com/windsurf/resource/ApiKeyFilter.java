package com.windsurf.resource;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "windsurf.api-key")
    String apiKey;

    @Override
    public void filter(ContainerRequestContext ctx) {
        if ("GET".equals(ctx.getMethod())) return;

        String provided = ctx.getHeaderString("X-Api-Key");
        if (!apiKey.equals(provided)) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "Ogiltig API-nyckel"))
                .build());
        }
    }
}
