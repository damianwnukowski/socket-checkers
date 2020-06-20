package wnukowski.damian;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerState {
    public static ConcurrentHashMap<UUID, GameRoom> gameRooms = new ConcurrentHashMap<>();
}
