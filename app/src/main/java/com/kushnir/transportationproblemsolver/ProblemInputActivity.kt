package com.kushnir.transportationproblemsolver

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ProblemInputActivity : AppCompatActivity() {
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

        // Получение размерности из предыдущего активити
        val numRows = intent.getIntExtra("numRows", 0)
        val numCols = intent.getIntExtra("numCols", 0)

        if (numRows <= 0 || numCols <= 0) {
            Toast.makeText(this, getString(R.string.error_invalid_dimensions), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Инициализация массивов для хранения EditText
        costsMatrix = Array(numRows) { Array(numCols) { EditText(this) } }
        suppliesInputs = Array(numRows) { EditText(this) }
        demandsInputs = Array(numCols) { EditText(this) }

        createTable(numRows, numCols)
        setupSpinners()

        btnSolve.setOnClickListener {
            if (validateInput()) {
                try {
                    val problem = createProblem()

                    if (!problem.isBalanced()) {
                        Toast.makeText(this, getString(R.string.error_unbalanced), Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }

                    val intent = Intent(this, SolutionActivity::class.java).apply {
                        putExtra("problem", problem)
                        putExtra("methodType", spinnerMethod.selectedItem.toString())
                        putExtra("objectiveType", spinnerObjective.selectedItem.toString())
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.error_data, e.message), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.error_invalid_input), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpinners() {
        // Настройка спиннера для метода решения
        ArrayAdapter.createFromResource(
            this,
            R.array.methods,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerMethod.adapter = adapter
        }

        // Настройка спиннера для цели оптимизации
        ArrayAdapter.createFromResource(
            this,
            R.array.objectives,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerObjective.adapter = adapter
        }
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

    private fun createProblem(): TransportationProblem {
        val costs = Array(costsMatrix.size) { i ->
            DoubleArray(costsMatrix[i].size) { j ->
                costsMatrix[i][j].text.toString().toDouble()
            }
        }

        val supplies = DoubleArray(suppliesInputs.size) { i ->
            suppliesInputs[i].text.toString().toDouble()
        }

        val demands = DoubleArray(demandsInputs.size) { i ->
            demandsInputs[i].text.toString().toDouble()
        }

        return TransportationProblem(costs, supplies, demands)
    }
}