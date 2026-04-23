package com.mycompany.new_cw.resource;

import com.mycompany.new_cw.model.Room;
import com.mycompany.new_cw.service.RoomService;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

@Path("/rooms")
public class SensorRoom {

    private final RoomService roomService = new RoomService();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllRooms() {
        List<Room> rooms = roomService.retrieveAll();
        return Response.ok(rooms).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addNewRoom(Room room, @Context UriInfo uriInfo) {
        Room saved = roomService.registerRoom(room);
        URI location = uriInfo.getAbsolutePathBuilder().path(saved.getId()).build();
        return Response.created(location).entity(saved).build();
    }

    @GET
    @Path("{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetchRoomById(@PathParam("roomId") String roomId) {
        Room room = roomService.lookupById(roomId);
        if (room == null) {
            throw new NotFoundException("No room exists with the identifier '" + roomId + "'.");
        }
        return Response.ok(room).build();
    }

    @DELETE
    @Path("{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeRoom(@PathParam("roomId") String roomId) {
        roomService.decommissionRoom(roomId);
        return Response.noContent().build();
    }
}
