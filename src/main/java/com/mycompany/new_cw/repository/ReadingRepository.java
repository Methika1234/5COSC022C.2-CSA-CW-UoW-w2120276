package com.mycompany.new_cw.repository;

import com.mycompany.new_cw.model.SensorReading;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReadingRepository {

    private static final Map<String, List<SensorReading>> readingHistory = new HashMap<>();

    public List<SensorReading> findBySensorId(String sensorId) {
        synchronized (readingHistory) {
            List<SensorReading> entries = readingHistory.get(sensorId);
            if (entries == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(entries);
        }
    }

    public void save(String sensorId, SensorReading reading) {
        synchronized (readingHistory) {
            List<SensorReading> entries = readingHistory.get(sensorId);
            if (entries == null) {
                entries = new ArrayList<>();
                readingHistory.put(sensorId, entries);
            }
            entries.add(reading);
        }
    }
}
