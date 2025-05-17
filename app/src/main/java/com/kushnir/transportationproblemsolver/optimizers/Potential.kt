package com.kushnir.transportationproblemsolver.optimizers

import android.content.Context
import android.util.Log
import com.kushnir.transportationproblemsolver.R
import com.kushnir.transportationproblemsolver.TransportationProblem
import kotlin.math.abs

class Potential(private val isMinimization: Boolean) {
    companion object {
        private const val EPSILON = 1e-10
        private const val MAX_ITERATIONS = 20
    }

    fun optimizeWithSteps(
        context: Context,
        problem: TransportationProblem,
        initialSolution: Array<DoubleArray>,
        initialBasicZeroCells: List<Pair<Int, Int>> = emptyList()
    ): List<OptimizationStep> {
        try {
            val steps = mutableListOf<OptimizationStep>()

            // Логируем базисные нули для отладки
            Log.d("Potential", "Получены базисные нули: $initialBasicZeroCells")
            val solution = copyMatrix(initialSolution)

            val basicZeroCells = initialBasicZeroCells.toMutableList()
            Log.d("DEBUG", "Базисные нули получены в оптимизаторе: $initialBasicZeroCells")
            steps.add(createInitialStep(context, problem, solution, basicZeroCells)) // ИСПРАВЛЕНО

            // Проверяем и исправляем вырожденность плана
            if (ensureNonDegeneratePlan(solution, basicZeroCells)) { // ИСПРАВЛЕНО
                steps.add(createDegeneracyFixStep(context, problem, solution, steps.size + 1))
            }
            if (initialSolution.size < problem.costs.size || initialSolution[0].size < problem.costs[0].size) {
                Log.e("Potential", "Несоответствие размеров: решение ${initialSolution.size}x${initialSolution[0].size}, " +
                        "задача ${problem.costs.size}x${problem.costs[0].size}")

                // Возвращаем шаг с ошибкой
                return listOf(
                    OptimizationStep(
                        stepNumber = 1,
                        description = "Ошибка: несоответствие размерностей решения и задачи",
                        currentSolution = copyMatrix(initialSolution),
                        isOptimal = true,
                        totalCost = calculateTotalCost(problem, initialSolution),
                        basicZeroCells = initialBasicZeroCells
                    )
                )
            }

            try {
                val potentials = safeCalculatePotentials(problem, solution, basicZeroCells)
                if (potentials == null) {
                    // Если не удалось вычислить потенциалы, добавляем ошибку и возвращаем
                    steps.add(
                        OptimizationStep(
                            stepNumber = steps.size + 1,
                            description = context.getString(R.string.optimization_potentials_error),
                            currentSolution = copyMatrix(solution),
                            isOptimal = true, // Чтобы прекратить дальнейшие вычисления
                            totalCost = calculateTotalCost(problem, solution),
                            basicZeroCells = initialBasicZeroCells
                        )
                    )
                    return steps
                }

                val (u, v) = potentials

                // Вычисляем оценки для свободных клеток с защитой от ошибок
                val evaluations = try {
                    calculateEvaluations(problem, solution, u, v)
                } catch (e: Exception) {
                    Log.e("Potential", "Ошибка при вычислении оценок", e)
                    steps.add(
                        OptimizationStep(
                            stepNumber = steps.size + 1,
                            description = "Ошибка при вычислении оценок: ${e.message}",
                            currentSolution = copyMatrix(solution),
                            isOptimal = true,
                            totalCost = calculateTotalCost(problem, solution),
                            basicZeroCells = initialBasicZeroCells
                        )
                    )
                    return steps
                }

                // Проверяем оптимальность
                var (isOptimal, pivotCell) = checkOptimality(solution, evaluations)

                // Добавляем шаг с потенциалами и оценками
                steps.add(
                    createPotentialsStep(
                        context,
                        problem,
                        solution,
                        u,
                        v,
                        evaluations,
                        steps.size + 1
                    )
                )

                // Если план уже оптимален, сразу завершаем
                if (isOptimal) {
                    steps.add(createFinalStep(context, problem, solution, steps.size + 1))
                    return steps
                }

                // Итеративный процесс оптимизации
                var iteration = 1

                while (!isOptimal && iteration <= MAX_ITERATIONS) {
                    // Проверяем, что pivotCell не null перед использованием
                    if (pivotCell == null) {
                        steps.add(
                            OptimizationStep(
                                stepNumber = steps.size + 1,
                                description = "Не удалось найти ведущую клетку для неоптимального плана",
                                currentSolution = copyMatrix(solution),
                                isOptimal = true, // Чтобы прекратить дальнейшие вычисления
                                totalCost = calculateTotalCost(problem, solution),
                                basicZeroCells = initialBasicZeroCells
                            )
                        )
                        break
                    }

                    // Строим цикл пересчета с проверкой на ошибки
                    val cycle = safeBuildCycle(solution, pivotCell)

                    // Если не удалось построить цикл, прерываем оптимизацию
                    if (cycle.isEmpty()) {
                        steps.add(
                            OptimizationStep(
                                stepNumber = steps.size + 1,
                                description = context.getString(R.string.optimization_cycle_error),
                                currentSolution = copyMatrix(solution),
                                isOptimal = true, // Чтобы прекратить дальнейшие вычисления
                                totalCost = calculateTotalCost(problem, solution),
                                basicZeroCells = initialBasicZeroCells
                            )
                        )
                        break
                    }

                    // Находим величину сдвига
                    val theta = findTheta(solution, cycle)
                    if (theta <= 0 || theta.isInfinite() || theta.isNaN()) {
                        Log.e("Potential", "Некорректное значение θ: $theta")
                        steps.add(
                            OptimizationStep(
                                stepNumber = steps.size + 1,
                                description = "Ошибка: некорректное значение величины сдвига",
                                currentSolution = copyMatrix(solution),
                                isOptimal = true,
                                totalCost = calculateTotalCost(problem, solution),
                                basicZeroCells = initialBasicZeroCells
                            )
                        )
                        break
                    }

                    // Добавляем шаг с циклом пересчета
                    steps.add(
                        createCycleStep(
                            context,
                            problem,
                            solution,
                            cycle,
                            pivotCell,
                            theta,
                            steps.size + 1
                        )
                    )

                    // Перераспределяем поставки
                    redistributeSupplies(solution, cycle, theta)

                    // Добавляем шаг с новым решением
                    steps.add(createRedistributionStep(context, problem, solution, steps.size + 1))

                    // Пересчитываем потенциалы и оценки с проверкой
                    val newPotentials = safeCalculatePotentials(problem, solution, basicZeroCells)
                    if (newPotentials == null) {
                        // Если не удалось пересчитать потенциалы
                        steps.add(
                            OptimizationStep(
                                stepNumber = steps.size + 1,
                                description = context.getString(R.string.optimization_potentials_error),
                                currentSolution = copyMatrix(solution),
                                isOptimal = true,
                                totalCost = calculateTotalCost(problem, solution),
                                basicZeroCells = initialBasicZeroCells
                            )
                        )
                        break
                    }

                    val (newU, newV) = newPotentials

                    // Вычисляем новые оценки с защитой от ошибок
                    val newEvaluations = try {
                        calculateEvaluations(problem, solution, newU, newV)
                    } catch (e: Exception) {
                        Log.e("Potential", "Ошибка при пересчете оценок", e)
                        steps.add(
                            OptimizationStep(
                                stepNumber = steps.size + 1,
                                description = "Ошибка при пересчете оценок: ${e.message}",
                                currentSolution = copyMatrix(solution),
                                isOptimal = true,
                                totalCost = calculateTotalCost(problem, solution),
                                basicZeroCells = initialBasicZeroCells
                            )
                        )
                        break
                    }

                    val optimalityResult = checkOptimality(solution, newEvaluations)
                    isOptimal = optimalityResult.first
                    pivotCell = optimalityResult.second

                    // Добавляем шаг с новыми потенциалами
                    steps.add(
                        createPotentialsStep(
                            context,
                            problem,
                            solution,
                            newU,
                            newV,
                            newEvaluations,
                            steps.size + 1
                        )
                    )

                    // Если план стал оптимальным, добавляем финальный шаг
                    if (isOptimal) {
                        steps.add(createFinalStep(context, problem, solution, steps.size + 1))
                        break
                    }

                    iteration++
                }

                // Если достигнуто максимальное число итераций
                if (iteration > MAX_ITERATIONS && !isOptimal) {
                    steps.add(
                        OptimizationStep(
                            stepNumber = steps.size + 1,
                            description = context.getString(R.string.optimization_max_iterations),
                            currentSolution = copyMatrix(solution),
                            isOptimal = true,
                            totalCost = calculateTotalCost(problem, solution)
                        )
                    )
                }
            } catch (e: Exception) {
                // Обрабатываем любые непредвиденные ошибки
                Log.e("Potential", "Непредвиденная ошибка при оптимизации", e)
                steps.add(
                    OptimizationStep(
                        stepNumber = steps.size + 1,
                        description = "Ошибка при оптимизации: ${e.message}",
                        currentSolution = copyMatrix(solution),
                        isOptimal = true,
                        totalCost = calculateTotalCost(problem, solution),
                        basicZeroCells = initialBasicZeroCells
                    )
                )
            }

            return steps
        } catch (e: Exception) {
            Log.e("Potential", "Неожиданная ошибка в optimizeWithSteps", e)

            // В случае любой ошибки возвращаем хотя бы один шаг
            return listOf(
                OptimizationStep(
                    stepNumber = 1,
                    description = "Ошибка при оптимизации: ${e.message}",
                    currentSolution = copyMatrix(initialSolution),
                    isOptimal = true,
                    totalCost = calculateTotalCost(problem, initialSolution)
                )
            )
        }
    }

    private fun copyMatrix(matrix: Array<DoubleArray>): Array<DoubleArray> {
        return Array(matrix.size) { i -> matrix[i].clone() }
    }
    private fun countOccupiedCells(solution: Array<DoubleArray>, basicZeroCells: List<Pair<Int, Int>>): Int {
        var count = 0
        for (i in solution.indices) {
            for (j in solution[0].indices) {
                if (solution[i][j] > EPSILON || basicZeroCells.contains(Pair(i, j))) {
                    count++
                }
            }
        }
        return count
    }

    private fun ensureNonDegeneratePlan(
        solution: Array<DoubleArray>,
        basicZeroCells: List<Pair<Int, Int>> = emptyList()
    ): Boolean {
        val rows = solution.size
        val cols = solution[0].size
        val requiredBasisCells = rows + cols - 1

        val basisCells = mutableSetOf<Pair<Int, Int>>()
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (solution[i][j] > EPSILON || basicZeroCells.contains(Pair(i, j))) {
                    basisCells.add(Pair(i, j))
                }
            }
        }
        if (basisCells.size >= requiredBasisCells) {
            return false
        }
        var added = 0
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                val cell = Pair(i, j)
                if (cell !in basisCells) {
                    solution[i][j] = EPSILON  // Используем EPSILON вместо 0.0
                    basisCells.add(cell)
                    added++

                    if (basisCells.size >= requiredBasisCells) {
                        return true
                    }
                }
            }
        }

        return added > 0
    }

    private fun safeCalculatePotentials(
        problem: TransportationProblem,
        solution: Array<DoubleArray>,
        basicZeroCells: List<Pair<Int, Int>> = emptyList()
    ): Pair<DoubleArray, DoubleArray>? {
        try {
            return calculatePotentials(problem, solution, basicZeroCells)
        } catch (e: Exception) {
            Log.e("Potential", "Ошибка при вычислении потенциалов", e)
            return null
        }
    }
    private fun calculatePotentials(
        problem: TransportationProblem,
        solution: Array<DoubleArray>,
        basicZeroCells: List<Pair<Int, Int>> = emptyList() // Добавлен параметр со списком базисных нулей
    ): Pair<DoubleArray, DoubleArray> {
        val rows = solution.size
        val cols = solution[0].size

        val u = DoubleArray(rows) { Double.NaN }
        val v = DoubleArray(cols) { Double.NaN }
        u[0] = 0.0

        val basisCells = mutableListOf<Pair<Int, Int>>()

        // Добавляем ячейки с положительными значениями
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (i < problem.costs.size && j < problem.costs[0].size && solution[i][j] > EPSILON) {
                    basisCells.add(Pair(i, j))
                }
            }
        }

        // Добавляем базисные нули из переданного списка
        for ((i, j) in basicZeroCells) {
            if (i < rows && j < cols && i < problem.costs.size && j < problem.costs[0].size &&
                !basisCells.contains(Pair(i, j))) {
                basisCells.add(Pair(i, j))
                Log.d("Potential", "Добавлен базисный ноль из списка в расчет потенциалов: ($i, $j)")
            }
        }

        // Автоматическое определение базисных нулей (можно оставить для страховки)
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (i < problem.costs.size && j < problem.costs[0].size &&
                    solution[i][j] == 0.0 &&
                    !basisCells.contains(Pair(i, j)) &&
                    isCellBasicZero(solution, i, j, basicZeroCells)) { // Передаем список базисных нулей
                    basisCells.add(Pair(i, j))
                    Log.d("Potential", "Автоматически добавлен базисный ноль в расчет потенциалов: ($i, $j)")
                }
            }
        }
        // Логируем финальный список базисных ячеек
        Log.d("DEBUG", "Финальный список базисных ячеек: $basisCells")

        var updated = true
        var iterations = 0
        val maxIterations = (rows + cols) * 2

        while (updated && iterations < maxIterations) {
            updated = false
            iterations++

            for ((i, j) in basisCells) {
                if (i >= rows || j >= cols) {
                    continue
                }

                if (!u[i].isNaN() && v[j].isNaN()) {
                    if (i < problem.costs.size && j < problem.costs[0].size) {
                        v[j] = problem.costs[i][j] - u[i]
                        updated = true
                    }
                } else if (u[i].isNaN() && !v[j].isNaN()) {
                    if (i < problem.costs.size && j < problem.costs[0].size) {
                        u[i] = problem.costs[i][j] - v[j]
                        updated = true
                    }
                }
            }
        }

        for (i in 0 until rows) {
            if (u[i].isNaN()) {
                Log.w("Potential", "Потенциал u[$i] не определен, устанавливаем 0")
                u[i] = 0.0
            }
        }

        for (j in 0 until cols) {
            if (v[j].isNaN()) {
                Log.w("Potential", "Потенциал v[$j] не определен, устанавливаем 0")
                v[j] = 0.0
            }
        }

        return Pair(u, v)
    }

    private fun isCellBasicZero(
        solution: Array<DoubleArray>,
        row: Int,
        col: Int,
        basicZeroCells: List<Pair<Int, Int>> = emptyList() // Добавляем опциональный параметр
    ): Boolean {
        // Если клетка уже в списке базисных нулей, возвращаем true
        if (basicZeroCells.contains(Pair(row, col))) {
            return true
        }

        var rowNonZeros = 0
        var colNonZeros = 0

        for (j in solution[row].indices) {
            if (solution[row][j] > EPSILON) {
                rowNonZeros++
            }
        }

        for (i in solution.indices) {
            if (solution[i][col] > EPSILON) {
                colNonZeros++
            }
        }

        val isIdentifiedBasicZero = solution[row][col] == 0.0 &&
                (rowNonZeros == 0 || colNonZeros == 0)

        return isIdentifiedBasicZero
    }

    private fun calculateTotalCost(
        problem: TransportationProblem,
        solution: Array<DoubleArray>
    ): Double {
        var totalCost = 0.0
        for (i in solution.indices) {
            for (j in solution[0].indices) {
                if (i < problem.costs.size && j < problem.costs[0].size) {
                    totalCost += solution[i][j] * problem.costs[i][j]
                }
            }
        }
        return totalCost
    }

    private fun calculateEvaluations(
        problem: TransportationProblem,
        solution: Array<DoubleArray>,
        u: DoubleArray,
        v: DoubleArray
    ): Array<DoubleArray> {
        val rows = solution.size
        val cols = solution[0].size
        val evaluations = Array(rows) { DoubleArray(cols) }

        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (i < problem.costs.size && j < problem.costs[0].size &&
                    !u[i].isNaN() && !v[j].isNaN()) {
                    evaluations[i][j] = problem.costs[i][j] - u[i] - v[j]
                } else {
                    evaluations[i][j] = 0.0
                }
            }
        }

        return evaluations
    }

    private fun checkOptimality(
        solution: Array<DoubleArray>,
        evaluations: Array<DoubleArray>,
        basicZeroCells: List<Pair<Int, Int>> = emptyList()
    ): Pair<Boolean, Pair<Int, Int>?> {
        val rows = evaluations.size
        val cols = evaluations[0].size

        // Для минимизации ищем отрицательные оценки, для максимизации - положительные
        var bestEvaluation = 0.0
        var bestCell: Pair<Int, Int>? = null

        for (i in 0 until rows) {
            for (j in 0 until cols) {
                // Пропускаем базисные клетки и базисные нули
                if (solution[i][j] > EPSILON || basicZeroCells.contains(Pair(i, j))) continue

                val value = evaluations[i][j]

                if ((isMinimization && value < bestEvaluation) ||
                    (!isMinimization && value > bestEvaluation)
                ) {
                    bestEvaluation = value
                    bestCell = Pair(i, j)
                }
            }
        }

        return Pair(
            (isMinimization && bestEvaluation >= -EPSILON) ||
                    (!isMinimization && bestEvaluation <= EPSILON),
            bestCell
        )
    }

    // Безопасное построение цикла с возможностью вернуть пустой список при ошибке
    private fun safeBuildCycle(
        solution: Array<DoubleArray>,
        pivotCell: Pair<Int, Int>
    ): List<Pair<Int, Int>> {
        try {
            val cycle = buildCycle(solution, pivotCell)
            if (cycle.isEmpty()) {
                Log.w("Potential", "Не удалось построить цикл, используем запасной вариант")
                return createSimpleCycle(solution, pivotCell)
            }
            return cycle
        } catch (e: Exception) {
            Log.e("Potential", "Ошибка при построении цикла", e)
            return createSimpleCycle(solution, pivotCell) // Возвращаем пустой список в крайнем случае
        }
    }

    private fun createSimpleCycle(
        solution: Array<DoubleArray>,
        pivotCell: Pair<Int, Int>
    ): List<Pair<Int, Int>> {
        val basisCells = mutableListOf<Pair<Int, Int>>()
        for (i in solution.indices) {
            for (j in solution[0].indices) {
                if (solution[i][j] > EPSILON && Pair(i, j) != pivotCell) {
                    basisCells.add(Pair(i, j))
                }
            }
        }

        // Ищем клетку в той же строке
        val rowCell = basisCells.firstOrNull { it.first == pivotCell.first }

        // Ищем клетку в том же столбце
        val colCell = basisCells.firstOrNull { it.second == pivotCell.second }

        if (rowCell != null && colCell != null) {
            // Ищем клетку на пересечении строки colCell и столбца rowCell
            val cornerCell = basisCells.firstOrNull {
                it.first == colCell.first && it.second == rowCell.second
            }

            if (cornerCell != null) {
                // Возвращаем простой прямоугольный цикл
                return listOf(pivotCell, rowCell, cornerCell, colCell)
            }
        }

        return emptyList()
    }

    private fun buildCycle(
        solution: Array<DoubleArray>,
        pivotCell: Pair<Int, Int>
    ): List<Pair<Int, Int>> {
        // Получаем список базисных клеток (с положительными поставками)
        val basisCells = mutableListOf<Pair<Int, Int>>()
        for (i in solution.indices) {
            for (j in solution[0].indices) {
                if (solution[i][j] > EPSILON) {
                    basisCells.add(Pair(i, j))
                }
            }
        }

        // Добавляем ведущую клетку в список, если ее там нет
        if (pivotCell !in basisCells) {
            basisCells.add(pivotCell)
        }

        // Начинаем поиск цикла из ведущей клетки
        val cycle = mutableListOf<Pair<Int, Int>>()
        cycle.add(pivotCell)

        // Используем алгоритм поиска в глубину
        val visited = mutableSetOf<Pair<Int, Int>>()
        if (findCycleDFS(pivotCell, pivotCell, basisCells, cycle, visited, false)) {
            return cycle
        }

        // Если не удалось найти цикл, возвращаем хотя бы базовый прямоугольный цикл
        return fallbackRectangularCycle(pivotCell, basisCells)
    }

    // Рекурсивный поиск цикла в глубину
    private fun findCycleDFS(
        startCell: Pair<Int, Int>,
        currentCell: Pair<Int, Int>,
        basisCells: List<Pair<Int, Int>>,
        cycle: MutableList<Pair<Int, Int>>,
        visited: MutableSet<Pair<Int, Int>>,
        canClose: Boolean
    ): Boolean {
        // Если можем замкнуть цикл и вернулись в начальную клетку
        if (canClose && currentCell == startCell && cycle.size >= 4) {
            return true
        }

        // Отмечаем текущую клетку как посещенную
        visited.add(currentCell)

        // Перебираем все базисные клетки
        for (cell in basisCells) {
            // Пропускаем уже посещенные клетки
            if (cell in visited && cell != startCell) continue

            // Клетка должна быть в той же строке или столбце
            if (cell.first == currentCell.first || cell.second == currentCell.second) {
                // Проверяем, что нет других клеток цикла между текущей и следующей
                if (isPathClear(currentCell, cell, cycle)) {
                    // Добавляем клетку в цикл
                    cycle.add(cell)

                    // Продолжаем поиск из этой клетки
                    val canCloseNext =
                        canClose || (cell.first == startCell.first || cell.second == startCell.second)
                    if (findCycleDFS(startCell, cell, basisCells, cycle, visited, canCloseNext)) {
                        return true
                    }

                    // Если не удалось построить цикл, удаляем клетку
                    cycle.removeAt(cycle.size - 1)
                }
            }
        }

        // Если не нашли цикл через эту клетку, снимаем отметку
        visited.remove(currentCell)
        return false
    }

    // Проверяет, что путь между клетками свободен
    private fun isPathClear(
        from: Pair<Int, Int>,
        to: Pair<Int, Int>,
        cycle: List<Pair<Int, Int>>
    ): Boolean {
        // Если клетки в одной строке
        if (from.first == to.first) {
            val row = from.first
            val minCol = minOf(from.second, to.second)
            val maxCol = maxOf(from.second, to.second)

            // Проверяем, что между ними нет других клеток цикла
            for (col in minCol + 1 until maxCol) {
                if (Pair(row, col) in cycle) return false
            }
            return true
        }

        // Если клетки в одном столбце
        if (from.second == to.second) {
            val col = from.second
            val minRow = minOf(from.first, to.first)
            val maxRow = maxOf(from.first, to.first)

            // Проверяем, что между ними нет других клеток цикла
            for (row in minRow + 1 until maxRow) {
                if (Pair(row, col) in cycle) return false
            }
            return true
        }

        return false
    }

    // Запасной метод для создания простого прямоугольного цикла
    private fun fallbackRectangularCycle(
        pivotCell: Pair<Int, Int>,
        basisCells: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {
        // Ищем клетку в той же строке
        val rowCell = basisCells.firstOrNull { it.first == pivotCell.first && it != pivotCell }

        // Ищем клетку в том же столбце
        val colCell = basisCells.firstOrNull { it.second == pivotCell.second && it != pivotCell }

        if (rowCell != null && colCell != null) {
            // Ищем клетку на пересечении строки colCell и столбца rowCell
            val cornerCell =
                basisCells.firstOrNull { it.first == colCell.first && it.second == rowCell.second }

            if (cornerCell != null) {
                // Возвращаем прямоугольный цикл
                return listOf(pivotCell, rowCell, cornerCell, colCell)
            }
        }

        // Если не удалось построить цикл, возвращаем пустой список
        return emptyList()
    }

    private fun findTheta(solution: Array<DoubleArray>, cycle: List<Pair<Int, Int>>): Double {
        // Проверка на случай, если цикл пустой или с одним элементом
        if (cycle.size <= 1) {
            return 0.0
        }

        var theta = Double.MAX_VALUE

        // Находим минимальное значение в отрицательных вершинах цикла (в нечетных позициях)
        for (i in 1 until cycle.size step 2) {
            val (row, col) = cycle[i]
            if (solution[row][col] < theta) {
                theta = solution[row][col]
            }
        }

        return theta
    }

    private fun redistributeSupplies(
        solution: Array<DoubleArray>,
        cycle: List<Pair<Int, Int>>,
        theta: Double
    ) {
        if (cycle.isEmpty() || theta <= 0) {
            return
        }

        // Перераспределяем поставки по циклу
        for (i in cycle.indices) {
            val (row, col) = cycle[i]
            if (i % 2 == 0) {
                // Положительные вершины цикла - добавляем theta
                solution[row][col] += theta
            } else {
                // Отрицательные вершины цикла - вычитаем theta
                solution[row][col] -= theta

                // Если значение близко к нулю, устанавливаем 0
                if (abs(solution[row][col]) < EPSILON) {
                    solution[row][col] = 0.0
                }
            }
        }
    }

    private fun createRedistributionStep(
        context: Context,
        problem: TransportationProblem,
        solution: Array<DoubleArray>,
        stepNumber: Int
    ): OptimizationStep {
        val totalCost = calculateTotalCost(problem, solution)

        return OptimizationStep(
            stepNumber = stepNumber,
            description = context.getString(
                R.string.optimization_after_redistribution,
                totalCost
            ),
            currentSolution = copyMatrix(solution),
            totalCost = totalCost
        )
    }

    private fun createInitialStep(
        context: Context,
        problem: TransportationProblem,
        solution: Array<DoubleArray>,
        basicZeroCells: List<Pair<Int, Int>> = emptyList()  // Добавляем этот параметр
    ): OptimizationStep {
        val totalCost = calculateTotalCost(problem, solution)

        return OptimizationStep(
            stepNumber = 1,
            description = context.getString(
                R.string.optimization_initial_plan,
                problem.costs.size,
                problem.costs[0].size,
                totalCost
            ),
            currentSolution = copyMatrix(solution),
            totalCost = totalCost,
            basicZeroCells = basicZeroCells  // Передаем список базисных нулей
        )
    }

    private fun createDegeneracyFixStep(
        context: Context,
        problem: TransportationProblem,
        solution: Array<DoubleArray>,
        stepNumber: Int,
        basicZeroCells: List<Pair<Int, Int>> = emptyList() // Добавьте этот параметр
    ): OptimizationStep {
        val totalCost = calculateTotalCost(problem, solution)

        return OptimizationStep(
            stepNumber = stepNumber,
            description = context.getString(R.string.optimization_fix_degeneracy),
            currentSolution = copyMatrix(solution),
            totalCost = totalCost,
            basicZeroCells = ArrayList(basicZeroCells) // Передаем базисные нули
        )
    }

    private fun createPotentialsStep(
        context: Context, problem: TransportationProblem, solution: Array<DoubleArray>,
        u: DoubleArray, v: DoubleArray, evaluations: Array<DoubleArray>, stepNumber: Int
    ): OptimizationStep {
        val totalCost = calculateTotalCost(problem, solution)

        return OptimizationStep(
            stepNumber = stepNumber,
            description = context.getString(
                R.string.optimization_potentials_calculated,
                u.joinToString(", ") { String.format("%.2f", it) },
                v.joinToString(", ") { String.format("%.2f", it) }
            ),
            currentSolution = copyMatrix(solution),
            potentialsU = u.clone(),
            potentialsV = v.clone(),
            evaluations = copyMatrix(evaluations),
            totalCost = totalCost
        )
    }

    private fun createCycleStep(
        context: Context, problem: TransportationProblem, solution: Array<DoubleArray>,
        cycle: List<Pair<Int, Int>>, pivotCell: Pair<Int, Int>, theta: Double, stepNumber: Int
    ): OptimizationStep {
        val totalCost = calculateTotalCost(problem, solution)

        val cycleDescription = cycle.joinToString(" → ") { "(${it.first + 1}, ${it.second + 1})" }

        return OptimizationStep(
            stepNumber = stepNumber,
            description = context.getString(
                R.string.optimization_cycle_built,
                pivotCell.first + 1, pivotCell.second + 1,
                cycleDescription,
                theta
            ),
            currentSolution = copyMatrix(solution),
            cyclePoints = cycle,
            pivotCell = pivotCell,
            theta = theta,
            totalCost = totalCost
        )
    }

    private fun createFinalStep(
        context: Context,
        problem: TransportationProblem,
        solution: Array<DoubleArray>,
        stepNumber: Int
    ): OptimizationStep {
        val totalCost = calculateTotalCost(problem, solution)

        return OptimizationStep(
            stepNumber = stepNumber,
            description = context.getString(
                R.string.optimization_optimal_found,
                totalCost
            ),
            currentSolution = copyMatrix(solution),
            isOptimal = true,
            totalCost = totalCost
        )
    }
}