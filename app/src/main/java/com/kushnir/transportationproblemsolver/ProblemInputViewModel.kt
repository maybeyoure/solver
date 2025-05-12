package com.kushnir.transportationproblemsolver

import android.content.Context
import android.widget.EditText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class ProblemInputViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState
    private var isInitialized = false

    private lateinit var costsMatrix: Array<Array<EditText>>
    private lateinit var suppliesInputs: Array<EditText>
    private lateinit var demandsInputs: Array<EditText>

    private var savedCostsValues: Array<Array<String>>? = null
    private var savedSuppliesValues: Array<String>? = null
    private var savedDemandsValues: Array<String>? = null

    fun initializeMatrices(context: Context, numRows: Int, numCols: Int) {
        // Проверяем, были ли уже инициализированы матрицы
        if (isInitialized) {
            // Если да, просто повторно отправляем существующие матрицы
            if (this::costsMatrix.isInitialized && this::suppliesInputs.isInitialized && this::demandsInputs.isInitialized) {
                _uiState.value = UiState.MatricesReady(costsMatrix, suppliesInputs, demandsInputs)
            }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                costsMatrix = Array(numRows) { Array(numCols) { EditText(context) } }
                suppliesInputs = Array(numRows) { EditText(context) }
                demandsInputs = Array(numCols) { EditText(context) }
                _uiState.value = UiState.MatricesReady(costsMatrix, suppliesInputs, demandsInputs)
            }
        }
    }

    fun saveMatrixValues() {
        if (this::costsMatrix.isInitialized && this::suppliesInputs.isInitialized && this::demandsInputs.isInitialized) {
            savedCostsValues = Array(costsMatrix.size) { i ->
                Array(costsMatrix[i].size) { j ->
                    costsMatrix[i][j].text.toString()
                }
            }

            savedSuppliesValues = Array(suppliesInputs.size) { i ->
                suppliesInputs[i].text.toString()
            }

            savedDemandsValues = Array(demandsInputs.size) { i ->
                demandsInputs[i].text.toString()
            }
        }
    }

    fun restoreMatrixValues() {
        if (this::costsMatrix.isInitialized && this::suppliesInputs.isInitialized && this::demandsInputs.isInitialized) {
            savedCostsValues?.let { saved ->
                for (i in saved.indices) {
                    for (j in saved[i].indices) {
                        if (i < costsMatrix.size && j < costsMatrix[i].size) {
                            costsMatrix[i][j].setText(saved[i][j])
                        }
                    }
                }
            }

            savedSuppliesValues?.let { saved ->
                for (i in saved.indices) {
                    if (i < suppliesInputs.size) {
                        suppliesInputs[i].setText(saved[i])
                    }
                }
            }

            savedDemandsValues?.let { saved ->
                for (i in saved.indices) {
                    if (i < demandsInputs.size) {
                        demandsInputs[i].setText(saved[i])
                    }
                }
            }
        }
    }

    sealed class UiState {
        object Initial : UiState()
        data class MatricesReady(
            val costsMatrix: Array<Array<EditText>>,
            val suppliesInputs: Array<EditText>,
            val demandsInputs: Array<EditText>
        ) : UiState()
    }
}