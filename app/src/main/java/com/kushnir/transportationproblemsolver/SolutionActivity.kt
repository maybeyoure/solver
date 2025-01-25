package com.kushnir.transportationproblemsolver

import android.os.Bundle
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.util.Log
import android.widget.Toast

class SolutionActivity : AppCompatActivity() {
    private lateinit var balanceConditionText: TextView
    private lateinit var solutionStepsContainer: LinearLayout
    private lateinit var resultTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_solution)

        try {
            balanceConditionText = findViewById(R.id.balance_condition_text)
            solutionStepsContainer = findViewById(R.id.solution_steps_container)
            resultTextView = findViewById(R.id.result_text_view)

            val problem = intent.getSerializableExtra("problem") as? TransportationProblem
            if (problem == null) {
                Toast.makeText(this, "Ошибка при получении данных", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            Log.d("SolutionActivity", "Problem received: ${problem.costs.size}x${problem.costs[0].size}")

            // Проверка баланса
            displayBalanceCheck(problem)

            // Пошаговое решение
            val solutionSteps = problem.solveByDoublePreferenceWithSteps()
            displaySolutionSteps(solutionSteps, problem)

        } catch (e: Exception) {
            Log.e("SolutionActivity", "Error in onCreate", e)
            Toast.makeText(this, "Произошла ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun displayBalanceCheck(problem: TransportationProblem) {
        val totalSupply = problem.supplies.sum()
        val totalDemand = problem.demands.sum()

        val balanceText = getString(R.string.balance_check, totalSupply, totalDemand)

        if (kotlin.math.abs(totalSupply - totalDemand) > 0.0001) {
            // Задача несбалансированная
            val diff = kotlin.math.abs(totalSupply - totalDemand)
            val additionalText = if (totalSupply < totalDemand) {
                "Добавляем фиктивного поставщика П${problem.costs.size + 1} с запасом $diff"
            } else {
                "Добавляем фиктивный магазин M${problem.costs[0].size + 1} с потребностью $diff"
            }
            balanceConditionText.text = "$balanceText\nЗадача является открытой.\n$additionalText"
        } else {
            balanceConditionText.text = "$balanceText\nЗадача является закрытой."
        }
    }

    private fun displaySolutionSteps(
        solutionSteps: List<SolutionStep>,
        problem: TransportationProblem
    ) {
        val balancedProblem = problem.makeBalanced()

        Log.d("SolutionActivity", "Steps count: ${solutionSteps.size}")
        Log.d(
            "SolutionActivity",
            "Original dimensions: ${problem.costs.size}x${problem.costs[0].size}"
        )
        Log.d(
            "SolutionActivity",
            "Balanced dimensions: ${balancedProblem.costs.size}x${balancedProblem.costs[0].size}"
        )

        solutionSteps.forEachIndexed { index, step ->
            try {
                // Проверяем индексы относительно балансированной задачи
                if (step.selectedRow < 0 || step.selectedRow >= balancedProblem.costs.size ||
                    step.selectedCol < 0 || step.selectedCol >= balancedProblem.costs[0].size
                ) {
                    Log.e(
                        "SolutionActivity",
                        "Invalid indices: row=${step.selectedRow}, col=${step.selectedCol}"
                    )
                    return@forEachIndexed
                }


                // Создаем контейнер для шага
                val stepContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 16, 0, 16)
                    }
                }

                // Добавляем описание шага с безопасным обращением к costs
                val costValue = balancedProblem.costs[step.selectedRow][step.selectedCol]
                val stepDescription = TextView(this).apply {
                    text = getString(
                        R.string.step_description,
                        index + 1,
                        step.selectedRow + 1,
                        step.selectedCol + 1,
                        costValue
                    )
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(0, 8, 0, 8)
                }
                stepContainer.addView(stepDescription)

                val matrixTable = createMatrixTable(step.currentSolution, balancedProblem)
                stepContainer.addView(matrixTable)

                // Добавляем описание распределения
                val distributionDescription = TextView(this).apply {
                    text = getString(
                        R.string.distribution_description,
                        step.selectedRow + 1,
                        step.selectedCol + 1,
                        step.remainingSupplies[step.selectedRow],
                        step.remainingDemands[step.selectedCol],
                        step.quantity
                    )
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(0, 8, 0, 8)
                }
                stepContainer.addView(distributionDescription)

                solutionStepsContainer.addView(stepContainer)
            } catch (e: Exception) {
                Log.e("SolutionActivity", "Error processing step $index", e)
            }
        }

        try {
            if (solutionSteps.isNotEmpty()) {
                val totalCost =
                    calculateTotalCost(solutionSteps.last().currentSolution, balancedProblem)
                resultTextView.text = getString(R.string.total_cost, totalCost)
            }
        } catch (e: Exception) {
            Log.e("SolutionActivity", "Error calculating total cost", e)
        }
    }

    private fun createMatrixTable(
        solution: Array<DoubleArray>,
        problem: TransportationProblem
    ): TableLayout {
        return TableLayout(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundResource(android.R.color.background_light)
            setPadding(16, 16, 16, 16)

            // Добавляем заголовки столбцов
            addView(TableRow(context).apply {
                addView(TextView(context).apply { text = "" })
                for (j in solution[0].indices) {
                    addView(TextView(context).apply {
                        text = getString(R.string.header_store, j + 1)
                        gravity = Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                    })
                }
                addView(TextView(context).apply {
                    text = getString(R.string.header_supplies)
                    gravity = Gravity.CENTER
                    setPadding(8, 8, 8, 8)
                })
            })

            // Добавляем строки с данными
            for (i in solution.indices) {
                addView(TableRow(context).apply {
                    // Заголовок строки
                    addView(TextView(context).apply {
                        text = getString(R.string.header_supplier, i + 1)
                        gravity = Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                    })

                    // Значения решения
                    for (j in solution[i].indices) {
                        addView(TextView(context).apply {
                            text = if (solution[i][j] > 0) {
                                String.format("%.1f", solution[i][j])
                            } else {
                                "-"
                            }
                            gravity = Gravity.CENTER
                            setPadding(8, 8, 8, 8)
                        })
                    }

                    // Значения запасов
                    addView(TextView(context).apply {
                        text = String.format("%.1f", problem.supplies[i])
                        gravity = Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                    })
                })
            }

            // Добавляем строку потребностей
            addView(TableRow(context).apply {
                addView(TextView(context).apply {
                    text = getString(R.string.header_demands)
                    gravity = Gravity.CENTER
                    setPadding(8, 8, 8, 8)
                })
                for (j in solution[0].indices) {
                    addView(TextView(context).apply {
                        text = String.format("%.1f", problem.demands[j])
                        gravity = Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                    })
                }
                addView(TextView(context).apply { text = "" })
            })
        }
    }

    private fun calculateTotalCost(
        solution: Array<DoubleArray>,
        problem: TransportationProblem
    ): Double {
        var totalCost = 0.0
        for (i in solution.indices) {
            for (j in solution[i].indices) {
                totalCost += solution[i][j] * problem.costs[i][j]
            }
        }
        return totalCost
    }
}
