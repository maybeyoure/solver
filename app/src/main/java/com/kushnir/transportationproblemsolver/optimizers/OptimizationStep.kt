package com.kushnir.transportationproblemsolver.optimizers

import java.io.Serializable

data class OptimizationStep(
    val stepNumber: Int,
    val description: String,
    val currentSolution: Array<DoubleArray>,
    val remainingSupplies: DoubleArray? = null,
    val remainingDemands: DoubleArray? = null,
    val potentialsU: DoubleArray? = null,  // Потенциалы строк
    val potentialsV: DoubleArray? = null,  // Потенциалы столбцов
    val evaluations: Array<DoubleArray>? = null,  // Оценки свободных клеток
    val cyclePoints: List<Pair<Int, Int>>? = null,  // Точки цикла пересчета
    val pivotCell: Pair<Int, Int>? = null,  // Ведущая клетка
    val theta: Double? = null,  // Величина сдвига по циклу
    val isOptimal: Boolean = false,
    val totalCost: Double = 0.0,
    val basicZeroCells: List<Pair<Int, Int>>? = null
) : Serializable