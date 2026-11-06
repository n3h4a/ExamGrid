package com.examgrid.solver.model;

/** Metaheuristic used to improve the initial DSatur timetable. */
public enum OptimizerType {
    /** Simulated annealing over move, swap and Kempe-chain neighbourhoods. */
    ANNEALING,
    /** Tabu search over a sampled move neighbourhood. */
    TABU,
    /** Keep the DSatur timetable as is. */
    NONE
}
