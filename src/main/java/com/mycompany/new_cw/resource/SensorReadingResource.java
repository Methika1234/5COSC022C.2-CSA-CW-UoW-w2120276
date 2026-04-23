package com.mycompany.new_cw.resource;

import com.mycompany.new_cw.model.SensorReading;
import com.mycompany.new_cw.service.SensorReadingService;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

public class SensorReadingResource {

    private final String sensorId;
    private final SensorReadingService readingService = new SensorReadingService();

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHistory() {
        List<SensorReading> readings = readingService.fetchHistory(sensorId);
        return Response.ok(readings).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submitReading(SensorReading reading, @Context UriInfo uriInfo) {
        SensorReading saved = readingService.recordNewReading(sensorId, reading);
        URI location = uriInfo.getAbsolutePathBuilder().path(saved.getId()).build();
        return Response.created(location).entity(saved).build();
    }
}
