package com.kushnir.transportationproblemsolver.solvers

import android.content.Context
import com.kushnir.transportationproblemsolver.R
import com.kushnir.transportationproblemsolver.SolutionStep
import com.kushnir.transportationproblemsolver.TransportationProblem


class FogelSolver(private val problem: TransportationProblem) : TransportationSolver {
    override fun solve(context: Context): Array<DoubleArray> {
        val balancedProblem = problem.makeBalanced()
        val rows = balancedProblem.costs.size
        val cols = balancedProblem.costs[0].size

        val totalSupply = balancedProblem.supplies.sum()
        val totalDemand = balancedProblem.demands.sum()

        println(context.getString(R.string.balance_check, totalSupply, totalDemand))
        if (problem.isBalanced()) {
            println(context.getString(R.string.balance_result,
                if (problem == balancedProblem) "закрытой" else "открытой"))
        }

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()
        val used = Array(rows) { BooleanArray(cols) }

        var stepCount = 1
        while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            val (rowDifferences, colDifferences) = calculatePenalties(
                balancedProblem.costs,
                remainingSupplies,
                remainingDemands,
                used
            )

            val maxRowDiff = rowDifferences.maxOrNull() ?: 0.0
            val maxColDiff = colDifferences.maxOrNull() ?: 0.0

            val isRow = maxRowDiff > maxColDiff
            val maxDiffIndex = if (isRow) {
                rowDifferences.indexOf(maxRowDiff)
            } else {
                colDifferences.indexOf(maxColDiff)
            }

            var minCost = Double.POSITIVE_INFINITY
            var selectedRow = -1
            var selectedCol = -1

            if (isRow) {
                for (j in 0 until cols) {
                    if (!used[maxDiffIndex][j] && remainingDemands[j] > 0 &&
                        balancedProblem.costs[maxDiffIndex][j] < minCost) {
                        minCost = balancedProblem.costs[maxDiffIndex][j]
                        selectedRow = maxDiffIndex
                        selectedCol = j
                    }
                }
            } else {
                for (i in 0 until rows) {
                    if (!used[i][maxDiffIndex] && remainingSupplies[i] > 0 &&
                        balancedProblem.costs[i][maxDiffIndex] < minCost) {
                        minCost = balancedProblem.costs[i][maxDiffIndex]
                        selectedRow = i
                        selectedCol = maxDiffIndex
                    }
                }
            }

            val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
            solution[selectedRow][selectedCol] = quantity
            remainingSupplies[selectedRow] -= quantity
            remainingDemands[selectedCol] -= quantity
            used[selectedRow][selectedCol] = true

            println(context.getString(R.string.step_description,
                stepCount++, selectedRow + 1, selectedCol + 1,
                balancedProblem.costs[selectedRow][selectedCol]))
            println(context.getString(R.string.distribution_description,
                selectedRow + 1, selectedCol + 1,
                balancedProblem.supplies[selectedRow],
                balancedProblem.demands[selectedCol],
                quantity))
        }

        val totalCost = problem.calculateTotalCost(solution)
        println(context.getString(R.string.total_cost, totalCost))

        return solution
    }

    override fun solveWithSteps(context: Context): List<SolutionStep> {
        val steps = mutableListOf<SolutionStep>()
        val balancedProblem = problem.makeBalanced()
        val rows = balancedProblem.costs.size
        val cols = balancedProblem.costs[0].size
        val costs = problem.costs



        // Проверка баланса и добавление фиктивных элементов
        val totalSupply = balancedProblem.supplies.sum()
        val totalDemand = balancedProblem.demands.sum()

        if (balancedProblem != problem) {
            if (totalDemand > problem.supplies.sum()) {
                steps.add(SolutionStep(
                    stepNumber = steps.size + 1,
                    description = context.getString(
                        R.string.fictive_supplier_added,
                        rows, totalDemand - problem.supplies.sum()
                    ),
                    currentSolution = Array(rows) { DoubleArray(cols) },
                    remainingSupplies = balancedProblem.supplies.clone(),
                    remainingDemands = balancedProblem.demands.clone(),
                    selectedRow = -1,
                    selectedCol = -1,
                    quantity = 0.0,
                    isFictive = true,
                    fictiveDescription = context.getString(R.string.fictive_supplier, rows)

                ))
            } else {
                steps.add(SolutionStep(  // используем тот же SolutionStep, что и раньше
                    stepNumber = steps.size + 1,
                    description = context.getString(R.string.fictive_store_added,
                        cols, totalSupply - problem.demands.sum()),
                    currentSolution = Array(rows) { DoubleArray(cols) },
                    remainingSupplies = balancedProblem.supplies.clone(),
                    remainingDemands = balancedProblem.demands.clone(),
                    selectedRow = -1,
                    selectedCol = -1,
                    quantity = 0.0,
                    isFictive = true,
                    fictiveDescription = context.getString(R.string.fictive_store, cols)
                ))
            }
        }

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()
        val used = Array(rows) { BooleanArray(cols) }

        while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            val (rowDifferences, colDifferences) = calculatePenalties(
                balancedProblem.costs,
                remainingSupplies,
                remainingDemands,
                used
            )
            val maxRowDiff = rowDifferences.maxOrNull() ?: 0.0
            val maxColDiff = colDifferences.maxOrNull() ?: 0.0

            val isRow = maxRowDiff > maxColDiff
            val maxDiffIndex = if (isRow) {
                rowDifferences.indexOf(maxRowDiff)
            } else {
                colDifferences.indexOf(maxColDiff)
            }

            var minCost = Double.POSITIVE_INFINITY
            var selectedRow = -1
            var selectedCol = -1

            if (isRow) {
                for (j in 0 until cols) {
                    if (!used[maxDiffIndex][j] && remainingDemands[j] > 0 &&
                        balancedProblem.costs[maxDiffIndex][j] < minCost) {
                        minCost = balancedProblem.costs[maxDiffIndex][j]
                        selectedRow = maxDiffIndex
                        selectedCol = j
                    }
                }
            } else {
                for (i in 0 until rows) {
                    if (!used[i][maxDiffIndex] && remainingSupplies[i] > 0 &&
                        balancedProblem.costs[i][maxDiffIndex] < minCost) {
                        minCost = balancedProblem.costs[i][maxDiffIndex]
                        selectedRow = i
                        selectedCol = maxDiffIndex
                    }
                }
            }

            val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
            solution[selectedRow][selectedCol] = quantity
            remainingSupplies[selectedRow] -= quantity
            remainingDemands[selectedCol] -= quantity
            used[selectedRow][selectedCol] = true
            val step = SolutionStep(
                stepNumber = steps.size + 1,
                description = context.getString(R.string.step_description,
                    steps.size + 1, selectedRow + 1, selectedCol + 1,
                    balancedProblem.costs[selectedRow][selectedCol]) + "\n" +
                        context.getString(R.string.distribution_description,
                            selectedRow + 1, selectedCol + 1,
                            balancedProblem.supplies[selectedRow],
                            balancedProblem.demands[selectedCol],
                            quantity),
                currentSolution = Array(rows) { i -> solution[i].clone() },
                remainingSupplies = remainingSupplies.clone(),
                remainingDemands = remainingDemands.clone(),
                selectedRow = selectedRow,
                selectedCol = selectedCol,
                quantity = quantity,
                isFictive = selectedRow >= costs.size || selectedCol >= costs[0].size,
                fictiveDescription = when {
                    selectedRow >= costs.size -> context.getString(R.string.fictive_supplier, selectedRow + 1)
                    selectedCol >= costs[0].size -> context.getString(R.string.fictive_store, selectedCol + 1)
                    else -> null
                }
            )
            steps.add(step)
        }

        return steps
    }

    private fun calculatePenalties(costs: Array<DoubleArray>,
                                   remainingSupplies: DoubleArray,
                                   remainingDemands: DoubleArray,
                                   used: Array<BooleanArray>): Pair<Array<Double>, Array<Double>> {
        val rows = costs.size
        val cols = costs[0].size
        val rowPenalties = Array(rows) { i ->
            if (remainingSupplies[i] <= 0) return@Array Double.POSITIVE_INFINITY

            val availableCosts = (0 until cols)
                .filter { !used[i][it] && remainingDemands[it] > 0 }
                .map { costs[i][it] }

            if (availableCosts.size < 2) return@Array 0.0

            val sorted = availableCosts.sorted()
            sorted[1] - sorted[0]
        }

        val colPenalties = Array(cols) { j ->
            if (remainingDemands[j] <= 0) return@Array Double.POSITIVE_INFINITY

            val availableCosts = (0 until rows)
                .filter { !used[it][j] && remainingSupplies[it] > 0 }
                .map { costs[it][j] }

            if (availableCosts.size < 2) return@Array 0.0

            val sorted = availableCosts.sorted()
            sorted[1] - sorted[0]
        }

        return Pair(rowPenalties, colPenalties)
    }
}