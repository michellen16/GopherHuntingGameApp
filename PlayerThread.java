package edu.uic.cs478.s2026.project4;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

/**
 * Base class for player worker threads.
 * Uses HandlerThread for proper Looper/Handler lifecycle.
 * All communication with UI thread is via Handlers and Messages.
 */
public abstract class PlayerThread extends HandlerThread {

    private static final String TAG = "PlayerThread";

    protected final int playerNumber;
    protected Handler myHandler;
    protected final Handler uiHandler;
    protected volatile boolean running = true;

    protected GuessResult lastResult = null;
    protected int lastGuessRow = -1;
    protected int lastGuessCol = -1;
    protected boolean[][] guessed = new boolean[10][10];
    protected int guessCount = 0;

    public PlayerThread(String name, int playerNumber, Handler uiHandler) {
        super(name);
        this.playerNumber = playerNumber;
        this.uiHandler = uiHandler;
    }

    /**
     * Create this thread's handler. Must be called AFTER start().
     * getLooper() blocks until the HandlerThread's looper is ready,
     * preventing the race condition.
     */
    public Handler createHandler() {
        myHandler = new Handler(getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (!running) return;
                try {
                    switch (msg.what) {
                        case GameEngine.MSG_YOUR_TURN:
                            handleMyTurn();
                            break;
                        case GameEngine.MSG_RESULT:
                            handleResult(msg);
                            break;
                        case GameEngine.MSG_GAME_OVER:
                            running = false;
                            Log.d(TAG, "P" + playerNumber + " received GAME_OVER");
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "P" + playerNumber + " error", e);
                }
            }
        };
        return myHandler;
    }

    /**
     * It's this player's turn. Compute a guess and send it to the UI.
     * No sleep here — sleep happens AFTER the result is received.
     */
    private void handleMyTurn() {
        if (!running) return;

        int[] guess = computeNextGuess();
        if (guess == null || !running) {
            Log.e(TAG, "P" + playerNumber + " cannot compute guess");
            return;
        }

        int row = guess[0];
        int col = guess[1];

        if (row < 0 || row > 9 || col < 0 || col > 9) {
            Log.e(TAG, "P" + playerNumber + " invalid guess (" + row + "," + col + ")");
            return;
        }

        lastGuessRow = row;
        lastGuessCol = col;
        guessed[row][col] = true;
        guessCount++;

        Log.d(TAG, "P" + playerNumber + " guess #" + guessCount
                + ": (" + row + "," + col + ")");

        // Send guess to UI thread via message
        Message msg = Message.obtain();
        msg.what = GameEngine.MSG_GUESS;
        Bundle bundle = new Bundle();
        bundle.putInt(GameEngine.KEY_ROW, row);
        bundle.putInt(GameEngine.KEY_COL, col);
        bundle.putInt(GameEngine.KEY_PLAYER, playerNumber);
        msg.setData(bundle);

        try {
            uiHandler.sendMessage(msg);
        } catch (Exception e) {
            Log.e(TAG, "P" + playerNumber + " failed to send guess", e);
        }
    }

    /**
     * Received the result of our guess from the UI thread.
     * Process it, then sleep 2 seconds to show the move on display,
     * then notify UI that our turn is complete.
     */
    private void handleResult(Message msg) {
        Bundle data = msg.getData();
        if (data == null) return;

        int code = data.getInt(GameEngine.KEY_RESULT_CODE, 3);
        lastResult = GuessResult.fromCode(code);

        Log.d(TAG, "P" + playerNumber + " result: " + lastResult
                + " for (" + lastGuessRow + "," + lastGuessCol + ")");

        if (lastResult == GuessResult.SUCCESS) {
            // We won! No need to sleep or continue.
            running = false;
            Log.d(TAG, "P" + playerNumber + " WON!");
            return;
        }

        // Let subclass update its strategy based on the result
        processResult(lastResult, lastGuessRow, lastGuessCol);

        // REQUIREMENT 6: Sleep 2 seconds so the user can see
        // the effects of this move on the display
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Log.d(TAG, "P" + playerNumber + " sleep interrupted");
            return;
        }

        // After sleeping, check if game is still running
        if (!running) {
            Log.d(TAG, "P" + playerNumber + " game ended during sleep");
            return;
        }

        // Notify UI thread that our turn is complete
        // UI will then signal the other player
        Message doneMsg = Message.obtain();
        doneMsg.what = GameEngine.MSG_TURN_DONE;
        Bundle doneBundle = new Bundle();
        doneBundle.putInt(GameEngine.KEY_PLAYER, playerNumber);
        doneMsg.setData(doneBundle);

        try {
            uiHandler.sendMessage(doneMsg);
        } catch (Exception e) {
            Log.e(TAG, "P" + playerNumber + " failed to send TURN_DONE", e);
        }
    }

    /**
     * Stop this player thread. Interrupts any ongoing sleep.
     */
    public void stopPlayer() {
        running = false;
        this.interrupt(); // Interrupts Thread.sleep() if sleeping
        if (myHandler != null) {
            myHandler.removeCallbacksAndMessages(null);
        }
        try {
            quitSafely();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping P" + playerNumber, e);
        }
    }

    /**
     * Compute the next guess. Implemented by subclasses with different algorithms.
     * @return int[]{row, col} or null if no guess possible
     */
    protected abstract int[] computeNextGuess();

    /**
     * Process feedback from the last guess. Subclasses update their strategy.
     */
    protected abstract void processResult(GuessResult result, int row, int col);
}
