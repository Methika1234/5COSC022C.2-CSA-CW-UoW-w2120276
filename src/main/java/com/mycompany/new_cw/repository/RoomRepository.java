package com.mycompany.new_cw.repository;

import com.mycompany.new_cw.model.Room;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoomRepository {

    private static final List<Room> roomStore =
            Collections.synchronizedList(new ArrayList<>());

    // Shared lock to coordinate sensor registration and room deletion atomically
    public static final Object INTEGRITY_LOCK = new Object();

    public List<Room> findAll() {
        synchronized (roomStore) {
            return new ArrayList<>(roomStore);
        }
    }

    public Room findById(String id) {
        if (id == null) return null;
        synchronized (roomStore) {
            for (Room r : roomStore) {
                if (id.equals(r.getId())) {
                    return r;
                }
            }
            return null;
        }
    }

    public void save(Room room) {
        synchronized (roomStore) {
            roomStore.add(room);
        }
    }

    public boolean remove(String id) {
        synchronized (roomStore) {
            for (int i = 0; i < roomStore.size(); i++) {
                if (roomStore.get(i).getId().equals(id)) {
                    roomStore.remove(i);
                    return true;
                }
            }
            return false;
        }
    }
}
