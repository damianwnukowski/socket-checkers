package wnukowski.damian.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wnukowski.damian.server.ServerState;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class GameRoom {
    private final static Logger log = LoggerFactory.getLogger(GameRoom.class);
    private LocalDateTime lastTimeUpdate = LocalDateTime.now();
    private final UUID roomUUID;
    private final UUID whiteUUID;
    private final UUID blackUUID;
    private long whiteMilliseconds = 1000 * 10 * 60;
    private long blackMilliseconds = 1000 * 10 * 60;
    private Color currentTurn = Color.WHITE;
    private boolean whiteWantsDraw = false;
    private boolean blackWantsDraw = false;
    private boolean gameStarted = false;
    private boolean whiteInTheRoom = false;
    private boolean blackInTheRoom = false;

    //b - indicates black piece, w indicates white pieces, W and B indicates queens of their colour
    private final char[][] board = {
            {'0', 'b', '0', 'b', '0', 'b', '0', 'b'}, // 8
            {'b', '0', 'b', '0', 'b', '0', 'b', '0'}, // 7
            {'0', 'b', '0', 'b', '0', 'b', '0', 'b'}, // 6
            {'0', '0', '0', '0', '0', '0', '0', '0'}, // 5
            {'0', '0', '0', '0', '0', '0', '0', '0'}, // 4
            {'w', '0', 'w', '0', 'w', '0', 'w', '0'}, // 3
            {'0', 'w', '0', 'w', '0', 'w', '0', 'w'}, // 2
            {'w', '0', 'w', '0', 'w', '0', 'w', '0'}  // 1
            //  a   b   c   d   e   f   g   h
    };

    public GameRoom() {
        roomUUID = UUID.randomUUID();
        whiteUUID = UUID.randomUUID();
        blackUUID = UUID.randomUUID();
        ServerState.gameRooms.put(this.roomUUID, this);
        log.debug("Creating room from memory");
    }

    public synchronized boolean join(UUID roomUUID, UUID colorUUID) {
        if (!roomUUID.equals(this.roomUUID)) {
            return false;
        }

        if (colorUUID.equals(whiteUUID) && !whiteInTheRoom) {
            whiteInTheRoom = true;
            if (blackInTheRoom && !gameStarted) {
                gameStarted = true;
            }
            return true;
        }
        if (colorUUID.equals(blackUUID) && !blackInTheRoom) {
            blackInTheRoom = true;
            if (whiteInTheRoom && !gameStarted) {
                gameStarted = true;
            }
            return true;
        }
        return false;  // no color uuid match
    }

    public synchronized void leave(UUID colorUUID) {
        if (colorUUID.equals(whiteUUID)) {
            whiteInTheRoom = false;
        }
        if (colorUUID.equals(blackUUID)) {
            blackInTheRoom = false;
        }
        if (!blackInTheRoom && !whiteInTheRoom) {
            // remove room from memory if both players leave
            log.debug("Deleting room from memory");
            ServerState.gameRooms.remove(this.getRoomUUID());
        }
    }

    public synchronized boolean requestADraw(UUID colorUUID) {
        Color color = getColorForUUID(colorUUID);
        if (color.equals(Color.BLACK) && blackWantsDraw) {
            return false;
        }
        if (color.equals(Color.WHITE) && whiteWantsDraw) {
            return false;
        }

        if (color.equals(Color.BLACK)) {
            blackWantsDraw = true;
        } else {
            whiteWantsDraw = true;
        }
        return true;
    }

    public synchronized boolean cancelDrawRequest(UUID colorUUID) {
        Color color = getColorForUUID(colorUUID);
        if (whiteWantsDraw && blackWantsDraw) {
            return false; // already draw
        }

        if (color.equals(Color.BLACK) && blackWantsDraw) {
            blackWantsDraw = false;
            return true;
        }

        if (color.equals(Color.WHITE) && whiteWantsDraw) {
            whiteWantsDraw = false;
            return true;
        }

        return false;
    }

    public synchronized String getWholeRoomStateAsString() {
        StringBuilder sb = new StringBuilder();
        updateTime();
        sb.append("STATE=").append(getGameState())
                .append(" PLAYER_TURN=").append(currentTurn.toString())
                .append(" WHITE_WANTS_DRAW=").append(whiteWantsDraw ? "TRUE" : "FALSE")
                .append(" BLACK_WANTS_DRAW=").append(blackWantsDraw ? "TRUE" : "FALSE")
                .append(" BLACK_TIME=").append(blackMilliseconds)
                .append(" WHITE_TIME=").append(whiteMilliseconds)
                .append(" WHITE_ONLINE=").append(whiteInTheRoom ? "TRUE" : "FALSE")
                .append(" BLACK_ONLINE=").append(blackInTheRoom ? "TRUE" : "FALSE")
                .append(" BOARD=");

        for (char[] chars : board) {
            for (char chr: chars) {
                sb.append(chr);
            }
        }

        return sb.toString();
    }

    public synchronized State getGameState() {
        if (!gameStarted) {
            return State.WAITING;
        }

        if (whiteWantsDraw && blackWantsDraw) {
            return State.DRAW;
        }

        if (didEnemyLose('b', 'B', blackMilliseconds)) {
            return State.WHITE_WON;
        }

        if (didEnemyLose('w', 'W', whiteMilliseconds)) {
            return State.BLACK_WON;
        }

        return State.PLAYING;
    }

    /**
     * @param commandsStrings list of commands in format [a-h][1-8] where first is chosen piece.
     * @return true if validation passed, false otherwise
     */
    public synchronized boolean move(List<String> commandsStrings, UUID colorUUID) {
        Color movingPlayer = getColorForUUID(colorUUID);

        if (!getGameState().equals(State.PLAYING)) {
            return false;
        }
        if (commandsStrings.size() < 2) {
            return false;
        }

        List<int[]> commands = commandsStrings.stream()
                .map(this::convertIntoArrayCommand)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (commands.size() != commandsStrings.size()) {
            // null filtered commands were invalid commands therefore size of collections differs
            return false;
        }

        int[] pieceArrayCords = commands.get(0);

        char piece = getPieceFromArrayCords(pieceArrayCords);

        if (!movingPlayer.isValidPiece(piece)) {
            return false;
        }

        boolean validationResult;
        validationResult = validateMove(commands, piece, movingPlayer);


        // time can be always updated, but is necessary to update just before potential turn change
        updateTime();
        if (validationResult) {
            finalizeMove(commands, movingPlayer);
            currentTurn = currentTurn.equals(Color.WHITE) ? Color.BLACK : Color.WHITE;
        }

        return validationResult;
    }



    public synchronized UUID getWhiteUUID() {
        return whiteUUID;
    }

    public synchronized UUID getBlackUUID() {
        return blackUUID;
    }

    public synchronized UUID getRoomUUID() {
        return roomUUID;
    }

    private Color getColorForUUID(UUID colorUUID) {
        Color movingPlayer = colorUUID.equals(whiteUUID) ? Color.WHITE
                : colorUUID.equals(blackUUID) ? Color.BLACK : null;
        if (movingPlayer == null) {
            throw new IllegalArgumentException("Color uuid stored in user state is invalid!");
        }
        return movingPlayer;
    }

    /**
     * Example: a1 will map to first white piece: 7, 0
     *
     * @param command like a5
     * @return array coordinates in format {[0,7],[0,7]} or null if cannot map to this format (validation failed)
     */
    private int[] convertIntoArrayCommand(String command) {
        if (command.length() != 2) {
            return null;
        }

        int secondCoordinate;
        try {
            secondCoordinate = Integer.parseInt(command.substring(1, 2));
        } catch (Exception e) {
            return null;
        }

        int[] result = {8 - secondCoordinate, command.charAt(0) - 'a'};
        if (result[0] > 7 || result[0] < 0 || result[1] > 7 || result[1] < 0) {
            return null;
        }

        return result;
    }

    private boolean validateMove(List<int[]> commands, char pieceMoved, Color movingPlayer) {
        int verticalDifference = movingPlayer.equals(Color.WHITE) ? -2 : 2;
        boolean isQueen = Character.isUpperCase(pieceMoved);
        // if first command is jump
        if (Math.abs(commands.get(1)[0] - commands.get(0)[0]) == 2) {
            for (int i = 0; i < commands.size() - 1; i++) {
                if (!validateSingleJump(commands.get(i), commands.get(i + 1), pieceMoved, movingPlayer)) {
                    return false;
                }
            }
            // after legal sequence of moves we need to check if he doesn't skip any possible move
            int[] lastCommand = commands.get(commands.size() - 1);
            int[] newSampleCommand1 = {lastCommand[0] + verticalDifference, lastCommand[1] + 2};
            int[] newSampleCommand2 = {lastCommand[0] + verticalDifference, lastCommand[1] - 2};

            // Additional jumps that queen can make
            int[] newSampleCommandForQueen1 = {lastCommand[0] - verticalDifference, lastCommand[1] + 2};
            int[] newSampleCommandForQueen2 = {lastCommand[0] - verticalDifference, lastCommand[1] - 2};

            List<int[]> generatedProposedCommands = Arrays.asList(newSampleCommand1, newSampleCommand2);
            if (isQueen) {
                generatedProposedCommands.add(newSampleCommandForQueen1);
                generatedProposedCommands.add(newSampleCommandForQueen2);
            }

            // if one of suggested commands is legal jump then move commands are invalid - they dont include
            // all possible jumps
            for (int[] command : generatedProposedCommands) {
                // check if jump is viable only if command is a proper command in the proper range
                if (command[0] >= 0 && command[0] < 8 && command[1] >= 0 && command[1] < 8) {
                    if (validateSingleJump(lastCommand, command, pieceMoved, movingPlayer)) {
                        return false; // validation fails if there was possible capture player didn't command
                    }
                }
            }
            // no proposed jump was valid - player made all possible jumps, validation is ok
            return true;
        }

        // first command isn't jump - only one move allowed
        if (commands.size() > 2) {
            return false;
        }

        int singleFieldMoveDifference = movingPlayer.equals(Color.WHITE) ? -1 : 1;
        int[] start = commands.get(0);
        int[] dest = commands.get(1);
        // no jumping
        if (((dest[0] - start[0] == singleFieldMoveDifference) && (Math.abs(dest[1] - start[1]) == 1)) || // piece
                (isQueen && Math.abs(dest[1] - start[1]) == 1 && Math.abs(dest[0] - start[0]) == 1)) { // queen
            // check if moved piece is there, check if field moved to is empty
            char movedPiece = getPieceFromArrayCords(start);
            char destination = getPieceFromArrayCords(dest);
            return movingPlayer.isValidPiece(movedPiece) && destination == '0';
        }

        return false;
    }

    private boolean validateSingleJump(int[] command1, int[] command2, char pieceMoved, Color movingPlayer) {
        boolean isQueen = Character.isUpperCase(pieceMoved);
        int verticalDifference = movingPlayer.equals(Color.WHITE) ? -2 : 2;
        Color enemyColor = movingPlayer.equals(Color.WHITE) ? Color.BLACK : Color.WHITE;

        if (Math.abs(command1[1] - command2[1]) != 2) return false; // they aren't separated by one field sideways
        if (isQueen) {
            if (Math.abs(command2[0] - command1[0]) != 2)
                return false; // direction doesn't matter as long its 2 distance
        } else {
            if (command2[0] - command1[0] != verticalDifference) return false; // direction matters based on color
        }

        int[] capturedPiecesCoords = {(command1[0] + command2[0]) / 2, (command1[1] + command2[1]) / 2};

        char pieceCaptured = getPieceFromArrayCords(capturedPiecesCoords);
        char destination = getPieceFromArrayCords(command2);

        if (!movingPlayer.isValidPiece(pieceMoved)) return false;
        if (!enemyColor.isValidPiece(pieceCaptured)) return false;
        if (destination != '0') return false;

        return true;
    }

    // set first position to empty, set last position to first position, deletes captured pieces
    private void finalizeMove(List<int[]> commands, Color movingPlayer) {
        for (int i = 0; i < commands.size() - 1; i++) {
            int[] command1 = commands.get(i);
            int[] command2 = commands.get(i + 1);
            if (Math.abs(command1[0] - command2[0]) == 2) {
                // one field gap with capture
                int[] capturedPieceCords = {(command1[0] + command2[0]) / 2, (command1[1] + command2[1]) / 2};
                setPieceOnArrayCords(capturedPieceCords, '0');
            }
        }
        int[] lastPiece = commands.get(commands.size() - 1);
        int isQueen = 0;
        if ((lastPiece[0] == 7 && movingPlayer.equals(Color.BLACK)) ||
                (lastPiece[0] == 0 && movingPlayer.equals(Color.WHITE))) {
            isQueen = 1;
        }

        setPieceOnArrayCords(commands.get(0), '0'); //Starting pos empty
        // Queen is at index of 1, basic piece at index of 0
        setPieceOnArrayCords(commands.get(commands.size() - 1), movingPlayer.piecesSymbols[isQueen]);
    }

    private char getPieceFromArrayCords(int[] cords) {
        return board[cords[0]][cords[1]];
    }

    private void setPieceOnArrayCords(int[] cords, char pieceToSet) {
        board[cords[0]][cords[1]] = pieceToSet;
    }

    private boolean didEnemyLose(char oppositePieceSymbol1, char oppositePieceSymbol2, long oppositePieceMillis) {
        boolean enemyLost = true;
        for (char[] row : board) {
            for (char cell : row) {
                if (cell == oppositePieceSymbol1 || cell == oppositePieceSymbol2) {
                    enemyLost = false;
                    break;
                }
            }
        }

        if (oppositePieceMillis <= 0) {
            enemyLost = true;
        }

        return enemyLost;
    }

    // SHOULD BE ALWAYS CALLED BEFORE TURN CHANGE!!!
    private void updateTime() {
        if (!gameStarted) {
            return;
        }
        long millisPassed = Duration.between(lastTimeUpdate, LocalDateTime.now()).toMillis();
        if (currentTurn.equals(Color.WHITE)) {
            whiteMilliseconds -= millisPassed;
        } else {
            blackMilliseconds -= millisPassed;
        }

        lastTimeUpdate = LocalDateTime.now();

        // Don't store negative time
        blackMilliseconds = Math.max(blackMilliseconds, 0);
        whiteMilliseconds = Math.max(whiteMilliseconds, 0);
    }

    public enum Color {
        /*
        Second piece mark is king version of base biece (first)
         */
        BLACK('b', 'B'), WHITE('w', 'W');

        private final char[] piecesSymbols;

        Color(char... piecesSymbols) {
            this.piecesSymbols = piecesSymbols;
        }

        boolean isValidPiece(char piece) {
            for (char c : piecesSymbols) {
                if (c == piece) {
                    return true;
                }
            }
            return false;
        }
    }

    public enum State {
        WAITING, PLAYING, DRAW, WHITE_WON, BLACK_WON
    }
}
