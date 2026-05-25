package edu.uic.cs478.s2026.project4;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Algorithm 2: Constraint Elimination with Information-Maximizing Selection
 *
 * Maintains a boolean grid of candidate locations where the gopher could be.
 * After each guess:
 *   - COMPLETE_MISS: Eliminates ALL cells within Chebyshev distance 2
 *   - NEAR_MISS: Restricts candidates to only distance-1 neighbors
 *   - CLOSE_GUESS: Restricts candidates to only distance-2 cells
 * Picks the next guess that maximizes the number of candidates it can disambiguate.
 */
public class SmartPlayerThread extends PlayerThread {

    private static final String TAG = "SmartPlayer";

    private final boolean[][] candidates = new boolean[10][10];
    private final Random random = new Random();

    public SmartPlayerThread(Handler uiHandler) {
        super("SmartPlayer", 2, uiHandler);
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                candidates[r][c] = true;
            }
        }
    }

    @Override
    protected int[] computeNextGuess() {
        List<int[]> possible = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                if (candidates[r][c] && !guessed[r][c]) {
                    possible.add(new int[]{r, c});
                }
            }
        }

        if (possible.isEmpty()) {
            // Fallback: guess any unguessed cell
            Log.w(TAG, "No candidates left, using fallback");
            for (int r = 0; r < 10; r++) {
                for (int c = 0; c < 10; c++) {
                    if (!guessed[r][c]) return new int[]{r, c};
                }
            }
            Log.e(TAG, "No unguessed cells!");
            return null;
        }

        if (possible.size() == 1) {
            return possible.get(0);
        }

        // Score each candidate: how many other candidates are in its neighborhood
        // Higher score = more informative guess
        int[] best = null;
        int bestScore = -1;

        for (int[] pos : possible) {
            int score = countNearbyCandidates(pos[0], pos[1]);
            if (score > bestScore || (score == bestScore && random.nextBoolean())) {
                bestScore = score;
                best = pos;
            }
        }

        Log.d(TAG, "Smart guess from " + possible.size() + " candidates, "
                + "best score=" + bestScore);

        return (best != null) ? best : possible.get(random.nextInt(possible.size()));
    }

    private int countNearbyCandidates(int r, int c) {
        int count = 0;
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr;
                int nc = c + dc;
                if (valid(nr, nc) && candidates[nr][nc] && !guessed[nr][nc]) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    protected void processResult(GuessResult result, int row, int col) {
        candidates[row][col] = false;

        switch (result) {
            case NEAR_MISS:
                restrictToDistance(row, col, 1);
                break;
            case CLOSE_GUESS:
                restrictToDistance(row, col, 2);
                break;
            case COMPLETE_MISS:
                eliminateWithinDistance(row, col, 2);
                break;
        }

        int remaining = countCandidates();
        Log.d(TAG, "After " + result + ": " + remaining + " candidates remain");
    }

    /**
     * Keep only candidates at exactly the given Chebyshev distance.
     */
    private void restrictToDistance(int row, int col, int dist) {
        boolean[][] keep = new boolean[10][10];
        for (int dr = -dist; dr <= dist; dr++) {
            for (int dc = -dist; dc <= dist; dc++) {
                if (Math.max(Math.abs(dr), Math.abs(dc)) == dist) {
                    int nr = row + dr;
                    int nc = col + dc;
                    if (valid(nr, nc)) {
                        keep[nr][nc] = true;
                    }
                }
            }
        }
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                candidates[r][c] = candidates[r][c] && keep[r][c];
            }
        }
    }

    /**
     * Eliminate all candidates within Chebyshev distance.
     */
    private void eliminateWithinDistance(int row, int col, int dist) {
        for (int dr = -dist; dr <= dist; dr++) {
            for (int dc = -dist; dc <= dist; dc++) {
                int nr = row + dr;
                int nc = col + dc;
                if (valid(nr, nc)) {
                    candidates[nr][nc] = false;
                }
            }
        }
    }

    private int countCandidates() {
        int count = 0;
        for (int r = 0; r < 10; r++)
            for (int c = 0; c < 10; c++)
                if (candidates[r][c]) count++;
        return count;
    }

    private boolean valid(int r, int c) {
        return r >= 0 && r < 10 && c >= 0 && c < 10;
    }
}
