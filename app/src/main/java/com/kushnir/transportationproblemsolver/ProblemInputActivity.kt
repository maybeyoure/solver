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

        tableLayout = findViewById(R.id.table_costs)
        btnSolve = findViewById(R.id.btn_solve)
        spinnerMethod = findViewById(R.id.spinner_method)
        spinnerObjective = findViewById(R.id.spinner_objective)

        val numRows = intent.getIntExtra("numRows", 0)
        val numCols = intent.getIntExtra("numCols", 0)

        costsMatrix = Array(numRows) { Array(numCols) { EditText(this) } }
        suppliesInputs = Array(numRows) { EditText(this) }
        demandsInputs = Array(numCols) { EditText(this) }
        val methods = listOf("Метод двойного предпочтения", "Метод Фогеля")
        val objectives = listOf("Минимальные затраты", "Максимальная прибыль")

        val methodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, methods)
        val objectiveAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, objectives)

        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        objectiveAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerMethod.adapter = methodAdapter
        spinnerObjective.adapter = objectiveAdapter

        spinnerMethod.prompt = getString(R.string.select_method_prompt)
        spinnerObjective.prompt = getString(R.string.select_objective_prompt)

        createTable(numRows, numCols)
        val btnBack: ImageButton = findViewById(R.id.btn_back)
        btnBack.setOnClickListener {
            onBackPressed()
        }

        btnSolve.setOnClickListener {
            if (validateInput()) {
                try {
                    val problem = createProblem()
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