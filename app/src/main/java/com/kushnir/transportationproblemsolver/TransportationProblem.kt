package com.kushnir.transportationproblemsolver

import android.content.Context
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
        return when (methodType) {
            methods[0] -> DoublePreferenceSolver(this).solveWithSteps(context)
            methods[1] -> FogelSolver(this).solveWithSteps(context)
            else -> throw IllegalArgumentException("Неподдерживаемый метод решения")
        }
    }

    fun isBalanced(): Boolean {
        val totalSupply = supplies.sum()
        val totalDemand = demands.sum()
        return kotlin.math.abs(totalSupply - totalDemand) < 0.0001
    }

    fun makeBalanced(): TransportationProblem {
        val totalSupply = supplies.sum()
        val totalDemand = demands.sum()

        // Если задача уже сбалансирована
        if (kotlin.math.abs(totalSupply - totalDemand) < 0.0001) {
            return this
        }

        return when {
            // Если спрос превышает предложение, добавляем фиктивного поставщика
            totalDemand > totalSupply -> {
                val diff = totalDemand - totalSupply
                val newCosts = costs.toMutableList().apply {
                    // Добавляем строку с нулевыми тарифами для фиктивного поставщика
                    add(DoubleArray(costs[0].size) { 0.0 })
                }.toTypedArray()

                val newSupplies = supplies.toMutableList().apply {
                    add(diff) // Добавляем недостающее количество запасов
                }.toDoubleArray()

                TransportationProblem(newCosts, newSupplies, demands)
            }

            // Если предложение превышает спрос, добавляем фиктивный магазин
            else -> {
                val diff = totalSupply - totalDemand
                val newCosts = costs.map { row ->
                    row.toMutableList().apply {
                        add(0.0) // Добавляем столбец с нулевыми тарифами
                    }.toDoubleArray()
                }.toTypedArray()

                val newDemands = demands.toMutableList().apply {
                    add(diff) // Добавляем недостающее количество потребностей
                }.toDoubleArray()

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