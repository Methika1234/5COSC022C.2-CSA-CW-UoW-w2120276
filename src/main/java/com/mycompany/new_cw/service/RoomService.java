package com.mycompany.new_cw.service;

import com.mycompany.new_cw.exception.RoomNotEmptyException;
import com.mycompany.new_cw.model.Room;
import com.mycompany.new_cw.repository.RoomRepository;
import com.mycompany.new_cw.repository.SensorRepository;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

public class RoomService {

    private final RoomRepository roomRepo = new RoomRepository();
    private final SensorRepository sensorRepo = new SensorRepository();

    public List<Room> retrieveAll() {
        return roomRepo.findAll();
    }

    public Room lookupById(String id) {
        return roomRepo.findById(id);
    }

    public Room registerRoom(Room room) {
        if (room.getId() == null || room.getId().trim().isEmpty()) {
            throw new WebApplicationException("A room ID must be provided.", Response.Status.BAD_REQUEST);
        }
        if (room.getName() == null || room.getName().trim().isEmpty()) {
            throw new WebApplicationException("A room name must be provided.", Response.Status.BAD_REQUEST);
        }
        if (room.getCapacity() < 0) {
            throw new WebApplicationException("Room capacity cannot be a negative number.", Response.Status.BAD_REQUEST);
        }
        synchronized (RoomRepository.INTEGRITY_LOCK) {
            if (roomRepo.findById(room.getId()) != null) {
                throw new WebApplicationException(
                        "A room with identifier '" + room.getId() + "' is already registered.",
                        Response.Status.CONFLICT);
            }
            room.setSensorIds(new ArrayList<>());
            roomRepo.save(room);
        }
        return room;
    }

    public void decommissionRoom(String id) {
        synchronized (RoomRepository.INTEGRITY_LOCK) {
            Room existing = roomRepo.findById(id);
            if (existing == null) {
                throw new NotFoundException("No room exists with the identifier '" + id + "'.");
            }
            if (!sensorRepo.findByRoomId(id).isEmpty()) {
                int count = sensorRepo.findByRoomId(id).size();
                throw new RoomNotEmptyException(
                        "Cannot decommission room '" + id + "' while " + count
                        + " sensor(s) remain deployed. Relocate or unregister all sensors first.");
            }
            roomRepo.remove(id);
        }
    }
}
