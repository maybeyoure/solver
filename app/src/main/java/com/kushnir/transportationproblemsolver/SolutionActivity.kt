package com.kushnir.transportationproblemsolver

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SolutionActivity : AppCompatActivity() {
    private lateinit var resultTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_solution)

        resultTextView = findViewById(R.id.result_text_view)

        val problem = intent.getSerializableExtra("problem") as? TransportationProblem
        val methodType = intent.getStringExtra("methodType") ?: "Метод Фогеля"
        val objectiveType = intent.getStringExtra("objectiveType") ?: "Минимальные затраты"

        if (problem == null) {
            finish()
            return
        }

        // Временный вывод данных для проверки
        val text = """
            Метод решения: $methodType
            Тип оптимизации: $objectiveType
            Размерность: ${problem.costs.size}x${problem.costs[0].size}
            Сумма запасов: ${problem.supplies.sum()}
            Сумма потребностей: ${problem.demands.sum()}
        """.trimIndent()

        resultTextView.text = text

        // TODO: Добавить логику решения задачи
    }
}