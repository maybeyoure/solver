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

    fun initializeMatrices(context: Context, numRows: Int, numCols: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val costsMatrix = Array(numRows) { Array(numCols) { EditText(context) } }
                val suppliesInputs = Array(numRows) { EditText(context) }
                val demandsInputs = Array(numCols) { EditText(context) }
                _uiState.value = UiState.MatricesReady(costsMatrix, suppliesInputs, demandsInputs)
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