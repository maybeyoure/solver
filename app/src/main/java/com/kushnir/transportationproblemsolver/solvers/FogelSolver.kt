package com.kushnir.transportationproblemsolver.solvers

import android.content.Context
import android.util.Log
import com.kushnir.transportationproblemsolver.R
import com.kushnir.transportationproblemsolver.SolutionStep
import com.kushnir.transportationproblemsolver.TransportationProblem

class FogelSolver(private val problem: TransportationProblem) : TransportationSolver {
    private val originalRows: Int = problem.costs.size
    private val originalCols: Int = problem.costs[0].size

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
        while (hasRealWork(remainingSupplies, remainingDemands, originalRows, originalCols)) {
            // Вычисляем штрафы только для реальных путей
            val (rowPenalties, colPenalties) = calculatePenalties(
                balancedProblem.costs, remainingSupplies, remainingDemands, used, true
            )

            // Находим максимальный штраф
            val (isRow, maxIndex) = findMaxPenaltyIndex(
                rowPenalties, colPenalties, remainingSupplies, remainingDemands, true
            )

            // Если не удалось найти индекс с максимальным штрафом, просто находим любую доступную ячейку
            val (selectedRow, selectedCol) = if (maxIndex == -1) {
                findAnyAvailableRealCell(remainingSupplies, remainingDemands, used, rows, cols)
            } else {
                findMinCostCell(isRow, maxIndex, balancedProblem.costs, remainingSupplies, remainingDemands, used, rows, cols, true)
            }

            // Если не нашли доступную ячейку, переходим к фиктивным путям
            if (selectedRow == -1 || selectedCol == -1) break

            // Распределяем ресурсы
            val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
            solution[selectedRow][selectedCol] = quantity
            remainingSupplies[selectedRow] -= quantity
            remainingDemands[selectedCol] -= quantity
            used[selectedRow][selectedCol] = true

            // Логируем результат шага
            Log.d("FogelSolver", "Step $stepCount: Selected real cell ($selectedRow, $selectedCol) with quantity $quantity")
            stepCount++
        }

        // Затем обрабатываем фиктивные пути, если остались нераспределенные ресурсы
        while (hasRemainingWork(remainingSupplies, remainingDemands)) {
            // Вычисляем штрафы для оставшихся путей (включая фиктивные)
            val (rowPenalties, colPenalties) = calculatePenalties(
                balancedProblem.costs, remainingSupplies, remainingDemands, used, false
            )

            // Находим максимальный штраф
            val (isRow, maxIndex) = findMaxPenaltyIndex(
                rowPenalties, colPenalties, remainingSupplies, remainingDemands, false
            )

            // Если не удалось найти индекс с максимальным штрафом, просто находим любую доступную ячейку
            val (selectedRow, selectedCol) = if (maxIndex == -1) {
                findAnyAvailableCell(remainingSupplies, remainingDemands, used, rows, cols)
            } else {
                findMinCostCell(isRow, maxIndex, balancedProblem.costs, remainingSupplies, remainingDemands, used, rows, cols, false)
            }

            // Если не нашли доступную ячейку, прерываем цикл
            if (selectedRow == -1 || selectedCol == -1) break

            // Распределяем ресурсы
            val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
            solution[selectedRow][selectedCol] = quantity
            remainingSupplies[selectedRow] -= quantity
            remainingDemands[selectedCol] -= quantity
            used[selectedRow][selectedCol] = true

            // Логируем результат шага
            Log.d("FogelSolver", "Step $stepCount: Selected fictive cell ($selectedRow, $selectedCol) with quantity $quantity")
            stepCount++
        }

        return solution
    }

    override fun solveWithSteps(context: Context): List<SolutionStep> {
        val steps = mutableListOf<SolutionStep>()
        val balancedProblem = problem.makeBalanced()
        val rows = balancedProblem.costs.size
        val cols = balancedProblem.costs[0].size

        // Добавляем шаг с информацией о балансировке, если необходимо
        addBalanceInfoStep(context, steps, balancedProblem, rows, cols)

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()
        val used = Array(rows) { BooleanArray(cols) }

        // Сначала обрабатываем реальные пути
        while (hasRealWork(remainingSupplies, remainingDemands, originalRows, originalCols)) {
            // Вычисляем штрафы только для реальных путей
            val (rowPenalties, colPenalties) = calculatePenalties(
                balancedProblem.costs, remainingSupplies, remainingDemands, used, true
            )

            // Находим максимальный штраф
            val (isRow, maxIndex) = findMaxPenaltyIndex(
                rowPenalties, colPenalties, remainingSupplies, remainingDemands, true
            )

            // Если не удалось найти индекс с максимальным штрафом, просто находим любую доступную ячейку
            val (selectedRow, selectedCol) = if (maxIndex == -1) {
                findAnyAvailableRealCell(remainingSupplies, remainingDemands, used, rows, cols)
            } else {
                findMinCostCell(isRow, maxIndex, balancedProblem.costs, remainingSupplies, remainingDemands, used, rows, cols, true)
            }

            // Если не нашли доступную ячейку, переходим к фиктивным путям
            if (selectedRow == -1 || selectedCol == -1) break

            // Распределяем ресурсы
            val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
            solution[selectedRow][selectedCol] = quantity
            remainingSupplies[selectedRow] -= quantity
            remainingDemands[selectedCol] -= quantity
            used[selectedRow][selectedCol] = true

            // Добавляем шаг в список
            steps.add(createSolutionStep(
                context,
                steps.size + 1,
                selectedRow,
                selectedCol,
                balancedProblem,
                solution,
                remainingSupplies,
                remainingDemands,
                quantity,
                isRow,
                rowPenalties,
                colPenalties
            ))
        }

        // Затем обрабатываем фиктивные пути, если остались нераспределенные ресурсы
        if (hasRemainingWork(remainingSupplies, remainingDemands)) {
            // Добавляем информационный шаг о переходе к фиктивным путям
            val fictiveInfoStep = SolutionStep(
                stepNumber = steps.size + 1,
                description = "Все реальные пути распределены. Переходим к распределению фиктивных путей.",
                currentSolution = Array(solution.size) { i -> solution[i].clone() },
                remainingSupplies = remainingSupplies.clone(),
                remainingDemands = remainingDemands.clone(),
                selectedRow = -1,
                selectedCol = -1,
                quantity = 0.0,
                isFictive = true,
                fictiveDescription = "Переход к фиктивным путям",
                rowPenalties = null,
                colPenalties = null,
                isVogel = true
            )
            steps.add(fictiveInfoStep)

            while (hasRemainingWork(remainingSupplies, remainingDemands)) {
                // Вычисляем штрафы для оставшихся путей (включая фиктивные)
                val (rowPenalties, colPenalties) = calculatePenalties(
                    balancedProblem.costs, remainingSupplies, remainingDemands, used, false
                )

                // Находим максимальный штраф
                val (isRow, maxIndex) = findMaxPenaltyIndex(
                    rowPenalties, colPenalties, remainingSupplies, remainingDemands, false
                )

                // Если не удалось найти индекс с максимальным штрафом, просто находим любую доступную ячейку
                val (selectedRow, selectedCol) = if (maxIndex == -1) {
                    findAnyAvailableCell(remainingSupplies, remainingDemands, used, rows, cols)
                } else {
                    findMinCostCell(isRow, maxIndex, balancedProblem.costs, remainingSupplies, remainingDemands, used, rows, cols, false)
                }

                // Если не нашли доступную ячейку, прерываем цикл
                if (selectedRow == -1 || selectedCol == -1) break

                // Распределяем ресурсы
                val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
                solution[selectedRow][selectedCol] = quantity
                remainingSupplies[selectedRow] -= quantity
                remainingDemands[selectedCol] -= quantity
                used[selectedRow][selectedCol] = true

                // Добавляем шаг в список
                steps.add(createSolutionStep(
                    context,
                    steps.size + 1,
                    selectedRow,
                    selectedCol,
                    balancedProblem,
                    solution,
                    remainingSupplies,
                    remainingDemands,
                    quantity,
                    isRow,
                    rowPenalties,
                    colPenalties
                ))
            }
        }

        return steps
    }

    // Проверяет, остались ли нераспределенные реальные ресурсы
    private fun hasRealWork(supplies: DoubleArray, demands: DoubleArray, originalRows: Int, originalCols: Int): Boolean {
        // Проверяем, есть ли поставщики с запасами и магазины с потребностями в пределах исходной матрицы
        val hasRealSuppliers = supplies.take(originalRows).any { it > 0 }
        val hasRealDemands = demands.take(originalCols).any { it > 0 }
        return hasRealSuppliers && hasRealDemands
    }

    // Проверяет, остались ли любые нераспределенные ресурсы
    private fun hasRemainingWork(supplies: DoubleArray, demands: DoubleArray): Boolean {
        return supplies.any { it > 0 } && demands.any { it > 0 }
    }

    // Вычисляет штрафы для строк и столбцов
    private fun calculatePenalties(
        costs: Array<DoubleArray>,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        onlyReal: Boolean
    ): Pair<DoubleArray, DoubleArray> {
        val rows = costs.size
        val cols = costs[0].size

        // Штрафы для строк
        val rowPenalties = DoubleArray(rows) { i ->
            if (remainingSupplies[i] <= 0 || (onlyReal && i >= originalRows)) {
                Double.NEGATIVE_INFINITY // Строка уже полностью распределена или фиктивная при onlyReal=true
            } else {
                calculateRowPenalty(i, costs, remainingDemands, used, cols, onlyReal)
            }
        }

        // Штрафы для столбцов
        val colPenalties = DoubleArray(cols) { j ->
            if (remainingDemands[j] <= 0 || (onlyReal && j >= originalCols)) {
                Double.NEGATIVE_INFINITY // Столбец уже полностью распределен или фиктивный при onlyReal=true
            } else {
                calculateColPenalty(j, costs, remainingSupplies, used, rows, onlyReal)
            }
        }

        return Pair(rowPenalties, colPenalties)
    }

    // Вычисляет штраф для строки
    private fun calculateRowPenalty(
        row: Int,
        costs: Array<DoubleArray>,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        cols: Int,
        onlyReal: Boolean
    ): Double {
        val availableCosts = mutableListOf<Double>()

        for (j in 0 until cols) {
            // Пропускаем фиктивные столбцы, если onlyReal=true
            if (onlyReal && j >= originalCols) continue

            if (!used[row][j] && remainingDemands[j] > 0) {
                availableCosts.add(costs[row][j])
            }
        }

        return when {
            availableCosts.isEmpty() -> Double.NEGATIVE_INFINITY
            availableCosts.size == 1 -> 0.0 // Если только одна ячейка, штраф 0
            else -> {
                val sorted = availableCosts.sorted()
                sorted[1] - sorted[0] // Разница между двумя наименьшими значениями
            }
        }
    }

    // Вычисляет штраф для столбца
    private fun calculateColPenalty(
        col: Int,
        costs: Array<DoubleArray>,
        remainingSupplies: DoubleArray,
        used: Array<BooleanArray>,
        rows: Int,
        onlyReal: Boolean
    ): Double {
        val availableCosts = mutableListOf<Double>()

        for (i in 0 until rows) {
            // Пропускаем фиктивные строки, если onlyReal=true
            if (onlyReal && i >= originalRows) continue

            if (!used[i][col] && remainingSupplies[i] > 0) {
                availableCosts.add(costs[i][col])
            }
        }

        return when {
            availableCosts.isEmpty() -> Double.NEGATIVE_INFINITY
            availableCosts.size == 1 -> 0.0 // Если только одна ячейка, штраф 0
            else -> {
                val sorted = availableCosts.sorted()
                sorted[1] - sorted[0] // Разница между двумя наименьшими значениями
            }
        }
    }

    // Находит индекс с максимальным штрафом
    private fun findMaxPenaltyIndex(
        rowPenalties: DoubleArray,
        colPenalties: DoubleArray,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        onlyReal: Boolean
    ): Pair<Boolean, Int> {
        var maxPenalty = Double.NEGATIVE_INFINITY
        var isRow = true
        var maxIndex = -1

        // Проверяем строки
        for (i in rowPenalties.indices) {
            // Пропускаем фиктивные строки, если onlyReal=true
            if (onlyReal && i >= originalRows) continue

            if (remainingSupplies[i] > 0 && rowPenalties[i] > maxPenalty) {
                maxPenalty = rowPenalties[i]
                isRow = true
                maxIndex = i
            }
        }

        // Проверяем столбцы
        for (j in colPenalties.indices) {
            // Пропускаем фиктивные столбцы, если onlyReal=true
            if (onlyReal && j >= originalCols) continue

            if (remainingDemands[j] > 0 && colPenalties[j] > maxPenalty) {
                maxPenalty = colPenalties[j]
                isRow = false
                maxIndex = j
            }
        }

        return Pair(isRow, maxIndex)
    }

    // Находит ячейку с минимальной стоимостью в выбранной строке или столбце
    private fun findMinCostCell(
        isRow: Boolean,
        maxIndex: Int,
        costs: Array<DoubleArray>,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        rows: Int,
        cols: Int,
        onlyReal: Boolean
    ): Pair<Int, Int> {
        var minCost = Double.POSITIVE_INFINITY
        var selectedRow = -1
        var selectedCol = -1

        if (isRow && maxIndex >= 0 && maxIndex < rows) {
            // Поиск минимальной стоимости в строке maxIndex
            for (j in 0 until cols) {
                // Пропускаем фиктивные столбцы, если onlyReal=true
                if (onlyReal && j >= originalCols) continue

                if (!used[maxIndex][j] && remainingDemands[j] > 0) {
                    // Проверяем, является ли ячейка реальной или мы обрабатываем фиктивные пути
                    val cost = costs[maxIndex][j]
                    if (cost < minCost) {
                        minCost = cost
                        selectedRow = maxIndex
                        selectedCol = j
                    }
                }
            }
        } else if (!isRow && maxIndex >= 0 && maxIndex < cols) {
            // Поиск минимальной стоимости в столбце maxIndex
            for (i in 0 until rows) {
                // Пропускаем фиктивные строки, если onlyReal=true
                if (onlyReal && i >= originalRows) continue

                if (!used[i][maxIndex] && remainingSupplies[i] > 0) {
                    // Проверяем, является ли ячейка реальной или мы обрабатываем фиктивные пути
                    val cost = costs[i][maxIndex]
                    if (cost < minCost) {
                        minCost = cost
                        selectedRow = i
                        selectedCol = maxIndex
                    }
                }
            }
        }

        return Pair(selectedRow, selectedCol)
    }

    // Находит любую доступную реальную ячейку
    private fun findAnyAvailableRealCell(
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        rows: Int,
        cols: Int
    ): Pair<Int, Int> {
        for (i in 0 until minOf(rows, originalRows)) {
            if (remainingSupplies[i] <= 0) continue
            for (j in 0 until minOf(cols, originalCols)) {
                if (remainingDemands[j] > 0 && !used[i][j]) {
                    return Pair(i, j)
                }
            }
        }
        return Pair(-1, -1)
    }

    // Находит любую доступную ячейку (включая фиктивные)
    private fun findAnyAvailableCell(
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        rows: Int,
        cols: Int
    ): Pair<Int, Int> {
        for (i in 0 until rows) {
            if (remainingSupplies[i] <= 0) continue
            for (j in 0 until cols) {
                if (remainingDemands[j] > 0 && !used[i][j]) {
                    return Pair(i, j)
                }
            }
        }
        return Pair(-1, -1)
    }

    // Добавляет информацию о балансировке в список шагов
    private fun addBalanceInfoStep(
        context: Context,
        steps: MutableList<SolutionStep>,
        balancedProblem: TransportationProblem,
        rows: Int,
        cols: Int
    ) {
        if (balancedProblem != problem) {
            val totalSupply = balancedProblem.supplies.sum()
            val totalDemand = balancedProblem.demands.sum()

            val step = if (totalDemand > problem.supplies.sum()) {
                SolutionStep(
                    stepNumber = 1,
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
                    fictiveDescription = context.getString(R.string.fictive_supplier, rows),
                    rowPenalties = null,
                    colPenalties = null,
                    isVogel = true
                )
            } else {
                SolutionStep(
                    stepNumber = 1,
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
                    fictiveDescription = context.getString(R.string.fictive_store, cols),
                    rowPenalties = null,
                    colPenalties = null,
                    isVogel = true
                )
            }
            steps.add(step)
        }
    }

    // Создает объект шага решения
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
        isRow: Boolean,
        rowPenalties: DoubleArray,
        colPenalties: DoubleArray,
        additionalDescription: String = ""
    ): SolutionStep {
        val isFictiveCell = selectedRow >= originalRows || selectedCol >= originalCols
        val cellType = if (isFictiveCell) "фиктивной" else "реальной"

        val penaltyDescription = context.getString(
            R.string.vogel_penalty_description,
            if (isRow) "строки" else "столбца",
            if (isRow) selectedRow + 1 else selectedCol + 1,
            if (isRow) rowPenalties[selectedRow] else colPenalties[selectedCol]
        )

        val stepDescription = context.getString(
            R.string.vogel_step_description,
            stepNumber,
            selectedRow + 1,
            selectedCol + 1,
            if (isRow) "строки ${selectedRow + 1}" else "столбца ${selectedCol + 1}",
            if (isRow) rowPenalties[selectedRow] else colPenalties[selectedCol],
            balancedProblem.costs[selectedRow][selectedCol]
        )

        val distributionDescription = context.getString(
            R.string.distribution_description,
            selectedRow + 1,
            selectedCol + 1,
            balancedProblem.supplies[selectedRow],
            balancedProblem.demands[selectedCol],
            quantity
        )

        val description = if (additionalDescription.isNotEmpty()) {
            "$additionalDescription: $stepDescription\n$penaltyDescription\n$distributionDescription"
        } else {
            "$stepDescription\n$penaltyDescription\n$distributionDescription"
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
            },
            rowPenalties = rowPenalties.clone(),
            colPenalties = colPenalties.clone(),
            isVogel = true
        )
    }
}