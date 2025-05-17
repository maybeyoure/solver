package com.kushnir.transportationproblemsolver.solvers

import android.content.Context
import android.util.Log
import com.kushnir.transportationproblemsolver.R
import com.kushnir.transportationproblemsolver.SolutionStep
import com.kushnir.transportationproblemsolver.TransportationProblem

class FogelSolver(private val problem: TransportationProblem) : TransportationSolver {
    private val originalRows: Int = problem.costs.size
    private val originalCols: Int = problem.costs[0].size
    private val EPSILON = 1e-10

    override fun solve(context: Context): Array<DoubleArray> {
        val balancedProblem = problem.makeBalanced()
        val rows = balancedProblem.costs.size
        val cols = balancedProblem.costs[0].size

        logBalanceInfo(context, balancedProblem)

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()
        val usedCells = Array(rows) { BooleanArray(cols) }
        val excludedRows = BooleanArray(rows) { false }
        val excludedCols = BooleanArray(cols) { false }

        // Список для отслеживания базисных нулей
        val basicZeroCells = mutableListOf<Pair<Int, Int>>()

        var stepCount = 1

        // Сначала обрабатываем реальные пути
        while (hasRealWork(remainingSupplies, remainingDemands, excludedRows, excludedCols)) {
            // Вычисляем штрафы для реальных путей
            val (rowPenalties, colPenalties) = calculatePenalties(
                balancedProblem.costs, remainingSupplies, remainingDemands,
                usedCells, true, excludedRows, excludedCols
            )

            // Находим максимальный штраф
            val (isRow, maxIndex) = findMaxPenaltyIndex(
                rowPenalties, colPenalties, remainingSupplies, remainingDemands,
                true, excludedRows, excludedCols
            )

            // Выбираем ячейку и определяем, является ли она базисным нулем
            val (selectedRow, selectedCol, isBasicZero) = selectCell(
                balancedProblem,
                remainingSupplies,
                remainingDemands,
                excludedRows,
                excludedCols,
                usedCells,
                isRow,
                maxIndex,
                true
            )

            if (selectedRow == -1 || selectedCol == -1) break

            // Распределяем ресурсы, учитывая базисные нули
            val quantity = allocateResources(solution, remainingSupplies, remainingDemands,
                selectedRow, selectedCol, isBasicZero)
            usedCells[selectedRow][selectedCol] = true

            // Если это базисный нуль, добавляем его в список
            if (isBasicZero) {
                basicZeroCells.add(Pair(selectedRow, selectedCol))
            }

            // Обновляем исключенные строки и столбцы
            if (remainingSupplies[selectedRow] <= EPSILON) {
                excludedRows[selectedRow] = true
            }
            if (remainingDemands[selectedCol] <= EPSILON && remainingSupplies[selectedRow] > EPSILON) {
                excludedCols[selectedCol] = true
            }

            // Логируем результат шага
            logStepInfo(context, stepCount++, selectedRow, selectedCol,
                balancedProblem, quantity, false, isBasicZero)
        }

        // Обрабатываем фиктивные пути, если остались нераспределенные ресурсы
        if (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            Log.d("FogelSolver", "All real paths allocated, switching to fictive paths")

            while (hasRemainingWork(remainingSupplies, remainingDemands, excludedRows, excludedCols)) {
                // Вычисляем штрафы для оставшихся путей (включая фиктивные)
                val (rowPenalties, colPenalties) = calculatePenalties(
                    balancedProblem.costs, remainingSupplies, remainingDemands,
                    usedCells, false, excludedRows, excludedCols
                )

                // Находим максимальный штраф
                val (isRow, maxIndex) = findMaxPenaltyIndex(
                    rowPenalties, colPenalties, remainingSupplies, remainingDemands,
                    false, excludedRows, excludedCols
                )

                // Выбираем ячейку и определяем, является ли она базисным нулем
                val (selectedRow, selectedCol, isBasicZero) = selectCell(
                    balancedProblem,
                    remainingSupplies,
                    remainingDemands,
                    excludedRows,
                    excludedCols,
                    usedCells,
                    isRow,
                    maxIndex,
                    false
                )

                if (selectedRow == -1 || selectedCol == -1) break

                // Распределяем ресурсы, учитывая базисные нули
                val quantity = allocateResources(solution, remainingSupplies, remainingDemands,
                    selectedRow, selectedCol, isBasicZero)
                usedCells[selectedRow][selectedCol] = true

                // Если это базисный нуль, добавляем его в список
                if (isBasicZero) {
                    basicZeroCells.add(Pair(selectedRow, selectedCol))
                }

                // Обновляем исключенные строки и столбцы
                if (remainingSupplies[selectedRow] <= EPSILON) {
                    excludedRows[selectedRow] = true
                }
                if (remainingDemands[selectedCol] <= EPSILON && remainingSupplies[selectedRow] > EPSILON) {
                    excludedCols[selectedCol] = true
                }

                // Логируем результат шага
                logStepInfo(context, stepCount++, selectedRow, selectedCol,
                    balancedProblem, quantity, true, isBasicZero)
            }
        }

        logTotalCost(context, problem, solution)
        return solution
    }
    private fun logBalanceInfo(context: Context, balancedProblem: TransportationProblem) {
        val totalSupply = balancedProblem.supplies.sum()
        val totalDemand = balancedProblem.demands.sum()

        Log.d("FogelSolver", context.getString(R.string.balance_check, totalSupply, totalDemand))
        if (problem.isBalanced()) {
            Log.d("FogelSolver", context.getString(R.string.balance_result, if (problem == balancedProblem) "закрытой" else "открытой"))
        }
    }

    private fun logStepInfo(
        context: Context,
        stepCount: Int,
        selectedRow: Int,
        selectedCol: Int,
        balancedProblem: TransportationProblem,
        quantity: Double,
        isFictive: Boolean,
        isBasicZero: Boolean
    ) {
        val prefix = if (isFictive) "Фиктивный путь: " else ""

        if (isBasicZero) {
            // Упрощенное сообщение для базисных нулей
            Log.d("FogelSolver", "Шаг $stepCount: Базисный нуль в клетке (${selectedRow + 1}, ${selectedCol + 1})")
        } else {
            Log.d("FogelSolver", prefix + context.getString(
                R.string.step_description,
                stepCount, selectedRow + 1, selectedCol + 1,
                balancedProblem.costs[selectedRow][selectedCol]
            ))

            Log.d("FogelSolver", context.getString(
                R.string.distribution_description,
                selectedRow + 1, selectedCol + 1,
                balancedProblem.supplies[selectedRow],
                balancedProblem.demands[selectedCol],
                quantity
            ))
        }
    }

    private fun logTotalCost(context: Context, problem: TransportationProblem, solution: Array<DoubleArray>) {
        val totalCost = problem.calculateTotalCost(solution)
        Log.d("FogelSolver", context.getString(R.string.total_cost, totalCost))
    }
    override fun solveWithSteps(context: Context): List<SolutionStep> {
        val steps = mutableListOf<SolutionStep>()
        val balancedProblem = problem.makeBalanced()
        val rows = balancedProblem.costs.size
        val cols = balancedProblem.costs[0].size
        val basicZeroCells = mutableListOf<Pair<Int, Int>>()

        // Добавляем шаг с информацией о балансировке, если необходимо
        addBalanceInfoStep(context, steps, balancedProblem, rows, cols)

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()

        // Инициализируем массивы для исключенных строк/столбцов и использованных ячеек
        val excludedRows = BooleanArray(rows) { false }
        val excludedCols = BooleanArray(cols) { false }
        val usedCells = Array(rows) { BooleanArray(cols) { false } }

        var stepNumber = steps.size + 1

        // Сначала обрабатываем реальные пути
        while (hasRealWork(remainingSupplies, remainingDemands, excludedRows, excludedCols)) {
            // Вычисляем штрафы только для реальных путей с учетом исключенных строк и столбцов
            val (rowPenalties, colPenalties) = calculatePenalties(
                balancedProblem.costs, remainingSupplies, remainingDemands,
                usedCells, true, excludedRows, excludedCols
            )

            // Находим максимальный штраф и выбираем ячейку с учетом исключенных строк и столбцов
            val (isRow, maxIndex) = findMaxPenaltyIndex(
                rowPenalties, colPenalties, remainingSupplies, remainingDemands,
                true, excludedRows, excludedCols
            )

            // Выбираем ячейку и определяем, базисный ли это ноль
            val (selectedRow, selectedCol, isBasicZero) = selectCell(
                balancedProblem,
                remainingSupplies,
                remainingDemands,
                excludedRows,
                excludedCols,
                usedCells,
                isRow,
                maxIndex,
                true
            )

            // Дополнительная проверка на базисный ноль
            val actuallyBasicZero = isBasicZero ||
                    (selectedRow != -1 && selectedCol != -1 &&
                            (remainingSupplies[selectedRow] <= EPSILON || remainingDemands[selectedCol] <= EPSILON))

            if (selectedRow == -1 || selectedCol == -1) break

            // Обрабатываем базисный нуль
            if (actuallyBasicZero) {
                Log.d("FogelSolver", "Добавляем базисный нуль в клетку ($selectedRow, $selectedCol)")
                basicZeroCells.add(Pair(selectedRow, selectedCol))
                Log.d("DEBUG", "Текущие базисные нули: $basicZeroCells")
                solution[selectedRow][selectedCol] = 0.0
            }

            // Распределяем ресурсы
            val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol, actuallyBasicZero)
            usedCells[selectedRow][selectedCol] = true

            // Обновляем исключенные строки и столбцы
            if (remainingSupplies[selectedRow] <= EPSILON) {
                excludedRows[selectedRow] = true
            }

            if (remainingDemands[selectedCol] <= EPSILON && remainingSupplies[selectedRow] > EPSILON) {
                excludedCols[selectedCol] = true
            }

            // Добавляем шаг в список
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
                isRow,
                rowPenalties,
                colPenalties,
                isBasicZero = actuallyBasicZero,
                basicZeroCells = ArrayList(basicZeroCells)
            ))
        }

        // Затем обрабатываем фиктивные пути, если остались нераспределенные ресурсы
        if (hasRemainingWork(remainingSupplies, remainingDemands, excludedRows, excludedCols)) {
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
                fictiveDescription = "Переход к фиктивным путям",
                rowPenalties = null,
                colPenalties = null,
                isVogel = true,
                basicZeroCells = ArrayList(basicZeroCells) // Передаем текущие базисные нули
            )
            steps.add(fictiveInfoStep)

            while (hasRemainingWork(remainingSupplies, remainingDemands, excludedRows, excludedCols)) {
                // Вычисляем штрафы для оставшихся путей (включая фиктивные)
                val (rowPenalties, colPenalties) = calculatePenalties(
                    balancedProblem.costs, remainingSupplies, remainingDemands,
                    usedCells, false, excludedRows, excludedCols
                )

                // Находим максимальный штраф с учетом исключенных строк и столбцов
                val (isRow, maxIndex) = findMaxPenaltyIndex(
                    rowPenalties, colPenalties, remainingSupplies, remainingDemands,
                    false, excludedRows, excludedCols
                )

                // Выбираем ячейку и определяем, базисный ли это ноль
                val (selectedRow, selectedCol, isBasicZero) = selectCell(
                    balancedProblem,
                    remainingSupplies,
                    remainingDemands,
                    excludedRows,
                    excludedCols,
                    usedCells,
                    isRow,
                    maxIndex,
                    false
                )

                // Дополнительная проверка на базисный ноль
                val actuallyBasicZero = isBasicZero ||
                        (selectedRow != -1 && selectedCol != -1 &&
                                (remainingSupplies[selectedRow] <= EPSILON || remainingDemands[selectedCol] <= EPSILON))

                if (selectedRow == -1 || selectedCol == -1) break

                // Обрабатываем базисный нуль
                if (actuallyBasicZero) {
                    Log.d("FogelSolver", "Добавляем базисный нуль в клетку ($selectedRow, $selectedCol)")
                    basicZeroCells.add(Pair(selectedRow, selectedCol))
                    Log.d("DEBUG", "Текущие базисные нули: $basicZeroCells")
                    solution[selectedRow][selectedCol] = 0.0
                }

                // Распределяем ресурсы
                val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol, actuallyBasicZero)
                usedCells[selectedRow][selectedCol] = true

                // Обновляем исключенные строки и столбцы
                if (remainingSupplies[selectedRow] <= EPSILON) {
                    excludedRows[selectedRow] = true
                }

                if (remainingDemands[selectedCol] <= EPSILON && remainingSupplies[selectedRow] > EPSILON) {
                    excludedCols[selectedCol] = true
                }

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
                    isRow,
                    rowPenalties,
                    colPenalties,
                    isBasicZero = actuallyBasicZero,
                    basicZeroCells = ArrayList(basicZeroCells)
                ))
            }
        }
        Log.d("DEBUG", "Итоговые базисные нули: $basicZeroCells")
        return steps
    }

    private fun selectCell(
        balancedProblem: TransportationProblem,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray,
        usedCells: Array<BooleanArray>,
        isRow: Boolean,
        maxIndex: Int,
        onlyReal: Boolean
    ): Triple<Int, Int, Boolean> {
        // Получаем размеры из параметров
        val rows = remainingSupplies.size
        val cols = remainingDemands.size

        // Находим ячейку по максимальному штрафу или любую доступную
        val (selectedRow, selectedCol) = if (maxIndex == -1) {
            if (onlyReal) {
                findAnyAvailableRealCell(
                    remainingSupplies,
                    remainingDemands,
                    usedCells,
                    rows,
                    cols,
                    excludedRows,
                    excludedCols
                )
            } else {
                findAnyAvailableCell(
                    remainingSupplies,
                    remainingDemands,
                    usedCells,
                    rows,
                    cols,
                    excludedRows,
                    excludedCols
                )
            }
        } else {
            // Исправленный вызов - передаем excludedRows и excludedCols
            findMinCostCell(
                isRow,
                maxIndex,
                balancedProblem.costs,
                remainingSupplies,
                remainingDemands,
                usedCells,
                rows,
                cols,
                onlyReal,
                excludedRows,
                excludedCols
            )
        }
        var isBasicZero = false

        if (selectedRow != -1 && selectedCol != -1) {
            // ЯВНО проверяем столбцы с нулевой потребностью
            if (remainingDemands[selectedCol] == 0.0) {  // Точное сравнение с 0.0
                isBasicZero = true
                Log.d("FogelSolver", "Базисный нуль: нулевая потребность в столбце $selectedCol")
            }
            // Случай 1: min(supply, demand) = 0, но мы всё равно выбрали эту клетку
            if (remainingSupplies[selectedRow] <= EPSILON || remainingDemands[selectedCol] <= EPSILON) {
                isBasicZero = true
                Log.d("FogelSolver", "Базисный нуль: случай 1 - нулевой запас/спрос в клетке ($selectedRow, $selectedCol)")
            }
            // Случай 2: Клетка на пересечении вычеркнутой и невычеркнутой строки/столбца
            else if ((excludedRows[selectedRow] && !excludedCols[selectedCol]) ||
                (!excludedRows[selectedRow] && excludedCols[selectedCol])) {
                isBasicZero = true
                Log.d("FogelSolver", "Базисный нуль: случай 2 - пересечение в клетке ($selectedRow, $selectedCol)")
            }
        }

        return Triple(selectedRow, selectedCol, isBasicZero)
    }

    private fun allocateResources(
        solution: Array<DoubleArray>,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        selectedRow: Int,
        selectedCol: Int,
        isBasicZero: Boolean
    ): Double {
        // ВАЖНО: явно проверяем столбцы с нулевой потребностью
        if (isBasicZero || remainingDemands[selectedCol] == 0.0) {  // Добавляем условие
            // Это базисный нуль
            solution[selectedRow][selectedCol] = 0.0
            return 0.0
        }

        // Обычное распределение ресурсов
        val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
        solution[selectedRow][selectedCol] = quantity
        remainingSupplies[selectedRow] -= quantity
        remainingDemands[selectedCol] -= quantity
        return quantity
    }

    private fun hasRealWork(
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray
    ): Boolean {
        // Проверяем, есть ли поставщики с запасами и магазины с потребностями в пределах исходной матрицы
        val hasRealSuppliers = remainingSupplies.take(originalRows).withIndex()
            .any { (i, supply) -> supply > 0 && !excludedRows[i] }

        val hasRealDemands = remainingDemands.take(originalCols).withIndex()
            .any { (j, demand) -> demand > 0 && !excludedCols[j] }

        return hasRealSuppliers && hasRealDemands
    }

    // Обновленный метод hasRemainingWork
    private fun hasRemainingWork(
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray
    ): Boolean {
        // Проверяем, есть ли любые поставщики с запасами и магазины с потребностями
        val hasSuppliers = remainingSupplies.withIndex()
            .any { (i, supply) -> supply > 0 && !excludedRows[i] }

        val hasDemands = remainingDemands.withIndex()
            .any { (j, demand) -> demand > 0 && !excludedCols[j] }

        return hasSuppliers && hasDemands
    }

    private fun calculatePenalties(
        costs: Array<DoubleArray>,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        onlyReal: Boolean,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray
    ): Pair<DoubleArray, DoubleArray> {
        val rows = costs.size
        val cols = costs[0].size

        // Штрафы для строк
        val rowPenalties = DoubleArray(rows) { i ->
            if (remainingSupplies[i] <= 0 || excludedRows[i] || (onlyReal && i >= originalRows)) {
                Double.NEGATIVE_INFINITY // Строка исключена или фиктивная
            } else {
                calculateRowPenalty(i, costs, remainingDemands, used, cols, onlyReal, excludedCols)
            }
        }

        // Штрафы для столбцов - ВАЖНОЕ ИЗМЕНЕНИЕ: НЕ игнорируем столбцы с нулевой потребностью
        val colPenalties = DoubleArray(cols) { j ->
            if (excludedCols[j] || (onlyReal && j >= originalCols)) {
                Double.NEGATIVE_INFINITY // Столбец исключен или фиктивный
            } else {
                // ВАЖНО: Проверяем отдельно столбцы с нулевой потребностью
                if (remainingDemands[j] <= EPSILON) {
                    // Для столбцов с нулевой потребностью вычисляем фиктивный штраф
                    // Используем наименьший тариф из доступных клеток столбца, чтобы сделать
                    // этот столбец привлекательным для выбора
                    var minCost = Double.MAX_VALUE
                    var hasAvailableCell = false
                    for (i in 0 until rows) {
                        if (!excludedRows[i] && !used[i][j] && remainingSupplies[i] > 0) {
                            hasAvailableCell = true
                            minCost = minOf(minCost, costs[i][j])
                        }
                    }
                    if (hasAvailableCell) -minCost else Double.NEGATIVE_INFINITY
                } else {
                    // Обычный расчет штрафа для столбцов с ненулевой потребностью
                    calculateColPenalty(j, costs, remainingSupplies, used, rows, onlyReal, excludedRows)
                }
            }
        }

        return Pair(rowPenalties, colPenalties)
    }
    private fun calculateRowPenalty(
        row: Int,
        costs: Array<DoubleArray>,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        cols: Int,
        onlyReal: Boolean,
        excludedCols: BooleanArray
    ): Double {
        val availableCosts = mutableListOf<Double>()

        for (j in 0 until cols) {
            if ((onlyReal && j >= originalCols) || excludedCols[j] || used[row][j]) {
                continue
            }

            if (remainingDemands[j] >= 0) {
                availableCosts.add(costs[row][j])
            }
        }

        return when {
            availableCosts.isEmpty() -> Double.NEGATIVE_INFINITY // Нет доступных клеток
            availableCosts.size == 1 -> {
                // Если только одна клетка, возвращаем ИНВЕРТИРОВАННОЕ значение тарифа!
                // Это обеспечит, что клетки с меньшим тарифом будут иметь больший приоритет
                -availableCosts[0] // Использовать отрицательный тариф как штраф
            }
            else -> {
                // Стандартная логика расчета штрафа
                val sorted = availableCosts.sorted()
                val min1 = sorted[0]

                var min2 = Double.MAX_VALUE
                for (cost in sorted) {
                    if (cost > min1) {
                        min2 = cost
                        break
                    }
                }

                if (min2 == Double.MAX_VALUE && sorted.size > 1) {
                    min2 = sorted[1]
                }

                min2 - min1
            }
        }
    }
    private fun calculateColPenalty(
        col: Int,
        costs: Array<DoubleArray>,
        remainingSupplies: DoubleArray,
        used: Array<BooleanArray>,
        rows: Int,
        onlyReal: Boolean,
        excludedRows: BooleanArray
    ): Double {
        val availableCosts = mutableListOf<Double>()

        for (i in 0 until rows) {
            // Пропускаем фиктивные строки, исключенные строки и уже используемые клетки
            if ((onlyReal && i >= originalRows) || excludedRows[i] || used[i][col]) {
                continue
            }

            if (remainingSupplies[i] > 0) {
                availableCosts.add(costs[i][col])
            }
        }

        return when {
            availableCosts.isEmpty() -> Double.NEGATIVE_INFINITY // Нет доступных клеток
            availableCosts.size == 1 -> {
                // Если только одна клетка, возвращаем ИНВЕРТИРОВАННОЕ значение тарифа!
                // Это обеспечит, что клетки с меньшим тарифом будут иметь больший приоритет
                -availableCosts[0] // Использовать отрицательный тариф как штраф
            }
            else -> {
                // Стандартная логика расчета штрафа
                val sorted = availableCosts.sorted()
                val min1 = sorted[0]

                var min2 = Double.MAX_VALUE
                for (cost in sorted) {
                    if (cost > min1) {
                        min2 = cost
                        break
                    }
                }

                if (min2 == Double.MAX_VALUE && sorted.size > 1) {
                    min2 = sorted[1]
                }

                min2 - min1
            }
        }
    }
    private fun findMaxPenaltyIndex(
        rowPenalties: DoubleArray,
        colPenalties: DoubleArray,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        onlyReal: Boolean,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray
    ): Pair<Boolean, Int> {
        var maxPenalty = Double.NEGATIVE_INFINITY
        var isRow = true
        var maxIndex = -1

        // Проверяем строки - это первый цикл, который был заменен на проверку столбцов
        for (i in rowPenalties.indices) {
            if ((onlyReal && i >= originalRows) || excludedRows[i]) {
                continue
            }

            if (remainingSupplies[i] > 0 && rowPenalties[i] > maxPenalty) {
                maxPenalty = rowPenalties[i]
                isRow = true
                maxIndex = i
            }
        }

        // Проверяем столбцы
        for (j in colPenalties.indices) {
            if ((onlyReal && j >= originalCols) || excludedCols[j]) {
                continue
            }

            if (remainingDemands[j] >= 0 && colPenalties[j] > maxPenalty) {
                maxPenalty = colPenalties[j]
                isRow = false
                maxIndex = j
            }
        }

        // Если максимальный штраф отрицательный (это инвертированный тариф из одиночной клетки)
        // тогда это значит, что мы выбираем по минимальному тарифу
        if (maxPenalty < 0) {
            Log.d("FogelSolver", "Выбор по минимальному тарифу. Штраф: $maxPenalty")
        }

        return Pair(isRow, maxIndex)
    }

    private fun findMinCostCell(
        isRow: Boolean,
        maxIndex: Int,
        costs: Array<DoubleArray>,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        rows: Int,
        cols: Int,
        onlyReal: Boolean,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray
    ): Pair<Int, Int> {
        var minCost = Double.POSITIVE_INFINITY
        var selectedRow = -1
        var selectedCol = -1

        if (isRow && maxIndex >= 0 && maxIndex < rows && !excludedRows[maxIndex]) {
            // Поиск минимальной стоимости в строке maxIndex
            for (j in 0 until cols) {
                // Пропускаем фиктивные столбцы, исключенные столбцы и уже используемые клетки
                if ((onlyReal && j >= originalCols) || excludedCols[j] || used[maxIndex][j]) {
                    continue
                }

                // ВАЖНОЕ ИЗМЕНЕНИЕ: Проверяем и нулевые потребности, но только если это основной проход
                if (remainingDemands[j] >= 0) { // >= 0 вместо > 0, чтобы включить столбцы с нулевой потребностью
                    val cost = costs[maxIndex][j]
                    if (cost < minCost) {
                        minCost = cost
                        selectedRow = maxIndex
                        selectedCol = j
                    }
                }
            }
        } else if (!isRow && maxIndex >= 0 && maxIndex < cols && !excludedCols[maxIndex]) {
            // Аналогично для столбцов
            for (i in 0 until rows) {
                if ((onlyReal && i >= originalRows) || excludedRows[i] || used[i][maxIndex]) {
                    continue
                }

                if (remainingSupplies[i] > 0) {
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
    private fun findAnyAvailableRealCell(
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        rows: Int,
        cols: Int,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray
    ): Pair<Int, Int> {
        for (i in 0 until minOf(rows, originalRows)) {
            if (remainingSupplies[i] <= 0 || excludedRows[i]) continue
            for (j in 0 until minOf(cols, originalCols)) {
                if (remainingDemands[j] >= 0 && !excludedCols[j] && !used[i][j]) {
                    return Pair(i, j)
                }
            }
        }
        return Pair(-1, -1)
    }

    // Обновленный метод findAnyAvailableCell
    private fun findAnyAvailableCell(
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        used: Array<BooleanArray>,
        rows: Int,
        cols: Int,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray
    ): Pair<Int, Int> {
        for (i in 0 until rows) {
            if (remainingSupplies[i] <= 0 || excludedRows[i]) continue
            for (j in 0 until cols) {
                if (remainingDemands[j] > 0 && !excludedCols[j] && !used[i][j]) {
                    return Pair(i, j)
                }
            }
        }
        return Pair(-1, -1)
    }
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
                    isVogel = true,
                    basicZeroCells = emptyList() // Добавляем пустой список базисных нулей
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
                    isVogel = true,
                    basicZeroCells = emptyList() // Добавляем пустой список базисных нулей
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
        isRow: Boolean,
        rowPenalties: DoubleArray,
        colPenalties: DoubleArray,
        isBasicZero: Boolean = false,
        basicZeroCells: List<Pair<Int, Int>> = listOf(),
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
            isVogel = true,
            basicZeroCells = basicZeroCells
        )
    }
}