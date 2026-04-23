package com.mycompany.new_cw.service;

import com.mycompany.new_cw.exception.LinkedResourceNotFoundException;
import com.mycompany.new_cw.model.Room;
import com.mycompany.new_cw.model.Sensor;
import com.mycompany.new_cw.repository.RoomRepository;
import com.mycompany.new_cw.repository.SensorRepository;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.List;

public class SensorService {

    private final SensorRepository sensorRepo = new SensorRepository();
    private final RoomRepository roomRepo = new RoomRepository();

    public List<Sensor> retrieveAll() {
        return sensorRepo.findAll();
    }

    public List<Sensor> filterByType(String type) {
        return sensorRepo.filterByType(type);
    }

    public Sensor lookupById(String id) {
        return sensorRepo.findById(id);
    }

    public Sensor registerSensor(Sensor sensor) {
        if (sensor.getId() == null || sensor.getId().trim().isEmpty()) {
            throw new WebApplicationException("A sensor ID must be provided.", Response.Status.BAD_REQUEST);
        }
        if (sensor.getType() == null || sensor.getType().trim().isEmpty()) {
            throw new WebApplicationException("Sensor type must be specified.", Response.Status.BAD_REQUEST);
        }
        synchronized (RoomRepository.INTEGRITY_LOCK) {
            if (sensorRepo.findById(sensor.getId()) != null) {
                throw new WebApplicationException(
                        "A sensor with identifier '" + sensor.getId() + "' is already registered.",
                        Response.Status.CONFLICT);
            }
            Room parentRoom = roomRepo.findById(sensor.getRoomId());
            if (parentRoom == null) {
                throw new LinkedResourceNotFoundException(
                        "The specified room reference '" + sensor.getRoomId()
                        + "' does not match any registered room. "
                        + "Ensure the room is created before assigning sensors to it.");
            }
            sensorRepo.save(sensor);
            parentRoom.getSensorIds().add(sensor.getId());
        }
        return sensor;
    }
}
