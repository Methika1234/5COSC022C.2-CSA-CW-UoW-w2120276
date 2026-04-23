package com.mycompany.new_cw.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discoverApi(@Context UriInfo uriInfo) {

        // Build URIs dynamically from the current deployment context
        URI roomsUri = uriInfo.getBaseUriBuilder().path("rooms").build();
        URI sensorsUri = uriInfo.getBaseUriBuilder().path("sensors").build();

        Map<String, String> admin = new LinkedHashMap<>();
        admin.put("name", "Methika Fernando");
        admin.put("email", "methika.fernando@university.edu");

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("rooms", roomsUri.toString());
        endpoints.put("sensors", sensorsUri.toString());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("apiName", "Smart Campus Sensor & Room Management API");
        metadata.put("version", "1.0.0");
        metadata.put("administrator", admin);
        metadata.put("resources", endpoints);

        return Response.ok(metadata).build();
    }
}
