package com.mycompany.new_cw.service;

import com.mycompany.new_cw.exception.SensorUnavailableException;
import com.mycompany.new_cw.model.Sensor;
import com.mycompany.new_cw.model.SensorReading;
import com.mycompany.new_cw.repository.ReadingRepository;
import com.mycompany.new_cw.repository.SensorRepository;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;

public class SensorReadingService {

    private final ReadingRepository readingRepo = new ReadingRepository();
    private final SensorRepository sensorRepo = new SensorRepository();

    public List<SensorReading> fetchHistory(String sensorId) {
        Sensor sensor = sensorRepo.findById(sensorId);
        if (sensor == null) {
            throw new NotFoundException("No sensor exists with the identifier '" + sensorId + "'.");
        }
        return readingRepo.findBySensorId(sensorId);
    }

    public SensorReading recordNewReading(String sensorId, SensorReading reading) {
        Sensor sensor = sensorRepo.findById(sensorId);
        if (sensor == null) {
            throw new NotFoundException("No sensor exists with the identifier '" + sensorId + "'.");
        }
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                    "Sensor '" + sensorId + "' is currently undergoing maintenance and is not "
                    + "accepting data submissions. Change the sensor status before recording readings.");
        }
        // Assign a UUID if the client did not supply one
        if (reading.getId() == null || reading.getId().isEmpty()) {
            reading.setId(UUID.randomUUID().toString());
        }
        // Default to current time if no timestamp was provided
        if (reading.getTimestamp() == 0) {
            reading.setTimestamp(System.currentTimeMillis());
        }
        readingRepo.save(sensorId, reading);

        // Side-effect: keep the parent sensor's currentValue in sync
        sensor.setCurrentValue(reading.getValue());
        return reading;
    }
}
