package edu.uic.cs478.s2026.project4;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Algorithm 1: Systematic Sweep with Refinement
 *
 * Phase 1: Sweeps grid in a sparse pattern (every 3 cells) to quickly
 *          cover the board and get proximity feedback.
 * Phase 2: When NEAR_MISS or CLOSE_GUESS received, switches to
 *          systematically checking the neighborhood.
 * Falls back to remaining unguessed cells if refinement exhausted.
 */
public class SweepPlayerThread extends PlayerThread {

    private static final String TAG = "SweepPlayer";

    private final List<int[]> sweepOrder = new ArrayList<>();
    private int sweepIndex = 0;

    private boolean refining = false;
    private final List<int[]> refineList = new ArrayList<>();
    private int refineIndex = 0;

    public SweepPlayerThread(Handler uiHandler) {
        super("SweepPlayer", 1, uiHandler);
        buildSweepOrder();
    }

    private void buildSweepOrder() {
        // Sparse sweep: sample every 3rd cell for maximum coverage
        // Each sample point's "close guess" radius covers a 5x5 area
        for (int r = 1; r < 10; r += 3) {
            for (int c = 1; c < 10; c += 3) {
                sweepOrder.add(new int[]{r, c});
            }
        }
        // Second tier: offset samples
        for (int r = 0; r < 10; r += 3) {
            for (int c = 0; c < 10; c += 3) {
                if (!hasPosition(sweepOrder, r, c)) {
                    sweepOrder.add(new int[]{r, c});
                }
            }
        }
        // Fill remaining cells
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                if (!hasPosition(sweepOrder, r, c)) {
                    sweepOrder.add(new int[]{r, c});
                }
            }
        }
        Log.d(TAG, "Sweep order has " + sweepOrder.size() + " positions");
    }

    private boolean hasPosition(List<int[]> list, int r, int c) {
        for (int[] p : list) {
            if (p[0] == r && p[1] == c) return true;
        }
        return false;
    }

    @Override
    protected int[] computeNextGuess() {
        // Phase 2: Refinement
        if (refining) {
            while (refineIndex < refineList.size()) {
                int[] pos = refineList.get(refineIndex);
                refineIndex++;
                if (valid(pos[0], pos[1]) && !guessed[pos[0]][pos[1]]) {
                    Log.d(TAG, "Refine guess: (" + pos[0] + "," + pos[1] + ")");
                    return pos;
                }
            }
            refining = false;
            Log.d(TAG, "Refinement exhausted, back to sweep");
        }

        // Phase 1: Sweep
        while (sweepIndex < sweepOrder.size()) {
            int[] pos = sweepOrder.get(sweepIndex);
            sweepIndex++;
            if (!guessed[pos[0]][pos[1]]) {
                return pos;
            }
        }

        // Fallback: any unguessed cell
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                if (!guessed[r][c]) return new int[]{r, c};
            }
        }

        Log.e(TAG, "No valid guess available!");
        return null;
    }

    @Override
    protected void processResult(GuessResult result, int row, int col) {
        switch (result) {
            case NEAR_MISS:
                startRefine(row, col, 1);
                break;
            case CLOSE_GUESS:
                startRefine(row, col, 2);
                break;
            case COMPLETE_MISS:
                // Continue sweep
                break;
        }
    }

    private void startRefine(int cr, int cc, int distance) {
        refining = true;
        refineList.clear();
        refineIndex = 0;

        if (distance == 1) {
            // Check all 8 adjacent cells
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    addRefineCandidate(cr + dr, cc + dc);
                }
            }
        } else {
            // Check cells at Chebyshev distance exactly 2 first
            for (int dr = -2; dr <= 2; dr++) {
                for (int dc = -2; dc <= 2; dc++) {
                    if (Math.max(Math.abs(dr), Math.abs(dc)) == 2) {
                        addRefineCandidate(cr + dr, cc + dc);
                    }
                }
            }
            // Then add distance-1 cells as backup
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    addRefineCandidate(cr + dr, cc + dc);
                }
            }
        }

        Log.d(TAG, "Refine around (" + cr + "," + cc + ") d=" + distance
                + " candidates=" + refineList.size());
    }

    private void addRefineCandidate(int r, int c) {
        if (valid(r, c) && !guessed[r][c] && !hasPosition(refineList, r, c)) {
            refineList.add(new int[]{r, c});
        }
    }

    private boolean valid(int r, int c) {
        return r >= 0 && r < 10 && c >= 0 && c < 10;
    }
}
