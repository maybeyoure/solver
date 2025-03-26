package com.kushnir.transportationproblemsolver

import android.content.Context
import android.util.Log
import com.kushnir.transportationproblemsolver.solvers.DoublePreferenceSolver
import com.kushnir.transportationproblemsolver.solvers.FogelSolver
import java.io.Serializable

class TransportationProblem(
    val costs: Array<DoubleArray>,
    val supplies: DoubleArray,
    val demands: DoubleArray
) : Serializable {

    fun solve(context: Context, methodType: String): Array<DoubleArray> {
        val methods = context.resources.getStringArray(R.array.methods)
        return when (methodType) {
            methods[0] -> DoublePreferenceSolver(this).solve(context)
            methods[1] -> FogelSolver(this).solve(context)
            else -> throw IllegalArgumentException("Неподдерживаемый метод решения")
        }
    }

    fun solveWithSteps(context: Context, methodType: String): List<SolutionStep> {
        val methods = context.resources.getStringArray(R.array.methods)
        Log.d("DEBUG", "methodType: '$methodType', methods: ${methods.joinToString()}")

        val methodIndex = methods.indexOfFirst { it.equals(methodType.trim(), ignoreCase = true) }
        Log.d("DEBUG", "methodIndex: $methodIndex")

        if (methodIndex == -1) {
            throw IllegalArgumentException("Метод '$methodType' не найден в списке: ${methods.joinToString()}")
        }

        return when (methodIndex) {
            0 -> DoublePreferenceSolver(this).solveWithSteps(context)
            1 -> FogelSolver(this).solveWithSteps(context)
            else -> throw IllegalArgumentException("Неподдерживаемый метод решения")
        }

    }

    fun isBalanced(): Boolean {
        val totalSupply = supplies.sum()
        val totalDemand = demands.sum()
        return kotlin.math.abs(totalSupply - totalDemand) < 1e-6
    }

    fun makeBalanced(): TransportationProblem {
        val totalSupply = supplies.sum()
        val totalDemand = demands.sum()

        // Если задача уже сбалансирована
        if (isBalanced()) {
            return this
        }

        return when {
            // Если спрос превышает предложение, добавляем фиктивного поставщика
            totalDemand > totalSupply -> {
                val diff = totalDemand - totalSupply
                val newCosts = Array(costs.size + 1) { i ->
                    if (i < costs.size) costs[i].copyOf() else DoubleArray(costs[0].size) { 0.0 }
                }

                val newSupplies = DoubleArray(supplies.size + 1).apply {
                    supplies.copyInto(this)
                    this[supplies.size] = diff
                }

                TransportationProblem(newCosts, newSupplies, demands)
            }

            // Если предложение превышает спрос, добавляем фиктивный магазин
            else -> {
                val diff = totalSupply - totalDemand
                val newCosts = Array(costs.size) { i ->
                    DoubleArray(costs[i].size + 1).apply {
                        costs[i].copyInto(this)
                        this[costs[i].size] = 0.0
                    }
                }

                val newDemands = DoubleArray(demands.size + 1).apply {
                    demands.copyInto(this)
                    this[demands.size] = diff
                }

                TransportationProblem(newCosts, supplies, newDemands)
            }
        }
    }
    fun calculateTotalCost(solution: Array<DoubleArray>): Double {
        var totalCost = 0.0
        for (i in costs.indices) {
            for (j in costs[0].indices) {
                totalCost += costs[i][j] * solution[i][j]
            }
        }
        return totalCost
    }
}