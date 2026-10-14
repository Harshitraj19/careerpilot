package com.placify.coding.service;

/**
 * Elo rating maths for adaptive practice. A student "plays a match" against a
 * problem: solving it is a win, a wrong submission before solving is a loss.
 * Problems are rated the same way, so their difficulty is learned from real data.
 */
public final class EloRating {

    public static final int MIN = 600;
    public static final int MAX = 3000;

    private EloRating() {
    }

    /** Probability (0..1) that a player rated {@code player} beats one rated {@code opponent}. */
    public static double expected(int player, int opponent) {
        return 1.0 / (1.0 + Math.pow(10, (opponent - player) / 400.0));
    }

    /**
     * @param result 1 for a solve, 0 for a failed attempt
     * @param k      sensitivity (32 for solves, smaller for failed attempts)
     * @return the new rating of the player
     */
    public static int update(int player, int opponent, double result, int k) {
        double next = player + k * (result - expected(player, opponent));
        return (int) Math.max(MIN, Math.min(MAX, Math.round(next)));
    }
}
