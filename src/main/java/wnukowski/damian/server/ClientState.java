package wnukowski.damian.server;

import java.util.UUID;

public class ClientState {
    private UUID roomID;
    private UUID colorID;

    public UUID getRoomID() {
        return roomID;
    }

    public void setRoomID(UUID roomID) {
        this.roomID = roomID;
    }

    public UUID getColorID() {
        return colorID;
    }

    public void setColorID(UUID colorID) {
        this.colorID = colorID;
    }
}
