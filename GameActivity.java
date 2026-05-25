package edu.uic.cs478.s2026.project4;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class GameActivity extends AppCompatActivity implements GameEngine.GameCallback {

    private static final String TAG = "GameActivity";

    private LinearLayout gridContainerP1;
    private LinearLayout gridContainerP2;
    private TextView tvStatus;
    private TextView tvTurn;
    private TextView tvP1Info;
    private TextView tvP2Info;
    private Button btnStop;
    private Button btnNew;

    private final TextView[][] cellsP1 = new TextView[10][10];
    private final TextView[][] cellsP2 = new TextView[10][10];

    private GameEngine engine;
    private SweepPlayerThread player1;
    private SmartPlayerThread player2;

    private int p1Guesses = 0;
    private int p2Guesses = 0;
    private volatile boolean gameRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        findViews();
        buildGrids();
        setupBackHandler();
        startNewGame();
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        cleanup();
                        setEnabled(false);
                        // Let the system handle back (finish activity)
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                });
    }

    private void findViews() {
        gridContainerP1 = findViewById(R.id.grid_container_p1);
        gridContainerP2 = findViewById(R.id.grid_container_p2);
        tvStatus = findViewById(R.id.tv_game_status);
        tvTurn = findViewById(R.id.tv_turn_indicator);
        tvP1Info = findViewById(R.id.tv_player1_info);
        tvP2Info = findViewById(R.id.tv_player2_info);
        btnStop = findViewById(R.id.btn_stop_game);
        btnNew = findViewById(R.id.btn_new_game);

        btnStop.setOnClickListener(v -> stopGame());
        btnNew.setOnClickListener(v -> {
            resetUI();
            startNewGame();
        });
    }

    private void buildGrids() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cellSize = (dm.widthPixels - 48) / 10;

        populateGrid(gridContainerP1, cellsP1, cellSize);
        populateGrid(gridContainerP2, cellsP2, cellSize);
    }

    private void populateGrid(LinearLayout container,
                              TextView[][] cells, int cellSize) {
        container.removeAllViews();
        container.setOrientation(LinearLayout.VERTICAL);

        for (int r = 0; r < 10; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            row.setGravity(Gravity.CENTER_HORIZONTAL);

            for (int c = 0; c < 10; c++) {
                TextView tv = new TextView(this);
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(cellSize, cellSize);
                lp.setMargins(1, 1, 1, 1);
                tv.setLayoutParams(lp);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(7);
                tv.setTextColor(Color.GRAY);
                tv.setText(r + "," + c);
                tv.setBackgroundResource(R.drawable.cell_background);
                cells[r][c] = tv;
                row.addView(tv);
            }
            container.addView(row);
        }
    }

    private void resetUI() {
        p1Guesses = 0;
        p2Guesses = 0;
        tvP1Info.setText("Guesses: 0");
        tvP2Info.setText("Guesses: 0");
        tvStatus.setText("Game in progress...");
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        tvTurn.setVisibility(View.VISIBLE);
        tvTurn.setText("Current Turn: Player 1 (Sweep)");
        btnStop.setVisibility(View.VISIBLE);
        btnNew.setVisibility(View.GONE);

        resetCells(cellsP1);
        resetCells(cellsP2);
    }

    private void resetCells(TextView[][] cells) {
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                cells[r][c].setBackgroundResource(R.drawable.cell_background);
                cells[r][c].setText(r + "," + c);
                cells[r][c].setTextSize(7);
                cells[r][c].setTextColor(Color.GRAY);
                cells[r][c].setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    private void startNewGame() {
        gameRunning = true;

        engine = new GameEngine(this);
        engine.initializeGopher();

        Handler uiHandler = engine.createUIHandler();

        player1 = new SweepPlayerThread(uiHandler);
        player2 = new SmartPlayerThread(uiHandler);

        player1.start();
        player2.start();

        // createHandler() calls getLooper() which blocks until ready
        Handler h1 = player1.createHandler();
        Handler h2 = player2.createHandler();

        engine.setPlayer1Handler(h1);
        engine.setPlayer2Handler(h2);

        Log.d(TAG, "New game. Gopher at ("
                + engine.getGopherRow() + "," + engine.getGopherCol() + ")");

        tvTurn.setText("Current Turn: Player 1 (Sweep)");
        engine.startGame();
    }

    private void cleanup() {
        gameRunning = false;
        if (engine != null) engine.setGameOver();
        if (player1 != null) {
            player1.stopPlayer();
            player1 = null;
        }
        if (player2 != null) {
            player2.stopPlayer();
            player2 = null;
        }
    }

    private void stopGame() {
        cleanup();
        tvStatus.setText("Game Stopped");
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.stop_color));
        tvTurn.setVisibility(View.GONE);
        btnStop.setVisibility(View.GONE);
        btnNew.setVisibility(View.VISIBLE);
        showGopher();
    }

    private void showGopher() {
        if (engine == null) return;
        int gr = engine.getGopherRow();
        int gc = engine.getGopherCol();
        markCellAsGopher(cellsP1[gr][gc]);
        markCellAsGopher(cellsP2[gr][gc]);
    }

    private void markCellAsGopher(TextView cell) {
        cell.setBackgroundColor(
                ContextCompat.getColor(this, R.color.success_color));
        cell.setText("G!");
        cell.setTextSize(10);
        cell.setTextColor(Color.WHITE);
        cell.setTypeface(null, Typeface.BOLD);
    }

    // ===== GameEngine.GameCallback =====

    @Override
    public void onGuessMade(int playerNumber, int row, int col, GuessResult result) {
        runOnUiThread(() -> {
            if (!gameRunning && result != GuessResult.SUCCESS) return;
            if (row < 0 || row > 9 || col < 0 || col > 9) return;

            TextView[][] cells;
            if (playerNumber == 1) {
                p1Guesses++;
                tvP1Info.setText("Guesses: " + p1Guesses);
                cells = cellsP1;
            } else {
                p2Guesses++;
                tvP2Info.setText("Guesses: " + p2Guesses);
                cells = cellsP2;
            }

            TextView cell = cells[row][col];

            switch (result) {
                case SUCCESS:
                    cell.setBackgroundColor(ContextCompat.getColor(
                            GameActivity.this, R.color.success_color));
                    cell.setText("G!");
                    cell.setTextSize(10);
                    cell.setTextColor(Color.WHITE);
                    cell.setTypeface(null, Typeface.BOLD);
                    break;

                case NEAR_MISS:
                    cell.setBackgroundColor(ContextCompat.getColor(
                            GameActivity.this, R.color.near_color));
                    cell.setText("N");
                    cell.setTextSize(9);
                    cell.setTextColor(Color.WHITE);
                    cell.setTypeface(null, Typeface.BOLD);
                    break;

                case CLOSE_GUESS:
                    cell.setBackgroundColor(ContextCompat.getColor(
                            GameActivity.this, R.color.close_color));
                    cell.setText("C");
                    cell.setTextSize(9);
                    cell.setTextColor(Color.BLACK);
                    cell.setTypeface(null, Typeface.BOLD);
                    break;

                case COMPLETE_MISS:
                    cell.setBackgroundColor(ContextCompat.getColor(
                            GameActivity.this, R.color.miss_color));
                    cell.setText("X");
                    cell.setTextSize(9);
                    cell.setTextColor(Color.WHITE);
                    break;
            }

            if (result != GuessResult.SUCCESS) {
                int next = (playerNumber == 1) ? 2 : 1;
                tvTurn.setText("Current Turn: Player "
                        + next + (next == 1 ? " (Sweep)" : " (Smart)"));
            }
        });
    }

    @Override
    public void onGameWon(int playerNumber) {
        runOnUiThread(() -> {
            gameRunning = false;

            String winner = "Player " + playerNumber
                    + (playerNumber == 1 ? " (Sweep)" : " (Smart)");
            int guesses = (playerNumber == 1) ? p1Guesses : p2Guesses;

            tvStatus.setText(winner + " WINS! (" + guesses + " guesses)");
            tvStatus.setTextColor(ContextCompat.getColor(this,
                    playerNumber == 1 ? R.color.player1_color : R.color.player2_color));
            tvTurn.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
            btnNew.setVisibility(View.VISIBLE);

            showGopher();

            if (player1 != null) { player1.stopPlayer(); player1 = null; }
            if (player2 != null) { player2.stopPlayer(); player2 = null; }

            Log.d(TAG, winner + " won in " + guesses + " guesses");
        });
    }

    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }
}
