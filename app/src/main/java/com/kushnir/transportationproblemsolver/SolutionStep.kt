package com.kushnir.transportationproblemsolver

import java.io.Serializable

data class SolutionStep(
    val stepNumber: Int = 0,
    val description: String,
    val selectedRow: Int,
    val selectedCol: Int,
    val quantity: Double,
    val currentSolution: Array<DoubleArray>,
    val remainingSupplies: DoubleArray,
    val remainingDemands: DoubleArray,
    val isFictive: Boolean = false,
    val fictiveDescription: String? = null,
    val rowPenalties: DoubleArray? = null,
    val colPenalties: DoubleArray? = null,
    val isVogel: Boolean = false
) : Serializable