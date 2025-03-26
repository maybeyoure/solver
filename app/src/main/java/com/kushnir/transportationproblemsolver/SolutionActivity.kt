package com.kushnir.transportationproblemsolver
import com.kushnir.transportationproblemsolver.SolutionStep
import android.content.Intent
import android.os.Bundle
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.util.Log
import android.widget.HorizontalScrollView
import android.widget.Toast
import android.graphics.Color
import kotlin.math.abs
import com.kushnir.transportationproblemsolver.solvers.DoublePreferenceSolver
import com.kushnir.transportationproblemsolver.solvers.FogelSolver

class SolutionActivity : AppCompatActivity() {
    private lateinit var balanceConditionText: TextView
    private lateinit var solutionStepsContainer: LinearLayout
    private lateinit var resultTextView: TextView
    private var isVogelMethod = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_solution)

        try {
            balanceConditionText = findViewById(R.id.balance_condition_text)
            solutionStepsContainer = findViewById(R.id.solution_steps_container)
            resultTextView = findViewById(R.id.result_text_view)
            val methodTitleText = findViewById<TextView>(R.id.method_title_text)

            val problem = intent.getSerializableExtra("problem") as? TransportationProblem
            if (problem == null) {
                Toast.makeText(this, "Ошибка при получении данных", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            Log.d("SolutionActivity", "Problem received: ${problem.costs.size}x${problem.costs[0].size}")

            // Получаем метод решения, если не передан - берем первый метод из массива
            val methodType = intent.getStringExtra("methodType") ?: resources.getStringArray(R.array.methods)[0]
            isVogelMethod = methodType == resources.getStringArray(R.array.methods)[1]
            methodTitleText.text = getString(R.string.method_title, methodType)

            val objectiveType = intent.getStringExtra("objectiveType") ?: resources.getStringArray(R.array.objectives)[0]
            displayBalanceCheck(problem)
            val solutionSteps = problem.solveWithSteps(this, methodType)

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

        if (abs(totalSupply - totalDemand) > 0.0001) {
            balanceConditionText.text = "$balanceText\nЗадача является открытой."
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
        Log.d("SolutionActivity", "Original dimensions: ${problem.costs.size}x${problem.costs[0].size}")
        Log.d("SolutionActivity", "Balanced dimensions: ${balancedProblem.costs.size}x${balancedProblem.costs[0].size}")

        solutionSteps.forEachIndexed { index, step ->
            try {
                // Проверяем индексы относительно балансированной задачи
                if (step.selectedRow < 0 || step.selectedRow >= balancedProblem.costs.size ||
                    step.selectedCol < 0 || step.selectedCol >= balancedProblem.costs[0].size
                ) {
                    if (step.isFictive) {
                        // Это нормально для шага с добавлением фиктивного элемента
                    } else {
                        Log.e("SolutionActivity", "Invalid indices: row=${step.selectedRow}, col=${step.selectedCol}")
                        return@forEachIndexed
                    }
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

                // Добавляем описание шага
                val stepDescription = TextView(this).apply {
                    text = step.description
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(0, 8, 0, 8)
                }
                stepContainer.addView(stepDescription)

                // Создаем горизонтальную прокрутку для таблицы
                val horizontalScrollView = HorizontalScrollView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    isHorizontalScrollBarEnabled = true
                    scrollBarSize = 10
                }

                // Создаем и добавляем матрицу в горизонтальную прокрутку
                if (step.isVogel && step.rowPenalties != null && step.colPenalties != null && !step.isFictive) {
                    // Для метода Фогеля создаем матрицу со штрафами
                    val matrixTable = createMatrixTableWithPenalties(
                        step.currentSolution,
                        balancedProblem,
                        step.rowPenalties,
                        step.colPenalties
                    )
                    horizontalScrollView.addView(matrixTable)
                } else {
                    // Для других методов используем обычную матрицу
                    val matrixTable = createMatrixTable(step.currentSolution, balancedProblem)
                    horizontalScrollView.addView(matrixTable)
                }
                stepContainer.addView(horizontalScrollView)

                solutionStepsContainer.addView(stepContainer)
            } catch (e: Exception) {
                Log.e("SolutionActivity", "Error processing step $index", e)
            }
        }

        try {
            if (solutionSteps.isNotEmpty()) {
                val totalCost = calculateTotalCost(solutionSteps.last().currentSolution, balancedProblem)
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
                TableLayout.LayoutParams.WRAP_CONTENT,
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
                    minWidth = 60
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
                        minWidth = 80
                        setPadding(8, 8, 8, 8)
                    })

                    // Значения решения
                    for (j in solution[i].indices) {
                        addView(TextView(context).apply {
                            text = if (solution[i][j] > 0) {
                                String.format("%.0f", solution[i][j])
                            } else {
                                "-"
                            }
                            gravity = Gravity.CENTER
                            minWidth = 80
                            setPadding(8, 8, 8, 8)

                            // Добавить подсветку фиктивных путей
                            if ((i >= problem.costs.size || j >= problem.costs[0].size) && solution[i][j] > 0) {
                                setBackgroundColor(Color.parseColor("#FFECB3"))
                            }
                        })
                    }

                    // Значения запасов
                    addView(TextView(context).apply {
                        text = String.format("%.0f", problem.supplies[i])
                        gravity = Gravity.CENTER
                        minWidth = 80
                        setPadding(8, 8, 8, 8)
                    })
                })
            }

            // Добавляем строку потребностей
            addView(TableRow(context).apply {
                addView(TextView(context).apply {
                    text = getString(R.string.header_demands)
                    gravity = Gravity.CENTER
                    minWidth = 80
                    setPadding(8, 8, 8, 8)
                })
                for (j in solution[0].indices) {
                    addView(TextView(context).apply {
                        text = String.format("%.0f", problem.demands[j])
                        gravity = Gravity.CENTER
                        minWidth = 80
                        setPadding(8, 8, 8, 8)
                    })
                }
                addView(TextView(context).apply {
                    text = ""
                    minWidth = 80
                })
            })
        }
    }
    private fun createMatrixTableWithPenalties(
        solution: Array<DoubleArray>,
        problem: TransportationProblem,
        rowPenalties: DoubleArray,
        colPenalties: DoubleArray
    ): TableLayout {
        return TableLayout(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,  // Изменено с MATCH_PARENT на WRAP_CONTENT
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundResource(android.R.color.background_light)
            setPadding(16, 16, 16, 16)

            // Добавляем заголовки столбцов
            addView(TableRow(context).apply {
                addView(TextView(context).apply {
                    text = ""
                    minWidth = 60
                    setPadding(8, 8, 8, 8)
                })
                for (j in solution[0].indices) {
                    addView(TextView(context).apply {
                        text = getString(R.string.header_store, j + 1)
                        gravity = Gravity.CENTER
                        minWidth = 80
                        setPadding(8, 8, 8, 8)
                    })
                }
                addView(TextView(context).apply {
                    text = getString(R.string.header_supplies)
                    gravity = Gravity.CENTER
                    minWidth = 80
                    setPadding(8, 8, 8, 8)
                })
                // Добавляем заголовок для штрафов строк
                addView(TextView(context).apply {
                    text = getString(R.string.header_penalties)
                    gravity = Gravity.CENTER
                    minWidth = 100
                    setPadding(8, 8, 8, 8)
                    setBackgroundColor(Color.parseColor("#E6E6FA")) // Светло-лавандовый цвет для выделения
                })
            })

            // Добавляем строки с данными
            for (i in solution.indices) {
                addView(TableRow(context).apply {
                    // Заголовок строки
                    addView(TextView(context).apply {
                        text = getString(R.string.header_supplier, i + 1)
                        gravity = Gravity.CENTER
                        minWidth = 60
                        setPadding(8, 8, 8, 8)
                    })

                    // Значения решения
                    for (j in solution[i].indices) {
                        addView(TextView(context).apply {
                            text = if (solution[i][j] > 0) {
                                String.format("%.0f", solution[i][j])
                            } else {
                                "-"
                            }
                            gravity = Gravity.CENTER
                            minWidth = 80
                            setPadding(8, 8, 8, 8)

                            // Если ячейка фиктивная, выделим её другим цветом
                            if ((i >= problem.costs.size || j >= problem.costs[0].size) && solution[i][j] > 0) {
                                setBackgroundColor(Color.parseColor("#FFECB3")) // Светло-жёлтый для фиктивных путей
                            }
                        })
                    }

                    // Значения запасов
                    addView(TextView(context).apply {
                        text = String.format("%.0f", problem.supplies[i])
                        gravity = Gravity.CENTER
                        minWidth = 80
                        setPadding(8, 8, 8, 8)
                    })

                    // Штрафы для строк
                    addView(TextView(context).apply {
                        val penalty = rowPenalties[i]
                        text = if (penalty.isFinite() && penalty >= 0) {
                            String.format("%.0f", penalty)
                        } else {
                            "-"
                        }
                        gravity = Gravity.CENTER
                        minWidth = 100
                        setPadding(8, 8, 8, 8)
                        setBackgroundColor(Color.parseColor("#E6E6FA")) // Светло-лавандовый цвет для выделения
                    })
                })
            }

            // Добавляем строку потребностей
            addView(TableRow(context).apply {
                addView(TextView(context).apply {
                    text = getString(R.string.header_demands)
                    gravity = Gravity.CENTER
                    minWidth = 60
                    setPadding(8, 8, 8, 8)
                })
                for (j in solution[0].indices) {
                    addView(TextView(context).apply {
                        text = String.format("%.0f", problem.demands[j])
                        gravity = Gravity.CENTER
                        minWidth = 80
                        setPadding(8, 8, 8, 8)
                    })
                }
                addView(TextView(context).apply {
                    text = ""
                    minWidth = 80
                })
                addView(TextView(context).apply {
                    text = ""
                    minWidth = 100
                })
            })

            // Добавляем строку штрафов столбцов
            addView(TableRow(context).apply {
                addView(TextView(context).apply {
                    text = getString(R.string.header_penalties)
                    gravity = Gravity.CENTER
                    minWidth = 60
                    setPadding(8, 8, 8, 8)
                    setBackgroundColor(Color.parseColor("#E6E6FA")) // Светло-лавандовый цвет для выделения
                })
                for (j in solution[0].indices) {
                    addView(TextView(context).apply {
                        val penalty = colPenalties[j]
                        text = if (penalty.isFinite() && penalty >= 0) {
                            String.format("%.0f", penalty)
                        } else {
                            "-"
                        }
                        gravity = Gravity.CENTER
                        minWidth = 80
                        setPadding(8, 8, 8, 8)
                        setBackgroundColor(Color.parseColor("#E6E6FA")) // Светло-лавандовый цвет для выделения
                    })
                }
                addView(TextView(context).apply {
                    text = ""
                    minWidth = 80
                })
                addView(TextView(context).apply {
                    text = ""
                    minWidth = 100
                })
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
                if (i < problem.costs.size && j < problem.costs[0].size) {
                    totalCost += solution[i][j] * problem.costs[i][j]
                }
            }
        }
        return totalCost
    }
}