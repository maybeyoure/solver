package com.kushnir.transportationproblemsolver.solvers

import android.content.Context
import com.kushnir.transportationproblemsolver.R
import com.kushnir.transportationproblemsolver.TransportationProblem
import com.kushnir.transportationproblemsolver.SolutionStep

class DoublePreferenceSolver(private val problem: TransportationProblem) : TransportationSolver {
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

        // Находим минимумы по строкам и столбцам
        val rowMins = Array(rows) { i -> balancedProblem.costs[i].minOrNull() ?: Double.MAX_VALUE }
        val colMins = Array(cols) { j -> balancedProblem.costs.map { it[j] }.minOrNull() ?: Double.MAX_VALUE }

        // Находим разности между двумя минимальными элементами
        val rowDiffs = Array(rows) { i ->
            val sorted = balancedProblem.costs[i].sorted()
            if (sorted.size >= 2) sorted[1] - sorted[0] else 0.0
        }
        val colDiffs = Array(cols) { j ->
            val sorted = balancedProblem.costs.map { it[j] }.sorted()
            if (sorted.size >= 2) sorted[1] - sorted[0] else 0.0
        }

        var stepCount = 1
        while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            // Находим максимальную разность
            var maxDiff = -1.0
            var maxDiffRow = -1
            var maxDiffCol = -1

            // Проверяем строки
            for (i in 0 until rows) {
                if (remainingSupplies[i] <= 0) continue
                if (rowDiffs[i] > maxDiff) {
                    for (j in 0 until cols) {
                        if (remainingDemands[j] <= 0) continue
                        if (balancedProblem.costs[i][j] == rowMins[i]) {
                            maxDiff = rowDiffs[i]
                            maxDiffRow = i
                            maxDiffCol = j
                        }
                    }
                }
            }

            // Проверяем столбцы
            for (j in 0 until cols) {
                if (remainingDemands[j] <= 0) continue
                if (colDiffs[j] > maxDiff) {
                    for (i in 0 until rows) {
                        if (remainingSupplies[i] <= 0) continue
                        if (balancedProblem.costs[i][j] == colMins[j]) {
                            maxDiff = colDiffs[j]
                            maxDiffRow = i
                            maxDiffCol = j
                        }
                    }
                }
            }

            if (maxDiffRow == -1 || maxDiffCol == -1) {
                // Если не нашли по разностям, берем минимальный элемент
                var minCost = Double.MAX_VALUE
                for (i in 0 until rows) {
                    for (j in 0 until cols) {
                        if (remainingSupplies[i] > 0 && remainingDemands[j] > 0 &&
                            balancedProblem.costs[i][j] < minCost) {
                            minCost = balancedProblem.costs[i][j]
                            maxDiffRow = i
                            maxDiffCol = j
                        }
                    }
                }
            }

            // Распределяем поставки
            val quantity = minOf(remainingSupplies[maxDiffRow], remainingDemands[maxDiffCol])
            solution[maxDiffRow][maxDiffCol] = quantity
            remainingSupplies[maxDiffRow] -= quantity
            remainingDemands[maxDiffCol] -= quantity

            println(context.getString(R.string.step_description,
                stepCount++, maxDiffRow + 1, maxDiffCol + 1,
                balancedProblem.costs[maxDiffRow][maxDiffCol]))
            println(context.getString(R.string.distribution_description,
                maxDiffRow + 1, maxDiffCol + 1,
                remainingSupplies[maxDiffRow],
                remainingDemands[maxDiffCol],
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

        // Проверка баланса и добавление фиктивных элементов (оставляем как есть)
        val totalSupply = balancedProblem.supplies.sum()
        val totalDemand = balancedProblem.demands.sum()

        // Добавление шага с фиктивными элементами (оставляем текущий код)
        if (balancedProblem != problem) {
            if (totalDemand > problem.supplies.sum()) {
                steps.add(
                    SolutionStep(
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
                    )
                )
            } else {
                steps.add(
                    SolutionStep(
                        stepNumber = steps.size + 1,
                        description = context.getString(
                            R.string.fictive_store_added,
                            cols, totalSupply - problem.demands.sum()
                        ),
                        currentSolution = Array(rows) { DoubleArray(cols) },
                        remainingSupplies = balancedProblem.supplies.clone(),
                        remainingDemands = balancedProblem.demands.clone(),
                        selectedRow = -1,
                        selectedCol = -1,
                        quantity = 0.0,
                        isFictive = true,
                        fictiveDescription = context.getString(R.string.fictive_store, cols)
                    )
                )
            }
        }

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()
        var stepNumber = steps.size + 1

        while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            // Находим минимумы и разности для строк
            val rowMinsAndDiffs = Array(rows) { i ->
                if (remainingSupplies[i] <= 0) {
                    Pair(Double.MAX_VALUE to Double.MAX_VALUE, 0.0)
                } else {
                    val availableCosts = (0 until cols)
                        .filter { remainingDemands[it] > 0 }
                        .map { balancedProblem.costs[i][it] }
                        .sorted()
                    if (availableCosts.size >= 2) {
                        Pair(availableCosts[0] to availableCosts[1], availableCosts[1] - availableCosts[0])
                    } else if (availableCosts.size == 1) {
                        Pair(availableCosts[0] to Double.MAX_VALUE, 0.0)
                    } else {
                        Pair(Double.MAX_VALUE to Double.MAX_VALUE, 0.0)
                    }
                }
            }

            // Находим минимумы и разности для столбцов
            val colMinsAndDiffs = Array(cols) { j ->
                if (remainingDemands[j] <= 0) {
                    Pair(Double.MAX_VALUE to Double.MAX_VALUE, 0.0)
                } else {
                    val availableCosts = (0 until rows)
                        .filter { remainingSupplies[it] > 0 }
                        .map { balancedProblem.costs[it][j] }
                        .sorted()
                    if (availableCosts.size >= 2) {
                        Pair(availableCosts[0] to availableCosts[1], availableCosts[1] - availableCosts[0])
                    } else if (availableCosts.size == 1) {
                        Pair(availableCosts[0] to Double.MAX_VALUE, 0.0)
                    } else {
                        Pair(Double.MAX_VALUE to Double.MAX_VALUE, 0.0)
                    }
                }
            }

            // Находим максимальную разность
            val maxRowDiff = rowMinsAndDiffs.maxByOrNull { it.second }
            val maxColDiff = colMinsAndDiffs.maxByOrNull { it.second }

            var selectedRow = -1
            var selectedCol = -1

            // Выбираем строку или столбец с максимальной разностью
            if ((maxRowDiff?.second ?: 0.0) >= (maxColDiff?.second ?: 0.0)) {
                // Используем строку с максимальной разностью
                selectedRow = rowMinsAndDiffs.indexOf(maxRowDiff)
                // Ищем минимальный элемент в выбранной строке
                val minInRow = (0 until cols)
                    .filter { remainingDemands[it] > 0 }
                    .minByOrNull { balancedProblem.costs[selectedRow][it] }
                selectedCol = minInRow ?: -1
            } else {
                // Используем столбец с максимальной разностью
                selectedCol = colMinsAndDiffs.indexOf(maxColDiff)
                // Ищем минимальный элемент в выбранном столбце
                val minInCol = (0 until rows)
                    .filter { remainingSupplies[it] > 0 }
                    .minByOrNull { balancedProblem.costs[it][selectedCol] }
                selectedRow = minInCol ?: -1
            }

            if (selectedRow == -1 || selectedCol == -1) break

            // Распределяем поставки
            val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
            solution[selectedRow][selectedCol] = quantity
            remainingSupplies[selectedRow] -= quantity
            remainingDemands[selectedCol] -= quantity

            // Сохраняем шаг решения
            steps.add(
                SolutionStep(
                    stepNumber = stepNumber++,
                    description = context.getString(
                        R.string.step_description,
                        steps.size + 1, selectedRow + 1, selectedCol + 1,
                        balancedProblem.costs[selectedRow][selectedCol]
                    ) + "\n" + context.getString(
                        R.string.distribution_description,
                        selectedRow + 1, selectedCol + 1,
                        balancedProblem.supplies[selectedRow],
                        balancedProblem.demands[selectedCol],
                        quantity
                    ),
                    currentSolution = solution.map { it.clone() }.toTypedArray(),
                    remainingSupplies = remainingSupplies.clone(),
                    remainingDemands = remainingDemands.clone(),
                    selectedRow = selectedRow,
                    selectedCol = selectedCol,
                    quantity = quantity,
                    isFictive = selectedRow >= problem.costs.size || selectedCol >= problem.costs[0].size,
                    fictiveDescription = when {
                        selectedRow >= problem.costs.size ->
                            context.getString(R.string.fictive_supplier, selectedRow + 1)
                        selectedCol >= problem.costs[0].size ->
                            context.getString(R.string.fictive_store, selectedCol + 1)
                        else -> null
                    }
                )
            )
        }

        return steps
    }
}