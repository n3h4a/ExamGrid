package com.examgrid.solver.optimize;

import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.progress.CancellationToken;
import com.examgrid.solver.progress.Phase;
import com.examgrid.solver.progress.ProgressEvent;
import com.examgrid.solver.progress.ProgressListener;

import java.util.SplittableRandom;

/**
 * Simulated annealing over three neighbourhoods: move (50 %), swap (25 %) and Kempe chain
 * (25 %). Moves that break a hard constraint are rejected before they are evaluated, so the
 * search never leaves the feasible region.
 *
 * <p><b>Schedule.</b> The initial temperature is calibrated from a sample of random feasible
 * moves so that a typical worsening move is accepted with probability
 * {@code initialAcceptance} (80 % by default): {@code T0 = −avgΔ⁺ / ln p0}. The temperature
 * is multiplied by {@code coolingRate} (0.995) after each block of moves, and the block length
 * is chosen so the budget ends when even the cheapest possible worsening move (one student,
 * smallest non-zero weight) is accepted only 0.1 % of the time. Calibrating the end point on
 * the smallest penalty rather than on the average matters: the average is measured on the
 * poor DSatur start and would leave the search too hot to fine-tune. The best solution seen
 * is restored at the end.
 */
public final class SimulatedAnnealing implements Optimizer {

    private static final int CALIBRATION_SAMPLES = 500;
    private static final double FINAL_ACCEPTANCE = 0.001;
    private static final long PROGRESS_EVERY_MILLIS = 150;

    private final SolverParams params;

    public SimulatedAnnealing(SolverParams params) {
        this.params = params;
    }

    @Override
    public OptimizerResult optimise(Timetable tt, ProgressListener listener, CancellationToken cancel) {
        long started = System.nanoTime();
        long deadline = started + params.timeLimitMillis() * 1_000_000L;
        long initial = tt.cost();
        int[] movable = movableNodes(tt);
        if (movable.length == 0 || initial == 0 || params.maxIterations() == 0) {
            return result(tt, initial, 0, 0, started, initial == 0
                    ? OptimizerResult.StopReason.OPTIMAL : OptimizerResult.StopReason.NOTHING_TO_OPTIMISE);
        }
        SplittableRandom rnd = new SplittableRandom(params.seed());

        double meanWorsening = calibrate(tt, movable, rnd);
        double t0 = -meanWorsening / Math.log(params.initialAcceptance());
        double tMin = Math.min(t0, -smallestPenalty(params) / Math.log(FINAL_ACCEPTANCE));
        long steps = Math.max(1, (long) Math.ceil(Math.log(tMin / t0) / Math.log(params.coolingRate())));
        long movesPerTemperature = Math.max(1, params.maxIterations() / steps);

        double temperature = t0;
        long best = tt.cost();
        int[] bestSlots = tt.slots();
        long accepted = 0;
        long iter = 0;
        long lastReport = 0;
        OptimizerResult.StopReason reason = OptimizerResult.StopReason.ITERATION_LIMIT;
        Move move = new Move();

        for (; iter < params.maxIterations(); iter++) {
            if ((iter & 1023) == 0) {
                cancel.throwIfCancelled();
                long now = System.nanoTime();
                if (now > deadline) {
                    reason = OptimizerResult.StopReason.TIME_LIMIT;
                    break;
                }
                if (now - lastReport > PROGRESS_EVERY_MILLIS * 1_000_000L) {
                    lastReport = now;
                    int pct = (int) Math.max(100 * iter / params.maxIterations(),
                            100 * (now - started) / (deadline - started));
                    listener.onProgress(new ProgressEvent(Phase.OPTIMISING, Phase.OPTIMISING.overall(pct), best,
                            "annealing at T=" + String.format("%.2f", temperature)));
                }
            }
            if (iter > 0 && iter % movesPerTemperature == 0) {
                temperature = Math.max(tMin, temperature * params.coolingRate());
            }
            long delta = propose(tt, movable, rnd, move);
            if (delta == Timetable.INFEASIBLE) {
                continue;
            }
            if (delta <= 0 || rnd.nextDouble() < Math.exp(-delta / temperature)) {
                apply(tt, move, delta);
                accepted++;
                if (tt.cost() < best) {
                    best = tt.cost();
                    bestSlots = tt.slots();
                    if (best == 0) {
                        reason = OptimizerResult.StopReason.OPTIMAL;
                        iter++;
                        break;
                    }
                }
            }
        }
        tt.restore(bestSlots);
        return result(tt, initial, iter, accepted, started, reason);
    }

    private static double smallestPenalty(SolverParams params) {
        int min = Integer.MAX_VALUE;
        for (int w : new int[] {params.weights().sameDay(), params.weights().backToBack(), params.weights().hardGap()}) {
            if (w > 0) {
                min = Math.min(min, w);
            }
        }
        return min == Integer.MAX_VALUE ? 1 : min;
    }

    /** Mean cost increase over a sample of feasible worsening moves (never applied). */
    private static double calibrate(Timetable tt, int[] movable, SplittableRandom rnd) {
        Move move = new Move();
        double sum = 0;
        int count = 0;
        for (int i = 0; i < CALIBRATION_SAMPLES * 4 && count < CALIBRATION_SAMPLES; i++) {
            long delta = propose(tt, movable, rnd, move);
            if (delta != Timetable.INFEASIBLE && delta > 0) {
                sum += delta;
                count++;
            }
        }
        return count == 0 ? 1.0 : sum / count;
    }

    /** Draws a random move into {@code move} and returns its delta (or INFEASIBLE). */
    static long propose(Timetable tt, int[] movable, SplittableRandom rnd, Move move) {
        int v = movable[rnd.nextInt(movable.length)];
        int kind = rnd.nextInt(4);
        if (kind <= 1) {
            int t = rnd.nextInt(tt.slotCount());
            move.set(Move.MOVE, v, t);
            return tt.moveDelta(v, t);
        }
        if (kind == 2) {
            int b = movable[rnd.nextInt(movable.length)];
            move.set(Move.SWAP, v, b);
            return tt.swapDelta(v, b);
        }
        int t = rnd.nextInt(tt.slotCount());
        move.set(Move.KEMPE, v, t);
        return tt.kempeDelta(v, t);
    }

    static void apply(Timetable tt, Move move, long delta) {
        switch (move.kind) {
            case Move.MOVE -> tt.applyMove(move.a, move.b, delta);
            case Move.SWAP -> tt.applySwap(move.a, move.b, delta);
            case Move.KEMPE -> tt.applyKempe(move.b, delta);
            default -> throw new IllegalStateException("unknown move kind " + move.kind);
        }
    }

    static int[] movableNodes(Timetable tt) {
        int count = 0;
        for (int v = 0; v < tt.nodeCount(); v++) {
            if (!tt.isLocked(v)) {
                count++;
            }
        }
        int[] out = new int[count];
        int i = 0;
        for (int v = 0; v < tt.nodeCount(); v++) {
            if (!tt.isLocked(v)) {
                out[i++] = v;
            }
        }
        return out;
    }

    private static OptimizerResult result(Timetable tt, long initial, long iterations, long accepted, long started,
                                          OptimizerResult.StopReason reason) {
        return new OptimizerResult(tt.slots(), initial, tt.cost(), iterations, accepted,
                (System.nanoTime() - started) / 1_000_000, reason);
    }

    /** Reusable description of a proposed move: kind, node a, and slot or node b. */
    static final class Move {
        static final int MOVE = 0;
        static final int SWAP = 1;
        static final int KEMPE = 2;
        int kind;
        int a;
        int b;

        void set(int kind, int a, int b) {
            this.kind = kind;
            this.a = a;
            this.b = b;
        }
    }
}
