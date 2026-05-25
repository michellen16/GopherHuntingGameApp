package edu.uic.cs478.s2026.project4;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class GameEngine {

    private static final String TAG = "GameEngine";

    // Message types for thread communication
    public static final int MSG_GUESS = 100;       // Worker → UI: here's my guess
    public static final int MSG_RESULT = 101;      // UI → Worker: here's the result
    public static final int MSG_GAME_OVER = 102;   // UI → Worker: game ended, you lost
    public static final int MSG_YOUR_TURN = 103;   // UI → Worker: it's your turn
    public static final int MSG_TURN_DONE = 104;   // Worker → UI: I'm done sleeping, next player

    // Bundle keys
    public static final String KEY_ROW = "row";
    public static final String KEY_COL = "col";
    public static final String KEY_PLAYER = "player";
    public static final String KEY_RESULT_CODE = "result_code";

    private int gopherRow;
    private int gopherCol;
    private volatile boolean gameOver = false;
    private int currentPlayer;

    private Handler uiHandler;
    private volatile Handler player1Handler;
    private volatile Handler player2Handler;

    private final GameCallback callback;

    public interface GameCallback {
        void onGuessMade(int playerNumber, int row, int col, GuessResult result);
        void onGameWon(int playerNumber);
    }

    public GameEngine(GameCallback callback) {
        this.callback = callback;
    }

    /**
     * UI thread randomly places gopher. Location is never shared with workers.
     */
    public void initializeGopher() {
        gopherRow = (int) (Math.random() * 10);
        gopherCol = (int) (Math.random() * 10);
        gameOver = false;
        Log.d(TAG, "Gopher at (" + gopherRow + "," + gopherCol + ")");
    }

    public int getGopherRow() { return gopherRow; }
    public int getGopherCol() { return gopherCol; }
    public boolean isGameOver() { return gameOver; }
    public void setGameOver() { gameOver = true; }

    /**
     * Evaluate guess using Chebyshev distance.
     * Distance 0 = SUCCESS, 1 = NEAR_MISS, 2 = CLOSE_GUESS, 3+ = COMPLETE_MISS
     */
    public GuessResult evaluateGuess(int row, int col) {
        int dist = Math.max(Math.abs(row - gopherRow), Math.abs(col - gopherCol));
        switch (dist) {
            case 0: return GuessResult.SUCCESS;
            case 1: return GuessResult.NEAR_MISS;
            case 2: return GuessResult.CLOSE_GUESS;
            default: return GuessResult.COMPLETE_MISS;
        }
    }

    /**
     * Create the UI-side handler. Receives messages from both worker threads.
     * MUST be called on the UI/main thread.
     */
    public Handler createUIHandler() {
        uiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_GUESS:
                        handlePlayerGuess(msg);
                        break;
                    case MSG_TURN_DONE:
                        handleTurnDone(msg);
                        break;
                }
            }
        };
        return uiHandler;
    }

    public void setPlayer1Handler(Handler h) { player1Handler = h; }
    public void setPlayer2Handler(Handler h) { player2Handler = h; }

    /**
     * Process a guess from a worker thread.
     * Runs on UI thread. Evaluates, updates display, sends result back.
     */
    private void handlePlayerGuess(Message msg) {
        if (gameOver) return;

        Bundle data = msg.getData();
        if (data == null) return;

        int row = data.getInt(KEY_ROW, -1);
        int col = data.getInt(KEY_COL, -1);
        int player = data.getInt(KEY_PLAYER, -1);

        if (row < 0 || row > 9 || col < 0 || col > 9 || player < 1) {
            Log.e(TAG, "Invalid guess data from player " + player);
            return;
        }

        Log.d(TAG, "P" + player + " guesses (" + row + "," + col + ")");
        GuessResult result = evaluateGuess(row, col);

        // Update display on UI thread
        if (callback != null) {
            callback.onGuessMade(player, row, col, result);
        }

        if (result == GuessResult.SUCCESS) {
            // Game won
            gameOver = true;
            sendToPlayer(player, MSG_RESULT, result, player);
            int other = (player == 1) ? 2 : 1;
            sendToPlayer(other, MSG_GAME_OVER, null, other);
            if (callback != null) {
                callback.onGameWon(player);
            }
        } else {
            // Send result back. Worker will sleep 2s then send TURN_DONE.
            currentPlayer = player;
            sendToPlayer(player, MSG_RESULT, result, player);
            // Do NOT signal next player here — wait for MSG_TURN_DONE
        }
    }

    /**
     * Worker thread finished sleeping after its move. Signal next player.
     */
    private void handleTurnDone(Message msg) {
        if (gameOver) return;

        Bundle data = msg.getData();
        if (data == null) return;

        int player = data.getInt(KEY_PLAYER, -1);
        int nextPlayer = (player == 1) ? 2 : 1;

        Log.d(TAG, "P" + player + " turn done. Signaling P" + nextPlayer);
        currentPlayer = nextPlayer;
        sendToPlayer(nextPlayer, MSG_YOUR_TURN, null, nextPlayer);
    }

    /**
     * Send a message to a player thread via its handler.
     */
    private void sendToPlayer(int player, int what, GuessResult result, int playerNum) {
        Handler h = (player == 1) ? player1Handler : player2Handler;
        if (h == null) {
            Log.w(TAG, "Handler null for P" + player);
            return;
        }
        try {
            Message msg = Message.obtain();
            msg.what = what;
            Bundle b = new Bundle();
            if (result != null) {
                b.putInt(KEY_RESULT_CODE, result.getCode());
            }
            b.putInt(KEY_PLAYER, playerNum);
            msg.setData(b);
            h.sendMessage(msg);
        } catch (Exception e) {
            Log.e(TAG, "Send to P" + player + " failed", e);
        }
    }

    /**
     * Start the game. Player 1 goes first.
     */
    public void startGame() {
        currentPlayer = 1;
        Log.d(TAG, "Game starting. P1 goes first.");
        sendToPlayer(1, MSG_YOUR_TURN, null, 1);
    }
}
