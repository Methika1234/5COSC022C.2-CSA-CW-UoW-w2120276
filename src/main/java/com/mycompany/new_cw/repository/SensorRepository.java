package com.mycompany.new_cw.repository;

import com.mycompany.new_cw.model.Sensor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SensorRepository {

    private static final List<Sensor> sensorStore =
            Collections.synchronizedList(new ArrayList<>());

    public List<Sensor> findAll() {
        synchronized (sensorStore) {
            return new ArrayList<>(sensorStore);
        }
    }

    public Sensor findById(String id) {
        if (id == null) return null;
        synchronized (sensorStore) {
            for (Sensor s : sensorStore) {
                if (id.equals(s.getId())) {
                    return s;
                }
            }
            return null;
        }
    }

    public void save(Sensor sensor) {
        synchronized (sensorStore) {
            sensorStore.add(sensor);
        }
    }

    public List<Sensor> filterByType(String type) {
        synchronized (sensorStore) {
            List<Sensor> matched = new ArrayList<>();
            for (Sensor s : sensorStore) {
                if (type.equalsIgnoreCase(s.getType())) {
                    matched.add(s);
                }
            }
            return matched;
        }
    }

    public List<Sensor> findByRoomId(String roomId) {
        synchronized (sensorStore) {
            List<Sensor> matched = new ArrayList<>();
            for (Sensor s : sensorStore) {
                if (roomId.equals(s.getRoomId())) {
                    matched.add(s);
                }
            }
            return matched;
        }
    }
}
