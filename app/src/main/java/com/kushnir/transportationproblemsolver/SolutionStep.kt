package com.kushnir.transportationproblemsolver
data class SolutionStep(
    val selectedRow: Int,
    val selectedCol: Int,
    val quantity: Double,
    val currentSolution: Array<DoubleArray>,
    val remainingSupplies: DoubleArray,
    val remainingDemands: DoubleArray,
    val isFictive: Boolean = false,  // добавляем флаг для фиктивного элемента
    val fictiveDescription: String? = null  // добавляем описание фиктивности
)