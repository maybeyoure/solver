package com.kushnir.transportationproblemsolver.solvers

import android.content.Context
import com.kushnir.transportationproblemsolver.R
import com.kushnir.transportationproblemsolver.TransportationProblem
import com.kushnir.transportationproblemsolver.SolutionStep

class DoublePreferenceSolver(private val problem: TransportationProblem) : TransportationSolver {
    override fun solve(context: Context): Array<DoubleArray> {
        val balancedProblem = problem.makeBalanced()
        val (rows, cols) = balancedProblem.costs.size to balancedProblem.costs[0].size

        logBalanceInfo(context, balancedProblem)

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()

        var stepCount = 1
        while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            val (selectedRow, selectedCol) = selectCell(balancedProblem, remainingSupplies, remainingDemands)
            if (selectedRow == -1 || selectedCol == -1) break

            val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol)
            logStepInfo(context, stepCount++, selectedRow, selectedCol, balancedProblem, quantity)
        }

        logTotalCost(context, problem, solution)
        return solution
    }

    override fun solveWithSteps(context: Context): List<SolutionStep> {
        val steps = mutableListOf<SolutionStep>()
        val balancedProblem = problem.makeBalanced()
        val (rows, cols) = balancedProblem.costs.size to balancedProblem.costs[0].size

        addFictiveStepIfNeeded(context, steps, balancedProblem, rows, cols)

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()
        var stepNumber = steps.size + 1

        while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            val (selectedRow, selectedCol) = selectCell(balancedProblem, remainingSupplies, remainingDemands)
            if (selectedRow == -1 || selectedCol == -1) break

            val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol)
            steps.add(createSolutionStep(context, stepNumber++, selectedRow, selectedCol, balancedProblem, solution, remainingSupplies, remainingDemands, quantity))
        }

        return steps
    }

    private fun selectCell(balancedProblem: TransportationProblem, remainingSupplies: DoubleArray, remainingDemands: DoubleArray): Pair<Int, Int> {
        var maxDiff = -1.0
        var selectedRow = -1
        var selectedCol = -1

        // Поиск по строкам
        for (i in remainingSupplies.indices) {
            if (remainingSupplies[i] <= 0) continue

            val availableCosts = remainingDemands.indices
                .filter { remainingDemands[it] > 0 }
                .map { balancedProblem.costs[i][it] }
                .sorted()

            if (availableCosts.size >= 2) {
                val diff = availableCosts[1] - availableCosts[0]
                if (diff > maxDiff) {
                    maxDiff = diff
                    selectedRow = i
                    selectedCol = remainingDemands.indices
                        .filter { remainingDemands[it] > 0 && balancedProblem.costs[i][it] == availableCosts[0] }
                        .firstOrNull() ?: -1
                }
            }
        }

        // Поиск по столбцам
        for (j in remainingDemands.indices) {
            if (remainingDemands[j] <= 0) continue

            val availableCosts = remainingSupplies.indices
                .filter { remainingSupplies[it] > 0 }
                .map { balancedProblem.costs[it][j] }
                .sorted()

            if (availableCosts.size >= 2) {
                val diff = availableCosts[1] - availableCosts[0]
                if (diff > maxDiff) {
                    maxDiff = diff
                    selectedRow = remainingSupplies.indices
                        .filter { remainingSupplies[it] > 0 && balancedProblem.costs[it][j] == availableCosts[0] }
                        .firstOrNull() ?: -1
                    selectedCol = j
                }
            }
        }

        // Если не нашли по разностям, выбираем минимальный элемент
        if (selectedRow == -1 || selectedCol == -1) {
            var minCost = Double.MAX_VALUE
            for (i in remainingSupplies.indices) {
                for (j in remainingDemands.indices) {
                    if (remainingSupplies[i] > 0 && remainingDemands[j] > 0 && balancedProblem.costs[i][j] < minCost) {
                        minCost = balancedProblem.costs[i][j]
                        selectedRow = i
                        selectedCol = j
                    }
                }
            }
        }

        return selectedRow to selectedCol
    }

    private fun allocateResources(solution: Array<DoubleArray>, remainingSupplies: DoubleArray, remainingDemands: DoubleArray, selectedRow: Int, selectedCol: Int): Double {
        val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
        solution[selectedRow][selectedCol] = quantity
        remainingSupplies[selectedRow] -= quantity
        remainingDemands[selectedCol] -= quantity
        return quantity
    }

    private fun logBalanceInfo(context: Context, balancedProblem: TransportationProblem) {
        val totalSupply = balancedProblem.supplies.sum()
        val totalDemand = balancedProblem.demands.sum()

        println(context.getString(R.string.balance_check, totalSupply, totalDemand))
        if (problem.isBalanced()) {
            println(context.getString(R.string.balance_result, if (problem == balancedProblem) "закрытой" else "открытой"))
        }
    }

    private fun logStepInfo(context: Context, stepCount: Int, selectedRow: Int, selectedCol: Int, balancedProblem: TransportationProblem, quantity: Double) {
        println(context.getString(R.string.step_description, stepCount, selectedRow + 1, selectedCol + 1, balancedProblem.costs[selectedRow][selectedCol]))
        println(context.getString(R.string.distribution_description, selectedRow + 1, selectedCol + 1, balancedProblem.supplies[selectedRow], balancedProblem.demands[selectedCol], quantity))
    }

    private fun logTotalCost(context: Context, problem: TransportationProblem, solution: Array<DoubleArray>) {
        val totalCost = problem.calculateTotalCost(solution)
        println(context.getString(R.string.total_cost, totalCost))
    }

    private fun addFictiveStepIfNeeded(context: Context, steps: MutableList<SolutionStep>, balancedProblem: TransportationProblem, rows: Int, cols: Int) {
        if (balancedProblem != problem) {
            val totalSupply = balancedProblem.supplies.sum()
            val totalDemand = balancedProblem.demands.sum()

            val step = if (totalDemand > problem.supplies.sum()) {
                SolutionStep(
                    stepNumber = steps.size + 1,
                    description = context.getString(R.string.fictive_supplier_added, rows, totalDemand - problem.supplies.sum()),
                    currentSolution = Array(rows) { DoubleArray(cols) },
                    remainingSupplies = balancedProblem.supplies.clone(),
                    remainingDemands = balancedProblem.demands.clone(),
                    selectedRow = -1,
                    selectedCol = -1,
                    quantity = 0.0,
                    isFictive = true,
                    fictiveDescription = context.getString(R.string.fictive_supplier, rows)
                )
            } else {
                SolutionStep(
                    stepNumber = steps.size + 1,
                    description = context.getString(R.string.fictive_store_added, cols, totalSupply - problem.demands.sum()),
                    currentSolution = Array(rows) { DoubleArray(cols) },
                    remainingSupplies = balancedProblem.supplies.clone(),
                    remainingDemands = balancedProblem.demands.clone(),
                    selectedRow = -1,
                    selectedCol = -1,
                    quantity = 0.0,
                    isFictive = true,
                    fictiveDescription = context.getString(R.string.fictive_store, cols)
                )
            }
            steps.add(step)
        }
    }

    private fun createSolutionStep(context: Context, stepNumber: Int, selectedRow: Int, selectedCol: Int, balancedProblem: TransportationProblem, solution: Array<DoubleArray>, remainingSupplies: DoubleArray, remainingDemands: DoubleArray, quantity: Double): SolutionStep {
        return SolutionStep(
            stepNumber = stepNumber,
            description = context.getString(R.string.step_description, stepNumber, selectedRow + 1, selectedCol + 1, balancedProblem.costs[selectedRow][selectedCol]) + "\n" +
                    context.getString(R.string.distribution_description, selectedRow + 1, selectedCol + 1, balancedProblem.supplies[selectedRow], balancedProblem.demands[selectedCol], quantity),
            currentSolution = solution.map { it.clone() }.toTypedArray(),
            remainingSupplies = remainingSupplies.clone(),
            remainingDemands = remainingDemands.clone(),
            selectedRow = selectedRow,
            selectedCol = selectedCol,
            quantity = quantity,
            isFictive = selectedRow >= problem.costs.size || selectedCol >= problem.costs[0].size,
            fictiveDescription = when {
                selectedRow >= problem.costs.size -> context.getString(R.string.fictive_supplier, selectedRow + 1)
                selectedCol >= problem.costs[0].size -> context.getString(R.string.fictive_store, selectedCol + 1)
                else -> null
            }
        )
    }
}