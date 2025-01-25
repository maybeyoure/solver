package com.kushnir.transportationproblemsolver

import java.io.Serializable

class TransportationProblem(
    val costs: Array<DoubleArray>,
    val supplies: DoubleArray,
    val demands: DoubleArray
) : Serializable {

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

    fun solveByDoublePreference(): Array<DoubleArray> {
        val balancedProblem = this.makeBalanced()
        val rows = balancedProblem.costs.size
        val cols = balancedProblem.costs[0].size

        println("Начальные данные:")
        println("Тарифы: ${balancedProblem.costs.contentDeepToString()}")
        println("Запасы: ${balancedProblem.supplies.contentToString()}")
        println("Потребности: ${balancedProblem.demands.contentToString()}")

        // Создаем массив для хранения результата
        val solution = Array(rows) { DoubleArray(cols) }

        // Создаем копии запасов и потребностей для изменения в процессе решения
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()

        // Находим минимумы по строкам и столбцам
        val rowMins = Array(rows) { i -> balancedProblem.costs[i].minOrNull() ?: Double.MAX_VALUE }
        val colMins = Array(cols) { j -> balancedProblem.costs.map { it[j] }.minOrNull() ?: Double.MAX_VALUE }

        // Находим разности между двумя минимальными элементами в строках и столбцах
        val rowDiffs = Array(rows) { i ->
            val sorted = balancedProblem.costs[i].sorted()
            if (sorted.size >= 2) sorted[1] - sorted[0] else 0.0
        }
        val colDiffs = Array(cols) { j ->
            val sorted = balancedProblem.costs.map { it[j] }.sorted()
            if (sorted.size >= 2) sorted[1] - sorted[0] else 0.0
        }

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
                        if (remainingSupplies[i] > 0 && remainingDemands[j] > 0 && costs[i][j] < minCost) {
                            minCost = costs[i][j]
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
            println("Распределение: [$maxDiffRow][$maxDiffCol] = $quantity")
            println("Оставшиеся запасы: ${remainingSupplies.contentToString()}")
            println("Оставшиеся потребности: ${remainingDemands.contentToString()}")
        }
        return solution
    }
    fun solveByDoublePreferenceWithSteps(): List<SolutionStep> {
        println("DEBUG: Starting solveByDoublePreferenceWithSteps")
        val steps = mutableListOf<SolutionStep>()

        val balancedProblem = this.makeBalanced()
        println("DEBUG: Original dimensions: ${costs.size}x${costs[0].size}")
        println("DEBUG: Balanced dimensions: ${balancedProblem.costs.size}x${balancedProblem.costs[0].size}")

        val rows = balancedProblem.costs.size
        val cols = balancedProblem.costs[0].size

        // Массив для хранения результата
        val solution = Array(rows) { DoubleArray(cols) }

        // Копии запасов и потребностей
        val remainingSupplies = balancedProblem.supplies.clone()
        val remainingDemands = balancedProblem.demands.clone()

        while (remainingSupplies.any { it > 0 } && remainingDemands.any { it > 0 }) {
            var selectedRow = -1
            var selectedCol = -1
            var minCost = Double.MAX_VALUE

            // Поиск ячейки с минимальной стоимостью
            for (i in 0 until rows) {
                if (remainingSupplies[i] <= 0) continue
                for (j in 0 until cols) {
                    if (remainingDemands[j] <= 0) continue
                    val cost = balancedProblem.costs[i][j]
                    if (cost < minCost) {
                        minCost = cost
                        selectedRow = i
                        selectedCol = j
                    }
                }
            }

            if (selectedRow == -1 || selectedCol == -1) break

            // Распределение максимально возможного количества
            val quantity = minOf(remainingSupplies[selectedRow], remainingDemands[selectedCol])
            solution[selectedRow][selectedCol] = quantity
            remainingSupplies[selectedRow] -= quantity
            remainingDemands[selectedCol] -= quantity

            // Сохраняем текущий шаг
            steps.add(
                SolutionStep(
                    selectedRow = selectedRow,
                    selectedCol = selectedCol,
                    quantity = quantity,
                    currentSolution = solution.map { it.clone() }.toTypedArray(),
                    remainingSupplies = remainingSupplies.clone(),
                    remainingDemands = remainingDemands.clone(),
                    isFictive = selectedRow >= balancedProblem.costs.size || selectedCol >= balancedProblem.costs[0].size,
                    fictiveDescription = when {
                        selectedRow >= balancedProblem.costs.size -> "Фиктивный поставщик П${selectedRow + 1}"
                        selectedCol >= balancedProblem.costs[0].size -> "Фиктивный магазин M${selectedCol + 1}"
                        else -> null
                    }
                )
            )
        }

        return steps
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