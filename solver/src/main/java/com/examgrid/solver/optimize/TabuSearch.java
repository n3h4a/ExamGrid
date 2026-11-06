package com.examgrid.solver.optimize;

import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.progress.CancellationToken;
import com.examgrid.solver.progress.Phase;
import com.examgrid.solver.progress.ProgressEvent;
import com.examgrid.solver.progress.ProgressListener;

import java.util.SplittableRandom;

/**
 * Tabu search over the move neighbourhood. Each iteration evaluates every slot for a random
 * sample of non-locked nodes and takes the best move that is not tabu, even if it is worse
 * than the current solution; that is how the search climbs out of local optima. After a
 * node leaves slot s, putting it back into s is tabu for {@code 7 + random(5)} iterations.
 * A tabu move is still allowed when it would beat the best score seen (aspiration).
 *
 * <p>The iteration budget {@code maxIterations} counts evaluated moves, so annealing and
 * tabu search get comparable work for the same parameters.
 */
public final class TabuSearch implements Optimizer {

    private static final int SAMPLE = 40;
    private static final int BASE_TENURE = 7;
    private static final long PROGRESS_EVERY_MILLIS = 150;

    private final SolverParams params;

    public TabuSearch(SolverParams params) {
        this.params = params;
    }

    @Override
    public OptimizerResult optimise(Timetable tt, ProgressListener listener, CancellationToken cancel) {
        long started = System.nanoTime();
        long deadline = started + params.timeLimitMillis() * 1_000_000L;
        long initial = tt.cost();
        int[] movable = SimulatedAnnealing.movableNodes(tt);
        if (movable.length == 0 || initial == 0 || params.maxIterations() == 0) {
            return new OptimizerResult(tt.slots(), initial, initial, 0, 0, 0, initial == 0
                    ? OptimizerResult.StopReason.OPTIMAL : OptimizerResult.StopReason.NOTHING_TO_OPTIMISE);
        }
        SplittableRandom rnd = new SplittableRandom(params.seed());
        long[][] tabuUntil = new long[tt.nodeCount()][tt.slotCount()];
        int sample = Math.min(SAMPLE, movable.length);
        long perIteration = (long) sample * tt.slotCount();
        long maxIterations = Math.max(1, params.maxIterations() / perIteration);

        long best = tt.cost();
        int[] bestSlots = tt.slots();
        long accepted = 0;
        long iter = 0;
        long lastReport = 0;
        OptimizerResult.StopReason reason = OptimizerResult.StopReason.ITERATION_LIMIT;

        for (; iter < maxIterations; iter++) {
            cancel.throwIfCancelled();
            long now = System.nanoTime();
            if (now > deadline) {
                reason = OptimizerResult.StopReason.TIME_LIMIT;
                break;
            }
            if (now - lastReport > PROGRESS_EVERY_MILLIS * 1_000_000L) {
                lastReport = now;
                int pct = (int) Math.max(100 * iter / maxIterations, 100 * (now - started) / (deadline - started));
                listener.onProgress(new ProgressEvent(Phase.OPTIMISING, Phase.OPTIMISING.overall(pct), best,
                        "tabu search"));
            }

            int bestNode = -1;
            int bestSlot = -1;
            long bestDelta = Long.MAX_VALUE;
            int ties = 0;
            for (int i = 0; i < sample; i++) {
                int v = movable[rnd.nextInt(movable.length)];
                for (int t = 0; t < tt.slotCount(); t++) {
                    long delta = tt.moveDelta(v, t);
                    if (delta == Timetable.INFEASIBLE) {
                        continue;
                    }
                    if (tabuUntil[v][t] > iter && tt.cost() + delta >= best) {
                        continue;
                    }
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestNode = v;
                        bestSlot = t;
                        ties = 1;
                    } else if (delta == bestDelta && rnd.nextInt(++ties) == 0) {
                        bestNode = v;
                        bestSlot = t;
                    }
                }
            }
            if (bestNode < 0) {
                continue;
            }
            int from = tt.slotOf(bestNode);
            tt.applyMove(bestNode, bestSlot, bestDelta);
            tabuUntil[bestNode][from] = iter + BASE_TENURE + rnd.nextInt(5);
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
        tt.restore(bestSlots);
        return new OptimizerResult(tt.slots(), initial, tt.cost(), iter * perIteration, accepted,
                (System.nanoTime() - started) / 1_000_000, reason);
    }
}
