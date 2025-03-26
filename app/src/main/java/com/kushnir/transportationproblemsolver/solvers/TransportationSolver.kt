package com.kushnir.transportationproblemsolver.solvers

import android.content.Context
import com.kushnir.transportationproblemsolver.SolutionStep

interface TransportationSolver {
    fun solve(context: Context): Array<DoubleArray>
    fun solveWithSteps(context: Context): List<SolutionStep>
}