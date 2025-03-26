package com.kushnir.transportationproblemsolver.solvers

import android.content.Context
import android.util.Log
import com.kushnir.transportationproblemsolver.R
import com.kushnir.transportationproblemsolver.TransportationProblem
import com.kushnir.transportationproblemsolver.SolutionStep

class DoublePreferenceSolver(private val problem: TransportationProblem) : TransportationSolver {

    private val originalRows: Int = problem.costs.size
    private val originalCols: Int = problem.costs[0].size

    override fun solve(context: Context): Array<DoubleArray> {
        val balancedProblem = problem.makeBalanced()
        val (rows, cols) = balancedProblem.costs.size to balancedProblem.costs[0].size

        logBalanceInfo(context, balancedProblem)

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()

        var stepCount = 1

        // Сначала обрабатываем реальные пути
        while (hasRealWork(remainingSupplies, remainingDemands)) {
            val (selectedRow, selectedCol) = selectCell(balancedProblem, remainingSupplies, remainingDemands, true)
            if (selectedRow == -1 || selectedCol == -1) break

            val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol)
            logStepInfo(context, stepCount++, selectedRow, selectedCol, balancedProblem, quantity, false)
        }

        // Затем обрабатываем фиктивные пути, если остались нераспределенные ресурсы
        if (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            Log.d("DoublePreferenceSolver", "All real paths allocated, switching to fictive paths")

            while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
                val (selectedRow, selectedCol) = selectCell(balancedProblem, remainingSupplies, remainingDemands, false)
                if (selectedRow == -1 || selectedCol == -1) break

                val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol)
                logStepInfo(context, stepCount++, selectedRow, selectedCol, balancedProblem, quantity, true)
            }
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

        // Сначала обрабатываем реальные пути
        while (hasRealWork(remainingSupplies, remainingDemands)) {
            val (selectedRow, selectedCol) = selectCell(balancedProblem, remainingSupplies, remainingDemands, true)
            if (selectedRow == -1 || selectedCol == -1) break

            val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol)
            steps.add(createSolutionStep(
                context,
                stepNumber++,
                selectedRow,
                selectedCol,
                balancedProblem,
                solution,
                remainingSupplies,
                remainingDemands,
                quantity,
                "Выбираем реальную ячейку"
            ))
        }

        // Добавляем информационный шаг перед переходом к фиктивным путям
        if (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            val fictiveInfoStep = SolutionStep(
                stepNumber = stepNumber++,
                description = "Все реальные пути распределены. Переходим к распределению фиктивных путей.",
                currentSolution = Array(solution.size) { i -> solution[i].clone() },
                remainingSupplies = remainingSupplies.clone(),
                remainingDemands = remainingDemands.clone(),
                selectedRow = -1,
                selectedCol = -1,
                quantity = 0.0,
                isFictive = true,
                fictiveDescription = "Переход к фиктивным путям"
            )
            steps.add(fictiveInfoStep)

            // Затем обрабатываем фиктивные пути
            while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
                val (selectedRow, selectedCol) = selectCell(balancedProblem, remainingSupplies, remainingDemands, false)
                if (selectedRow == -1 || selectedCol == -1) break

                val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol)
                steps.add(createSolutionStep(
                    context,
                    stepNumber++,
                    selectedRow,
                    selectedCol,
                    balancedProblem,
                    solution,
                    remainingSupplies,
                    remainingDemands,
                    quantity,
                    "Выбираем фиктивную ячейку"
                ))
            }
        }

        return steps
    }

    private fun hasRealWork(remainingSupplies: DoubleArray, remainingDemands: DoubleArray): Boolean {
        // Проверяем, есть ли поставщики с запасами и магазины с потребностями в пределах исходной матрицы
        val hasRealSuppliers = remainingSupplies.take(originalRows).any { it > 0 }
        val hasRealDemands = remainingDemands.take(originalCols).any { it > 0 }
        return hasRealSuppliers && hasRealDemands
    }

    private fun selectCell(
        balancedProblem: TransportationProblem,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        onlyReal: Boolean
    ): Pair<Int, Int> {
        var maxDiff = -1.0
        var selectedRow = -1
        var selectedCol = -1

        // Ограничим поиск реальными ячейками, если необходимо
        val rowsLimit = if (onlyReal) minOf(originalRows, remainingSupplies.size) else remainingSupplies.size
        val colsLimit = if (onlyReal) minOf(originalCols, remainingDemands.size) else remainingDemands.size

        // Поиск по строкам
        for (i in 0 until rowsLimit) {
            if (remainingSupplies[i] <= 0) continue

            val availableCosts = mutableListOf<Pair<Double, Int>>()
            for (j in 0 until colsLimit) {
                if (remainingDemands[j] > 0) {
                    availableCosts.add(balancedProblem.costs[i][j] to j)
                }
            }

            if (availableCosts.size >= 2) {
                availableCosts.sortBy { it.first }
                val diff = availableCosts[1].first - availableCosts[0].first
                if (diff > maxDiff) {
                    maxDiff = diff
                    selectedRow = i
                    selectedCol = availableCosts[0].second
                }
            } else if (availableCosts.size == 1 && maxDiff == -1.0) {
                // Если только одна доступная ячейка и еще не нашли разницу
                selectedRow = i
                selectedCol = availableCosts[0].second
            }
        }

        // Поиск по столбцам
        for (j in 0 until colsLimit) {
            if (remainingDemands[j] <= 0) continue

            val availableCosts = mutableListOf<Pair<Double, Int>>()
            for (i in 0 until rowsLimit) {
                if (remainingSupplies[i] > 0) {
                    availableCosts.add(balancedProblem.costs[i][j] to i)
                }
            }

            if (availableCosts.size >= 2) {
                availableCosts.sortBy { it.first }
                val diff = availableCosts[1].first - availableCosts[0].first
                if (diff > maxDiff) {
                    maxDiff = diff
                    selectedRow = availableCosts[0].second
                    selectedCol = j
                }
            } else if (availableCosts.size == 1 && maxDiff == -1.0) {
                // Если только одна доступная ячейка и еще не нашли разницу
                selectedRow = availableCosts[0].second
                selectedCol = j
            }
        }

        // Если не нашли по разностям, выбираем минимальный элемент
        if (selectedRow == -1 || selectedCol == -1) {
            var minCost = Double.MAX_VALUE
            for (i in 0 until rowsLimit) {
                if (remainingSupplies[i] <= 0) continue
                for (j in 0 until colsLimit) {
                    if (remainingDemands[j] > 0 && balancedProblem.costs[i][j] < minCost) {
                        minCost = balancedProblem.costs[i][j]
                        selectedRow = i
                        selectedCol = j
                    }
                }
            }
        }

        // Если все еще не нашли ячейку и обрабатываем только реальные пути,
        // значит все реальные пути распределены и нужно вернуть (-1, -1)
        if ((selectedRow == -1 || selectedCol == -1) && onlyReal) {
            return -1 to -1
        }

        // Если не нашли ячейку среди всех, находим любую доступную ячейку
        if (selectedRow == -1 || selectedCol == -1) {
            for (i in remainingSupplies.indices) {
                if (remainingSupplies[i] <= 0) continue
                for (j in remainingDemands.indices) {
                    if (remainingDemands[j] > 0) {
                        return i to j
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

        Log.d("DoublePreferenceSolver", context.getString(R.string.balance_check, totalSupply, totalDemand))
        if (problem.isBalanced()) {
            Log.d("DoublePreferenceSolver", context.getString(R.string.balance_result, if (problem == balancedProblem) "закрытой" else "открытой"))
        }
    }

    private fun logStepInfo(
        context: Context,
        stepCount: Int,
        selectedRow: Int,
        selectedCol: Int,
        balancedProblem: TransportationProblem,
        quantity: Double,
        isFictive: Boolean
    ) {
        val prefix = if (isFictive) "Фиктивный путь: " else ""
        Log.d("DoublePreferenceSolver", prefix + context.getString(
            R.string.step_description,
            stepCount, selectedRow + 1, selectedCol + 1,
            balancedProblem.costs[selectedRow][selectedCol]
        ))
        Log.d("DoublePreferenceSolver", context.getString(
            R.string.distribution_description,
            selectedRow + 1, selectedCol + 1,
            balancedProblem.supplies[selectedRow],
            balancedProblem.demands[selectedCol],
            quantity
        ))
    }

    private fun logTotalCost(context: Context, problem: TransportationProblem, solution: Array<DoubleArray>) {
        val totalCost = problem.calculateTotalCost(solution)
        Log.d("DoublePreferenceSolver", context.getString(R.string.total_cost, totalCost))
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

    private fun createSolutionStep(
        context: Context,
        stepNumber: Int,
        selectedRow: Int,
        selectedCol: Int,
        balancedProblem: TransportationProblem,
        solution: Array<DoubleArray>,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        quantity: Double,
        additionalDescription: String = ""
    ): SolutionStep {
        val isFictiveCell = selectedRow >= originalRows || selectedCol >= originalCols

        val description = if (additionalDescription.isNotEmpty()) {
            "$additionalDescription: " + context.getString(
                R.string.step_description,
                stepNumber, selectedRow + 1, selectedCol + 1,
                balancedProblem.costs[selectedRow][selectedCol]
            ) + "\n" + context.getString(
                R.string.distribution_description,
                selectedRow + 1, selectedCol + 1,
                remainingSupplies[selectedRow] + quantity,
                remainingDemands[selectedCol] + quantity,
                quantity
            )
        } else {
            context.getString(
                R.string.step_description,
                stepNumber, selectedRow + 1, selectedCol + 1,
                balancedProblem.costs[selectedRow][selectedCol]
            ) + "\n" + context.getString(
                R.string.distribution_description,
                selectedRow + 1, selectedCol + 1,
                remainingSupplies[selectedRow] + quantity,
                remainingDemands[selectedCol] + quantity,
                quantity
            )
        }

        return SolutionStep(
            stepNumber = stepNumber,
            description = description,
            currentSolution = Array(solution.size) { i -> solution[i].clone() },
            remainingSupplies = remainingSupplies.clone(),
            remainingDemands = remainingDemands.clone(),
            selectedRow = selectedRow,
            selectedCol = selectedCol,
            quantity = quantity,
            isFictive = isFictiveCell,
            fictiveDescription = when {
                selectedRow >= originalRows -> context.getString(R.string.fictive_supplier, selectedRow + 1)
                selectedCol >= originalCols -> context.getString(R.string.fictive_store, selectedCol + 1)
                else -> null
            }
        )
    }
}