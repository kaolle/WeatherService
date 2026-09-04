package com.windsurf.resource;

import com.windsurf.model.AppSettings;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/settings")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Settings")
public class SettingsResource {

    @GET
    public Response get() {
        return Response.ok(Map.of("title", AppSettings.get().title)).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response update(Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return Response.status(400).entity(Map.of("error", "title krävs")).build();
        }
        AppSettings s = AppSettings.get();
        s.title = title.strip();
        return Response.ok(Map.of("title", s.title)).build();
    }
}
