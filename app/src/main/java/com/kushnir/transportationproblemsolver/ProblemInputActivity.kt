package com.kushnir.transportationproblemsolver

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProblemInputActivity : AppCompatActivity() {
    private val viewModel: ProblemInputViewModel by viewModels()
    private lateinit var tableLayout: TableLayout
    private lateinit var btnSolve: Button
    private lateinit var spinnerMethod: Spinner
    private lateinit var spinnerObjective: Spinner
    private lateinit var costsMatrix: Array<Array<EditText>>
    private lateinit var suppliesInputs: Array<EditText>
    private lateinit var demandsInputs: Array<EditText>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_problem_input)

        // Инициализация views
        tableLayout = findViewById(R.id.table_costs)
        btnSolve = findViewById(R.id.btn_solve)
        spinnerMethod = findViewById(R.id.spinner_method)
        spinnerObjective = findViewById(R.id.spinner_objective)

        // Настройка спиннеров - это быстрая операция, можно оставить в главном потоке
        setupSpinners()

        // Получение размеров матрицы
        val numRows = intent.getIntExtra("numRows", 0)
        val numCols = intent.getIntExtra("numCols", 0)
        viewModel.initializeMatrices(this, numRows, numCols)

        // Запускаем тяжелые операции в фоновом потоке
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProblemInputViewModel.UiState.MatricesReady -> {
                            costsMatrix = state.costsMatrix
                            suppliesInputs = state.suppliesInputs
                            demandsInputs = state.demandsInputs
                            createTable(numRows, numCols)
                        }

                        ProblemInputViewModel.UiState.Initial -> {
                            // Начальное состояние, ничего не делаем
                        }
                    }
                }
            }
        }// Настройка кнопок
        setupButtons()
    }

    private fun setupButtons() {
        val btnBack: ImageButton = findViewById(R.id.btn_back)
        btnBack.setOnClickListener {
            onBackPressed()
        }

        btnSolve.setOnClickListener {
            if (validateInput()) {
                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val problem = createProblem()
                        withContext(Dispatchers.Main) {
                            val intent = Intent(
                                this@ProblemInputActivity,
                                SolutionActivity::class.java
                            ).apply {
                                putExtra("problem", problem)
                                putExtra("methodType", spinnerMethod.selectedItem.toString())
                                putExtra("objectiveType", spinnerObjective.selectedItem.toString())
                            }
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ProblemInputActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Некорректные данные!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Выносим настройку спиннеров в отдельную функцию
    private fun setupSpinners() {
        val methodAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.methods,
            android.R.layout.simple_spinner_item
        )
        val objectiveAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.objectives,
            android.R.layout.simple_spinner_item
        )

        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        objectiveAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerMethod.adapter = methodAdapter
        spinnerObjective.adapter = objectiveAdapter
        spinnerMethod.setSelection(0)
        spinnerObjective.setSelection(0)
        spinnerMethod.prompt = getString(R.string.select_method_prompt)
        spinnerObjective.prompt = getString(R.string.select_objective_prompt)
    }

    private fun createProblem(): TransportationProblem {
        // Создаем массив затрат из матрицы EditText
        val costs = Array(costsMatrix.size) { i ->
            DoubleArray(costsMatrix[i].size) { j ->
                costsMatrix[i][j].text.toString().toDouble()
            }
        }

        // Создаем массив запасов
        val supplies = DoubleArray(suppliesInputs.size) { i ->
            suppliesInputs[i].text.toString().toDouble()
        }

        // Создаем массив потребностей
        val demands = DoubleArray(demandsInputs.size) { i ->
            demandsInputs[i].text.toString().toDouble()
        }

        return TransportationProblem(costs, supplies, demands)
    }

    private fun createTable(numRows: Int, numCols: Int) {
        // Создание заголовков
        val headerRow = TableRow(this)
        headerRow.addView(TextView(this))

        // Добавление заголовков для магазинов
        for (j in 0 until numCols) {
            val headerCell = TextView(this).apply {
                text = getString(R.string.header_store, j + 1)
                setPadding(8, 8, 8, 8)
            }
            headerRow.addView(headerCell)
        }

        // Добавление колонки для запасов
        val supplyHeader = TextView(this).apply {
            text = getString(R.string.header_supplies)
            setPadding(8, 8, 8, 8)
        }
        headerRow.addView(supplyHeader)
        tableLayout.addView(headerRow)

        // Создание строк с данными
        for (i in 0 until numRows) {
            val row = TableRow(this)

            // Добавление заголовка строки (поставщик)
            val rowHeader = TextView(this).apply {
                text = getString(R.string.header_supplier, i + 1)
                setPadding(8, 8, 8, 8)
            }
            row.addView(rowHeader)

            // Добавление ячеек для тарифов
            for (j in 0 until numCols) {
                val cell = EditText(this).apply {
                    setPadding(8, 8, 8, 8)
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    filters = arrayOf(android.text.InputFilter.LengthFilter(5))
                }
                costsMatrix[i][j] = cell
                row.addView(cell)
            }

            // Добавление поля для запасов
            val supplyCell = EditText(this).apply {
                setPadding(8, 8, 8, 8)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                filters = arrayOf(android.text.InputFilter.LengthFilter(5))
            }
            suppliesInputs[i] = supplyCell
            row.addView(supplyCell)

            tableLayout.addView(row)
        }

        // Добавление строки для потребностей
        val demandsRow = TableRow(this)
        demandsRow.addView(TextView(this))

        // Добавление полей для потребностей
        for (j in 0 until numCols) {
            val demandCell = EditText(this).apply {
                setPadding(8, 8, 8, 8)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                filters = arrayOf(android.text.InputFilter.LengthFilter(5))
            }
            demandsInputs[j] = demandCell
            demandsRow.addView(demandCell)
        }

        // Добавление заголовка "Потребности" в последнюю ячейку
        val demandHeader = TextView(this).apply {
            text = getString(R.string.header_demands)
            setPadding(8, 8, 8, 8)
        }
        demandsRow.addView(demandHeader)

        tableLayout.addView(demandsRow)
    }

    private fun validateInput(): Boolean {
        // Проверка матрицы тарифов
        for (i in costsMatrix.indices) {
            for (j in costsMatrix[i].indices) {
                if (costsMatrix[i][j].text.isNullOrEmpty()) return false
                try {
                    costsMatrix[i][j].text.toString().toDouble()
                } catch (e: NumberFormatException) {
                    return false
                }
            }
        }

        // Проверка запасов
        for (supply in suppliesInputs) {
            if (supply.text.isNullOrEmpty()) return false
            try {
                supply.text.toString().toDouble()
            } catch (e: NumberFormatException) {
                return false
            }
        }

        // Проверка потребностей
        for (demand in demandsInputs) {
            if (demand.text.isNullOrEmpty()) return false
            try {
                demand.text.toString().toDouble()
            } catch (e: NumberFormatException) {
                return false
            }
        }

        return true
    }
}