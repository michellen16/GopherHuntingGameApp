package edu.uic.cs478.s2026.project4;

public enum GuessResult {
    SUCCESS(0),
    NEAR_MISS(1),
    CLOSE_GUESS(2),
    COMPLETE_MISS(3);

    private final int code;

    GuessResult(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static GuessResult fromCode(int code) {
        switch (code) {
            case 0: return SUCCESS;
            case 1: return NEAR_MISS;
            case 2: return CLOSE_GUESS;
            default: return COMPLETE_MISS;
        }
    }
}
