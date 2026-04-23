package com.mycompany.new_cw.resource;

import com.mycompany.new_cw.model.Sensor;
import com.mycompany.new_cw.service.SensorService;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

@Path("/sensors")
public class SensorResource {

    private final SensorService sensorService = new SensorService();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listSensors(@QueryParam("type") String type) {
        List<Sensor> result;
        if (type != null && !type.trim().isEmpty()) {
            result = sensorService.filterByType(type);
        } else {
            result = sensorService.retrieveAll();
        }
        return Response.ok(result).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registerNewSensor(Sensor sensor, @Context UriInfo uriInfo) {
        Sensor saved = sensorService.registerSensor(sensor);
        URI location = uriInfo.getAbsolutePathBuilder().path(saved.getId()).build();
        return Response.created(location).entity(saved).build();
    }

    @Path("{sensorId}/readings")
    public SensorReadingResource readingsSubResource(@PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
    }
}
