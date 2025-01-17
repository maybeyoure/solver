package com.kushnir.transportationproblemsolver

import java.io.Serializable

class TransportationProblem(
    val costs: Array<DoubleArray>,
    val supplies: DoubleArray,
    val demands: DoubleArray
) : Serializable {

    fun isValid(): Boolean {
        return costs.isNotEmpty() &&
                costs.all { it.size == demands.size } &&
                costs.size == supplies.size
    }

    fun isBalanced(): Boolean {
        val totalSupply = supplies.sum()
        val totalDemand = demands.sum()
        return kotlin.math.abs(totalSupply - totalDemand) < 0.0001
    }
}