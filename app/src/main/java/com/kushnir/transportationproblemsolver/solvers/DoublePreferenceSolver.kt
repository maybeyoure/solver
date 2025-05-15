package com.kushnir.transportationproblemsolver.solvers

import android.content.Context
import android.util.Log
import com.kushnir.transportationproblemsolver.R
import com.kushnir.transportationproblemsolver.TransportationProblem
import com.kushnir.transportationproblemsolver.SolutionStep
import kotlin.math.abs

class DoublePreferenceSolver(private val problem: TransportationProblem) : TransportationSolver {

    private val originalRows: Int = problem.costs.size
    private val originalCols: Int = problem.costs[0].size
    private val EPSILON = 1e-10

    override fun solve(context: Context): Array<DoubleArray> {
        val balancedProblem = problem.makeBalanced()
        val (rows, cols) = balancedProblem.costs.size to balancedProblem.costs[0].size

        logBalanceInfo(context, balancedProblem)

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()

        // Заменяем used на более конкретные массивы
        val excludedRows = BooleanArray(rows) { false }
        val excludedCols = BooleanArray(cols) { false }
        val usedCells = Array(rows) { BooleanArray(cols) { false } }

        var stepCount = 1

        // Вычисляем клетки с предпочтениями ОДИН РАЗ в начале для реальных путей
        val (initialDoublePreferenceCells, initialSinglePreferenceCells) = findPreferenceCells(
            balancedProblem, remainingSupplies, remainingDemands, excludedRows, excludedCols, usedCells, true
        )

        // Сначала обрабатываем реальные пути
        while (hasRealWork(remainingSupplies, remainingDemands, excludedRows, excludedCols)) {
            val (selectedRow, selectedCol, isBasicZero) = selectCell(
                balancedProblem,
                remainingSupplies,
                remainingDemands,
                excludedRows,
                excludedCols,
                usedCells,
                true,
                initialDoublePreferenceCells,
                initialSinglePreferenceCells
            )

            if (selectedRow == -1 || selectedCol == -1) break

            val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol, isBasicZero)
            usedCells[selectedRow][selectedCol] = true

            // Если запас исчерпан, вычёркиваем строку
            if (remainingSupplies[selectedRow] <= 0) {
                excludedRows[selectedRow] = true
            }

            // Если потребность исчерпана и при этом строка не исчерпана,
            // вычёркиваем столбец
            if (remainingDemands[selectedCol] <= 0 && remainingSupplies[selectedRow] > 0) {
                excludedCols[selectedCol] = true
            }

            logStepInfo(context, stepCount++, selectedRow, selectedCol, balancedProblem, quantity, false, isBasicZero)
        }

        // Затем обрабатываем фиктивные пути, если остались нераспределенные ресурсы
        if (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            Log.d("DoublePreferenceSolver", "All real paths allocated, switching to fictive paths")

            // Вычисляем предпочтения для фиктивных путей ОДИН РАЗ
            val (fictiveDoublePreferenceCells, fictiveSinglePreferenceCells) = findPreferenceCells(
                balancedProblem, remainingSupplies, remainingDemands, excludedRows, excludedCols, usedCells, false
            )

            while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
                val (selectedRow, selectedCol, isBasicZero) = selectCell(
                    balancedProblem,
                    remainingSupplies,
                    remainingDemands,
                    excludedRows,
                    excludedCols,
                    usedCells,
                    false,
                    fictiveDoublePreferenceCells,
                    fictiveSinglePreferenceCells
                )

                if (selectedRow == -1 || selectedCol == -1) break

                val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol, isBasicZero)
                usedCells[selectedRow][selectedCol] = true

                // Если запас исчерпан, вычёркиваем строку
                if (remainingSupplies[selectedRow] <= 0) {
                    excludedRows[selectedRow] = true
                }

                // Если потребность исчерпана и при этом строка не исчерпана,
                // вычёркиваем столбец
                if (remainingDemands[selectedCol] <= 0 && remainingSupplies[selectedRow] > 0) {
                    excludedCols[selectedCol] = true
                }

                logStepInfo(context, stepCount++, selectedRow, selectedCol, balancedProblem, quantity, true, isBasicZero)
            }
        }

        logTotalCost(context, problem, solution)
        return solution
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
            Log.d("DoublePreferenceSolver", "Шаг $stepCount: Базисный нуль в клетке (${selectedRow + 1}, ${selectedCol + 1})")
        } else {
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
    }

    private fun findPreferenceCells(
        balancedProblem: TransportationProblem,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray,
        usedCells: Array<BooleanArray>,
        onlyReal: Boolean
    ): Pair<List<Pair<Int, Int>>, List<Pair<Int, Int>>> {
        val rowsLimit = if (onlyReal) minOf(originalRows, remainingSupplies.size) else remainingSupplies.size
        val colsLimit = if (onlyReal) minOf(originalCols, remainingDemands.size) else remainingDemands.size

        // 1. Определяем минимальные элементы в каждой строке
        val rowMinIndices = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until rowsLimit) {
            if (excludedRows[i]) continue

            // Сначала находим минимальное значение в строке
            var minValue = Double.MAX_VALUE
            for (j in 0 until colsLimit) {
                if (excludedCols[j] || usedCells[i][j]) continue

                val cost = balancedProblem.costs[i][j]
                if (cost < minValue) {
                    minValue = cost
                }
            }

            // Затем добавляем все клетки с минимальным значением
            for (j in 0 until colsLimit) {
                if (excludedCols[j] || usedCells[i][j]) continue

                val cost = balancedProblem.costs[i][j]
                if (abs(cost - minValue) < EPSILON) { // Используем EPSILON для сравнения с плавающей точкой
                    rowMinIndices.add(Pair(i, j))
                }
            }
        }

        // 2. Определяем минимальные элементы в каждом столбце
        val colMinIndices = mutableListOf<Pair<Int, Int>>()
        for (j in 0 until colsLimit) {
            if (excludedCols[j]) continue

            // Сначала находим минимальное значение в столбце
            var minValue = Double.MAX_VALUE
            for (i in 0 until rowsLimit) {
                if (excludedRows[i] || usedCells[i][j]) continue

                val cost = balancedProblem.costs[i][j]
                if (cost < minValue) {
                    minValue = cost
                }
            }

            // Затем добавляем все клетки с минимальным значением
            for (i in 0 until rowsLimit) {
                if (excludedRows[i] || usedCells[i][j]) continue

                val cost = balancedProblem.costs[i][j]
                if (abs(cost - minValue) < EPSILON) { // Используем EPSILON для сравнения с плавающей точкой
                    colMinIndices.add(Pair(i, j))
                }
            }
        }

        // Для отладки выводим найденные минимумы
        Log.d("DoublePreferenceSolver", "Row minimum indices: $rowMinIndices")
        Log.d("DoublePreferenceSolver", "Column minimum indices: $colMinIndices")

        // 3. Находим клетки с двойным предпочтением
        val doublePreferredCells = rowMinIndices.filter { rowCell ->
            colMinIndices.any { colCell ->
                colCell.first == rowCell.first && colCell.second == rowCell.second
            }
        }

        Log.d("DoublePreferenceSolver", "Double preference cells: $doublePreferredCells")

        // 4. Находим клетки с одинарным предпочтением
        val allPreferredCells = (rowMinIndices + colMinIndices).distinct()
        val singlePreferredCells = allPreferredCells.filter { cell ->
            !doublePreferredCells.contains(cell)
        }

        Log.d("DoublePreferenceSolver", "Single preference cells: $singlePreferredCells")

        return Pair(doublePreferredCells, singlePreferredCells)
    }

    override fun solveWithSteps(context: Context): List<SolutionStep> {
        val steps = mutableListOf<SolutionStep>()
        val balancedProblem = problem.makeBalanced()
        val (rows, cols) = balancedProblem.costs.size to balancedProblem.costs[0].size
        val basicZeroCells = mutableListOf<Pair<Int, Int>>()

        addFictiveStepIfNeeded(context, steps, balancedProblem, rows, cols)

        val solution = Array(rows) { DoubleArray(cols) }
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()

        // Заменяем used на более конкретные массивы
        val excludedRows = BooleanArray(rows) { false }
        val excludedCols = BooleanArray(cols) { false }
        val usedCells = Array(rows) { BooleanArray(cols) { false } }

        var stepNumber = steps.size + 1

        // Вычисляем клетки с предпочтениями ОДИН РАЗ в начале для реальных путей
        val (initialDoublePreferenceCells, initialSinglePreferenceCells) = findPreferenceCells(
            balancedProblem, remainingSupplies, remainingDemands, excludedRows, excludedCols, usedCells, true
        )

        Log.d("DoublePreferenceSolver", "Initial double preference cells: $initialDoublePreferenceCells")
        Log.d("DoublePreferenceSolver", "Initial single preference cells: $initialSinglePreferenceCells")

        // Сначала обрабатываем реальные пути
        while (hasRealWork(remainingSupplies, remainingDemands, excludedRows, excludedCols)) {
            // Используем изначально вычисленные предпочтения
            val (selectedRow, selectedCol, isBasicZero) = selectCell(
                balancedProblem,
                remainingSupplies,
                remainingDemands,
                excludedRows,
                excludedCols,
                usedCells,
                true,
                initialDoublePreferenceCells,
                initialSinglePreferenceCells
            )
            val actuallyBasicZero = isBasicZero ||
                    (selectedRow != -1 && selectedCol != -1 &&
                            (remainingSupplies[selectedRow] == 0.0 || remainingDemands[selectedCol] == 0.0))

            if (actuallyBasicZero) {
                Log.d("DEBUG", "Добавляем базисный нуль в клетку ($selectedRow, $selectedCol)")
                basicZeroCells.add(Pair(selectedRow, selectedCol))
                solution[selectedRow][selectedCol] = 0.0
            }

            if (selectedRow == -1 || selectedCol == -1) break

            val quantity = allocateResources(solution, remainingSupplies, remainingDemands, selectedRow, selectedCol, isBasicZero)
            usedCells[selectedRow][selectedCol] = true

            // Если запас исчерпан, вычёркиваем строку
            if (remainingSupplies[selectedRow] <= 0) {
                excludedRows[selectedRow] = true
            }

            // Если потребность исчерпана и при этом строка не исчерпана,
            // вычёркиваем столбец
            if (remainingDemands[selectedCol] <= 0 && remainingSupplies[selectedRow] > 0) {
                excludedCols[selectedCol] = true
            }

            // Для отладки
            Log.d("DoublePreferenceSolver",
                "Step ${stepNumber}: Selected cell ($selectedRow, $selectedCol) with quantity $quantity" +
                        ", isBasicZero=$isBasicZero"
            )
            if (isBasicZero) {
                basicZeroCells.add(Pair(selectedRow, selectedCol))
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
                initialDoublePreferenceCells,
                initialSinglePreferenceCells,
                isBasicZero = actuallyBasicZero,
                basicZeroCells = ArrayList(basicZeroCells)
            ))
        }

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
                fictiveDescription = "Переход к фиктивным путям",
                doublePreferenceCells = initialDoublePreferenceCells,
                singlePreferenceCells = initialSinglePreferenceCells
            )
            steps.add(fictiveInfoStep)

            // Вычисляем предпочтения для фиктивных путей ОДИН РАЗ до начала цикла
            val (fictiveDoublePreferenceCells, fictiveSinglePreferenceCells) = findPreferenceCells(
                balancedProblem,
                remainingSupplies,
                remainingDemands,
                excludedRows,
                excludedCols,
                usedCells,
                false
            )

            Log.d(
                "DoublePreferenceSolver",
                "Fictive double preference cells: $fictiveDoublePreferenceCells"
            )
            Log.d(
                "DoublePreferenceSolver",
                "Fictive single preference cells: $fictiveSinglePreferenceCells"
            )

// Затем обрабатываем фиктивные пути
            while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
                val (selectedRow, selectedCol, isBasicZero) = selectCell(
                    balancedProblem,
                    remainingSupplies,
                    remainingDemands,
                    excludedRows,
                    excludedCols,
                    usedCells,
                    false,
                    fictiveDoublePreferenceCells,
                    fictiveSinglePreferenceCells
                )

                val actuallyBasicZero = isBasicZero ||
                        (selectedRow != -1 && selectedCol != -1 &&
                                (remainingSupplies[selectedRow] == 0.0 || remainingDemands[selectedCol] == 0.0))

                if (actuallyBasicZero) {
                    Log.d("DEBUG", "Добавляем базисный нуль в клетку ($selectedRow, $selectedCol)")
                    basicZeroCells.add(Pair(selectedRow, selectedCol))
                    solution[selectedRow][selectedCol] = 0.0
                }

                if (selectedRow == -1 || selectedCol == -1) break

                val quantity = allocateResources(
                    solution,
                    remainingSupplies,
                    remainingDemands,
                    selectedRow,
                    selectedCol,
                    isBasicZero
                )
                usedCells[selectedRow][selectedCol] = true

                // Если запас исчерпан, вычёркиваем строку
                if (remainingSupplies[selectedRow] <= 0) {
                    excludedRows[selectedRow] = true
                }

                // Если потребность исчерпана и при этом строка не исчерпана,
                // вычёркиваем столбец
                if (remainingDemands[selectedCol] <= 0 && remainingSupplies[selectedRow] > 0) {
                    excludedCols[selectedCol] = true
                }

                // Для отладки
                Log.d(
                    "DoublePreferenceSolver",
                    "Fictive step ${stepNumber}: Selected cell ($selectedRow, $selectedCol) with quantity $quantity, isBasicZero=$isBasicZero"
                )

                steps.add(
                    createSolutionStep(
                        context,
                        stepNumber++,
                        selectedRow,
                        selectedCol,
                        balancedProblem,
                        solution,
                        remainingSupplies,
                        remainingDemands,
                        quantity,
                        fictiveDoublePreferenceCells,
                        fictiveSinglePreferenceCells,
                        isBasicZero = actuallyBasicZero,
                        basicZeroCells = ArrayList(basicZeroCells)
                    )
                )
            }
        }

        return steps
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

    private fun selectCell(
        balancedProblem: TransportationProblem,
        remainingSupplies: DoubleArray,
        remainingDemands: DoubleArray,
        excludedRows: BooleanArray,
        excludedCols: BooleanArray,
        usedCells: Array<BooleanArray>,
        onlyReal: Boolean,
        doublePreferredCells: List<Pair<Int, Int>>,
        singlePreferredCells: List<Pair<Int, Int>>
    ): Triple<Int, Int, Boolean> {
        val rowsLimit =
            if (onlyReal) minOf(originalRows, remainingSupplies.size) else remainingSupplies.size
        val colsLimit =
            if (onlyReal) minOf(originalCols, remainingDemands.size) else remainingDemands.size

        // Сначала выбираем клетку по обычному правилу (предпочтение или минимальный тариф)
        var selectedRow = -1
        var selectedCol = -1
        var isBasicZero = false

        // Фильтруем доступные клетки с двойным предпочтением
        val availableDoublePreferred = doublePreferredCells.filter { (i, j) ->
            i < rowsLimit && j < colsLimit &&
                    !excludedRows[i] && !excludedCols[j] && !usedCells[i][j]
        }

        if (availableDoublePreferred.isNotEmpty()) {
            val selected =
                availableDoublePreferred.minByOrNull { balancedProblem.costs[it.first][it.second] }
                    ?: availableDoublePreferred.first()
            selectedRow = selected.first
            selectedCol = selected.second
        } else {
            // Фильтруем доступные клетки с одинарным предпочтением
            val availableSinglePreferred = singlePreferredCells.filter { (i, j) ->
                i < rowsLimit && j < colsLimit &&
                        !excludedRows[i] && !excludedCols[j] && !usedCells[i][j]
            }

            if (availableSinglePreferred.isNotEmpty()) {
                val selected =
                    availableSinglePreferred.minByOrNull { balancedProblem.costs[it.first][it.second] }
                        ?: availableSinglePreferred.first()
                selectedRow = selected.first
                selectedCol = selected.second
            } else {
                // Если нет клеток с предпочтением, ищем клетку с минимальным тарифом
                var minCost = Double.MAX_VALUE

                for (i in 0 until rowsLimit) {
                    if (excludedRows[i]) continue

                    for (j in 0 until colsLimit) {
                        if (excludedCols[j] || usedCells[i][j]) continue

                        if (balancedProblem.costs[i][j] < minCost) {
                            minCost = balancedProblem.costs[i][j]
                            selectedRow = i
                            selectedCol = j
                        }
                    }
                }
            }
        }
        if (selectedRow != -1 && selectedCol != -1) {
            // Случай 1: min(supply, demand) = 0, но мы всё равно выбрали эту клетку
            if (remainingSupplies[selectedRow] == 0.0 || remainingDemands[selectedCol] == 0.0) {
                isBasicZero = true
                Log.d("DEBUG", "Базисный нуль: случай 1 - нулевой запас/спрос в клетке ($selectedRow, $selectedCol)")
            }
            // Случай 2: Клетка на пересечении вычеркнутой и невычеркнутой строки/столбца
            else if ((excludedRows[selectedRow] && !excludedCols[selectedCol]) ||
                (!excludedRows[selectedRow] && excludedCols[selectedCol])) {
                isBasicZero = true
                Log.d("DEBUG", "Базисный нуль: случай 2 - пересечение в клетке ($selectedRow, $selectedCol)")
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
        if (isBasicZero) {
            // Это базисный нуль - специальный случай, когда запасы и потребности уже равны нулю
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
    private fun logBalanceInfo(context: Context, balancedProblem: TransportationProblem) {
        val totalSupply = balancedProblem.supplies.sum()
        val totalDemand = balancedProblem.demands.sum()

        Log.d("DoublePreferenceSolver", context.getString(R.string.balance_check, totalSupply, totalDemand))
        if (problem.isBalanced()) {
            Log.d("DoublePreferenceSolver", context.getString(R.string.balance_result, if (problem == balancedProblem) "закрытой" else "открытой"))
        }
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
        doublePreferenceCells: List<Pair<Int, Int>>? = null,
        singlePreferenceCells: List<Pair<Int, Int>>? = null,
        isBasicZero: Boolean = false,
        basicZeroCells: MutableList<Pair<Int, Int>> = mutableListOf(),
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
            },
            doublePreferenceCells = doublePreferenceCells,
            singlePreferenceCells = singlePreferenceCells,
            basicZeroCells = basicZeroCells
        )
    }
}