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
            println(
                context.getString(
                    R.string.balance_result,
                    if (problem == balancedProblem) "закрытой" else "открытой"
                )
            )
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
                rowDifferences.indexOf(maxRowDiff).takeIf { it >= 0 } ?: -1
            } else {
                colDifferences.indexOf(maxColDiff).takeIf { it >= 0 } ?: -1
            }

            if (maxDiffIndex == -1) {
                throw IllegalStateException("Не удалось найти строку или столбец с максимальной разностью.")
            }

            var minCost = Double.POSITIVE_INFINITY
            var selectedRow = -1
            var selectedCol = -1

            if (isRow) {
                for (j in 0 until cols) {
                    if (!used[maxDiffIndex][j] && remainingDemands[j] > 0 &&
                        balancedProblem.costs[maxDiffIndex][j] < minCost
                    ) {
                        minCost = balancedProblem.costs[maxDiffIndex][j]
                        selectedRow = maxDiffIndex
                        selectedCol = j
                    }
                }
            } else {
                for (i in 0 until rows) {
                    if (!used[i][maxDiffIndex] && remainingSupplies[i] > 0 &&
                        balancedProblem.costs[i][maxDiffIndex] < minCost
                    ) {
                        minCost = balancedProblem.costs[i][maxDiffIndex]
                        selectedRow = i
                        selectedCol = maxDiffIndex
                    }
                }
            }

            if (selectedRow == -1 || selectedCol == -1) {
                throw IllegalStateException("Не удалось найти ячейку для распределения.")
            }

            val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
            solution[selectedRow][selectedCol] = quantity
            remainingSupplies[selectedRow] -= quantity
            remainingDemands[selectedCol] -= quantity
            used[selectedRow][selectedCol] = true

            println(
                context.getString(
                    R.string.step_description,
                    stepCount++, selectedRow + 1, selectedCol + 1,
                    balancedProblem.costs[selectedRow][selectedCol]
                )
            )
            println(
                context.getString(
                    R.string.distribution_description,
                    selectedRow + 1, selectedCol + 1,
                    balancedProblem.supplies[selectedRow],
                    balancedProblem.demands[selectedCol],
                    quantity
                )
            )
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

            // Исправление 1: Безопасно находим индекс
            var maxDiffIndex = -1
            if (isRow) {
                // Найдем все доступные индексы с этим значением
                val validIndices = rowDifferences.indices
                    .filter { remainingSupplies[it] > 0 && rowDifferences[it] == maxRowDiff }

                if (validIndices.isNotEmpty()) {
                    maxDiffIndex = validIndices[0]
                }
            } else {
                // Найдем все доступные индексы с этим значением
                val validIndices = colDifferences.indices
                    .filter { remainingDemands[it] > 0 && colDifferences[it] == maxColDiff }

                if (validIndices.isNotEmpty()) {
                    maxDiffIndex = validIndices[0]
                }
            }

            // Исправление 2: Вместо выбрасывания исключения, найдем любую ячейку
            if (maxDiffIndex == -1) {
                // Найдем любую доступную ячейку
                var found = false
                for (i in 0 until rows) {
                    if (remainingSupplies[i] <= 0) continue
                    for (j in 0 until cols) {
                        if (remainingDemands[j] > 0 && !used[i][j]) {
                            maxDiffIndex = if (isRow) i else j
                            found = true
                            break
                        }
                    }
                    if (found) break
                }

                // Если все еще не нашли, выходим из цикла
                if (maxDiffIndex == -1) break
            }

            var minCost = Double.POSITIVE_INFINITY
            var selectedRow = -1
            var selectedCol = -1

            if (isRow) {
                // Исправление 3: Проверяем индекс перед использованием
                if (maxDiffIndex >= 0 && maxDiffIndex < rows) {
                    for (j in 0 until cols) {
                        if (!used[maxDiffIndex][j] && remainingDemands[j] > 0 &&
                            balancedProblem.costs[maxDiffIndex][j] < minCost
                        ) {
                            minCost = balancedProblem.costs[maxDiffIndex][j]
                            selectedRow = maxDiffIndex
                            selectedCol = j
                        }
                    }
                    // Если все стоимости одинаковы, выбираем первую доступную ячейку
                    if (selectedRow == -1 || selectedCol == -1) {
                        for (j in 0 until cols) {
                            if (!used[maxDiffIndex][j] && remainingDemands[j] > 0) {
                                selectedRow = maxDiffIndex
                                selectedCol = j
                                break
                            }
                        }
                    }
                }
            } else {
                // Исправление 4: Проверяем индекс перед использованием
                if (maxDiffIndex >= 0 && maxDiffIndex < cols) {
                    for (i in 0 until rows) {
                        if (!used[i][maxDiffIndex] && remainingSupplies[i] > 0 &&
                            balancedProblem.costs[i][maxDiffIndex] < minCost
                        ) {
                            minCost = balancedProblem.costs[i][maxDiffIndex]
                            selectedRow = i
                            selectedCol = maxDiffIndex
                        }
                    }
                    // Если все стоимости одинаковы, выбираем первую доступную ячейку
                    if (selectedRow == -1 || selectedCol == -1) {
                        for (i in 0 until rows) {
                            if (!used[i][maxDiffIndex] && remainingSupplies[i] > 0) {
                                selectedRow = i
                                selectedCol = maxDiffIndex
                                break
                            }
                        }
                    }
                }
            }

            // Исправление 5: Если не нашли ячейку, ищем любую доступную вместо выбрасывания исключения
            if (selectedRow == -1 || selectedCol == -1) {
                for (i in 0 until rows) {
                    if (remainingSupplies[i] <= 0) continue
                    for (j in 0 until cols) {
                        if (remainingDemands[j] > 0 && !used[i][j]) {
                            selectedRow = i
                            selectedCol = j
                            break
                        }
                    }
                    if (selectedRow != -1) break
                }

                // Если все еще не нашли, выходим из цикла
                if (selectedRow == -1 || selectedCol == -1) break
            }

            val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
            solution[selectedRow][selectedCol] = quantity
            remainingSupplies[selectedRow] -= quantity
            remainingDemands[selectedCol] -= quantity
            used[selectedRow][selectedCol] = true

            val step = SolutionStep(
                stepNumber = steps.size + 1,
                description = context.getString(
                    R.string.step_description,
                    steps.size + 1, selectedRow + 1, selectedCol + 1,
                    balancedProblem.costs[selectedRow][selectedCol]
                ) + "\n" +
                        context.getString(
                            R.string.distribution_description,
                            selectedRow + 1, selectedCol + 1,
                            balancedProblem.supplies[selectedRow],
                            balancedProblem.demands[selectedCol],
                            quantity
                        ),
                currentSolution = Array(rows) { i -> solution[i].clone() },
                remainingSupplies = remainingSupplies.clone(),
                remainingDemands = remainingDemands.clone(),
                selectedRow = selectedRow,
                selectedCol = selectedCol,
                quantity = quantity,
                isFictive = selectedRow >= costs.size || selectedCol >= costs[0].size,
                fictiveDescription = when {
                    selectedRow >= costs.size -> context.getString(
                        R.string.fictive_supplier,
                        selectedRow + 1
                    )

                    selectedCol >= costs[0].size -> context.getString(
                        R.string.fictive_store,
                        selectedCol + 1
                    )

                    else -> null
                }
            )
            steps.add(step)
        }

        return steps
    }

    private fun calculatePenalties(
        costs: Array<DoubleArray>,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>
    ): Pair<Array<Double>, Array<Double>> {
        val rows = costs.size
        val cols = costs[0].size

        val rowPenalties = Array(rows) { i ->
            // Исправление 6: Изменение логики для строк без доступных ячеек
            if (remainingSupplies[i] <= 0) return@Array Double.NEGATIVE_INFINITY

            val availableCosts = (0 until cols)
                .filter { !used[i][it] && remainingDemands[it] > 0 }
                .map { costs[i][it] }

            // Исправление 7: Корректная обработка недостаточного количества ячеек
            when {
                availableCosts.isEmpty() -> Double.NEGATIVE_INFINITY
                availableCosts.size == 1 -> 0.0 // Если только одна ячейка, штраф 0
                else -> {
                    val sorted = availableCosts.sorted()
                    sorted[1] - sorted[0]
                }
            }
        }

        val colPenalties = Array(cols) { j ->
            // Исправление 8: Изменение логики для столбцов без доступных ячеек
            if (remainingDemands[j] <= 0) return@Array Double.NEGATIVE_INFINITY

            val availableCosts = (0 until rows)
                .filter { !used[it][j] && remainingSupplies[it] > 0 }
                .map { costs[it][j] }

            // Исправление 9: Корректная обработка недостаточного количества ячеек
            when {
                availableCosts.isEmpty() -> Double.NEGATIVE_INFINITY
                availableCosts.size == 1 -> 0.0 // Если только одна ячейка, штраф 0
                else -> {
                    val sorted = availableCosts.sorted()
                    sorted[1] - sorted[0]
                }
            }
        }

        return Pair(rowPenalties, colPenalties)
    }
}