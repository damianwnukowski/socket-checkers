package wnukowski.damian.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wnukowski.damian.game.GameRoom;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static wnukowski.damian.server.ServerCodes.*;

public class ClientHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    private static final Charset COMMUNICATION_CHARSET = StandardCharsets.UTF_8;

    private final ClientState state = new ClientState();
    private final Socket socket;
    private final BufferedReader bufferedReader;
    private final PrintWriter printWriter;
    private boolean shouldBeRunning = true;
    private final Random random = new Random();

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.bufferedReader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), COMMUNICATION_CHARSET));
        this.printWriter = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), COMMUNICATION_CHARSET),
                true);
    }

    @Override
    public void run() {
        try {
            while (shouldBeRunning) {
                String command;
                try {
                    command = bufferedReader.readLine();
                    if (command == null) {
                        shouldBeRunning = false;
                        break;
                    }
                    printWriter.println(processCommand(command));
                } catch (IOException ioException) {
                    log.error("IO EXCEPTION, connection closing", ioException);
                    shouldBeRunning = false;
                } catch (Throwable e) {
                    log.error("Unexpected server error", e);
                    printWriter.println(SERVER_ERROR);
                }
            }
        } finally {
            try {
                log.info("Closing socket connection");
                if (state.getRoomID() != null) {
                    ServerState.gameRooms.get(state.getRoomID()).leave(state.getColorID());
                    state.setColorID(null);
                    state.setRoomID(null);
                }
                socket.close();
            } catch (IOException ioException) {
                log.warn("Error during connection close", ioException);
            }
        }
    }

    /**
     * @return command to send back
     */
    private String processCommand(String command) {
        log.info("Request for room with id [{}] for and color uuid [{}]: " + System.lineSeparator() + "[{}]",
                state.getRoomID(), state.getColorID(), command);
        if (command.equals("QUIT")) {
            shouldBeRunning = false;
        }

        if (state.getRoomID() == null) {
            if (command.startsWith("JOIN")) {
                String[] commandFragments = command.split(" ");
                if (commandFragments.length != 3) {
                    return INVALID_SYNTAX;
                }
                try {
                    UUID room = UUID.fromString(commandFragments[1]);
                    UUID color = UUID.fromString(commandFragments[2]);
                    GameRoom gameRoom = ServerState.gameRooms.get(room);
                    if (gameRoom != null && gameRoom.join(room, color)) {
                        this.state.setRoomID(room);
                        this.state.setColorID(color);
                        return ROOM_JOINED;
                    } else {
                        return ROOM_NOT_FOUND;
                    }
                } catch (Exception E) {
                    return INVALID_SYNTAX;
                }
            }
            if (command.startsWith("CREATE")) {
                if (!command.equals("CREATE")) {
                    return INVALID_SYNTAX; // CREATE doesn't take any arguments, should be explicitly CREATE
                }
                GameRoom gameRoom = new GameRoom();
                this.state.setRoomID(gameRoom.getRoomUUID());
                if (random.nextBoolean()) {
                    this.state.setColorID(gameRoom.getWhiteUUID());
                    gameRoom.join(gameRoom.getRoomUUID(), gameRoom.getWhiteUUID());
                    log.debug("Enemy join command: JOIN {} {}", gameRoom.getRoomUUID(), gameRoom.getBlackUUID());
                    return ROOM_CREATED + " ROOM_ID=" + gameRoom.getRoomUUID() +
                            " PLAYER_COLOR_ID=" + gameRoom.getWhiteUUID() +
                            " ENEMY_COLOR_ID=" + gameRoom.getBlackUUID();
                } else {
                    this.state.setColorID(gameRoom.getBlackUUID());
                    gameRoom.join(gameRoom.getRoomUUID(), gameRoom.getBlackUUID());
                    log.debug("Enemy join command: JOIN {} {}", gameRoom.getRoomUUID(), gameRoom.getWhiteUUID());
                    return ROOM_CREATED + " ROOM_ID=" + gameRoom.getRoomUUID() +
                            " PLAYER_COLOR_ID=" + gameRoom.getBlackUUID() +
                            " ENEMY_COLOR_ID=" + gameRoom.getWhiteUUID();
                }
            }
            return INVALID_SYNTAX + " - please join or create room";
        } else {
            // Returns state of the play
            if (command.equals("GET_STATE")) {
                GameRoom gameRoom = ServerState.gameRooms.get(state.getRoomID());
                if (gameRoom == null) {
                    return ROOM_NOT_FOUND;
                }
                return STATUS_OK + " " + gameRoom.getWholeRoomStateAsString();
            }
            if (command.startsWith("MOVE")) {
                GameRoom gameRoom = ServerState.gameRooms.get(state.getRoomID());
                List<String> commands = Arrays.asList(command.split(" "));
                boolean result = gameRoom.move(commands.subList(1, commands.size()), state.getColorID());
                if (result) {
                    return MOVE_OK;
                } else {
                    return MOVE_FAIL + " - please check if move is indeed legal for current position";
                }
            }
            if (command.equals("LEAVE")) {
                GameRoom gameRoom = ServerState.gameRooms.get(state.getRoomID());
                if (gameRoom == null) {
                    return ROOM_NOT_FOUND;
                }
                gameRoom.leave(state.getColorID());
                state.setRoomID(null);
                state.setColorID(null);
                return ROOM_LEFT;
            }

            if (command.equals("REQUEST_A_DRAW")) {
                GameRoom gameRoom = ServerState.gameRooms.get(state.getRoomID());
                if (gameRoom == null) {
                    return ROOM_NOT_FOUND;
                }
                if (gameRoom.requestADraw(state.getColorID())) {
                    return DRAW_OK;
                }
                return DRAW_FAIL;
            }

            if (command.equals("CANCEL_DRAW_REQUEST")) {
                GameRoom gameRoom = ServerState.gameRooms.get(state.getRoomID());
                if (gameRoom == null) {
                    return ROOM_NOT_FOUND;
                }
                if (gameRoom.cancelDrawRequest(state.getColorID())) {
                    return DRAW_CANCEL_OK;
                }
                return DRAW_CANCEL_FAIL;
            }
            return INVALID_SYNTAX + " - please input a valid command for the ROOM you are in";
        }
    }
}
