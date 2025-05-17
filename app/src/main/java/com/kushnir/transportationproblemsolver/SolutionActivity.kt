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
import android.graphics.Typeface
import android.widget.ImageButton
import kotlin.math.abs
import com.kushnir.transportationproblemsolver.solvers.DoublePreferenceSolver
import com.kushnir.transportationproblemsolver.solvers.FogelSolver
import com.kushnir.transportationproblemsolver.optimizers.Potential
import com.kushnir.transportationproblemsolver.optimizers.OptimizationStep

class SolutionActivity : AppCompatActivity() {
    private lateinit var balanceConditionText: TextView
    private lateinit var solutionStepsContainer: LinearLayout
    private var isVogelMethod = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_solution)
        val btnBack = findViewById<ImageButton>(R.id.btn_back2)
        btnBack.setOnClickListener {
            onBackPressed()
        }

        try {
            balanceConditionText = findViewById(R.id.balance_condition_text)
            solutionStepsContainer = findViewById(R.id.solution_steps_container)
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

            val optimizationSteps = problem.optimizeSolutionWithSteps(this, methodType, objectiveType)

            Log.d("SolutionActivity", "Optimization steps count: ${optimizationSteps.size}")

            displayOptimizationSteps(optimizationSteps, problem)

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
                        step.colPenalties,
                        step // Передаем шаг с информацией о базисных нулях
                    )
                    horizontalScrollView.addView(matrixTable)
                } else {
                    // Для других методов используем обычную матрицу с предпочтениями
                    val matrixTable = createMatrixTable(step.currentSolution, balancedProblem, step)
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
            }
        } catch (e: Exception) {
            Log.e("SolutionActivity", "Error calculating total cost", e)
        }
    }

    private fun createMatrixTable(
        solution: Array<DoubleArray>,
        problem: TransportationProblem,
        step: SolutionStep
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

            for (i in solution.indices) {
                addView(TableRow(context).apply {
                    addView(TextView(context).apply {
                        text = getString(R.string.header_supplier, i + 1)
                        gravity = Gravity.CENTER
                        minWidth = 80
                        setPadding(8, 8, 8, 8)
                    })

                    for (j in solution[i].indices) {
                        addView(TextView(context).apply {
                            val cellText = StringBuilder()

                            // Проверяем, является ли эта клетка базисным нулем - используем список из SolutionStep
                            val isCurrentCellBasicZero = step.basicZeroCells?.any { it.first == i && it.second == j } == true

                            // Отображаем базисный нуль как "0"
                            if (isCurrentCellBasicZero) {
                                cellText.append("0")
                            }
                            // Обычное положительное значение
                            else if (solution[i][j] > 0) {
                                cellText.append(String.format("%.0f", solution[i][j]))
                            }
                            // Пустая клетка
                            else {
                                cellText.append("-")
                            }

                            // Добавляем обозначение предпочтения, если есть
                            if (step.doublePreferenceCells?.contains(Pair(i, j)) == true) {
                                cellText.append("[VV]") // Двойное предпочтение
                            } else if (step.singlePreferenceCells?.contains(Pair(i, j)) == true) {
                                cellText.append("[V]") // Одинарное предпочтение
                            }

                            text = cellText.toString()
                            gravity = Gravity.CENTER
                            minWidth = 80
                            setPadding(8, 8, 8, 8)

                            if ((i >= problem.costs.size || j >= problem.costs[0].size) &&
                                (solution[i][j] > 0 || isCurrentCellBasicZero)) {
                                setBackgroundColor(Color.parseColor("#FFECB3"))
                            }
                        })
                    }

                    addView(TextView(context).apply {
                        text = if (i < problem.supplies.size) {
                            String.format("%.0f", problem.supplies[i])
                        } else {
                            String.format("%.0f", 0.0)
                        }
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
                        text = if (j < problem.demands.size) {
                            String.format("%.0f", problem.demands[j])
                        } else {
                            String.format("%.0f", 0.0)
                        }
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
        colPenalties: DoubleArray,
        step: SolutionStep // Добавлен параметр step для получения информации о базисных нулях
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
                            // Проверяем, является ли эта клетка базисным нулем
                            val isCurrentCellBasicZero = step.basicZeroCells?.any { it.first == i && it.second == j } == true

                            // Выбираем текст для отображения
                            text = if (isCurrentCellBasicZero) {
                                "0" // Отображаем базисный нуль как "0"
                            } else if (solution[i][j] > 0) {
                                String.format("%.0f", solution[i][j]) // Положительное значение
                            } else {
                                "-" // Пустая клетка
                            }

                            gravity = Gravity.CENTER
                            minWidth = 80
                            setPadding(8, 8, 8, 8)

                            // Если ячейка фиктивная, выделим её другим цветом
                            if ((i >= problem.costs.size || j >= problem.costs[0].size) &&
                                (solution[i][j] > 0 || isCurrentCellBasicZero)) {
                                setBackgroundColor(Color.parseColor("#FFECB3")) // Светло-жёлтый для фиктивных путей
                            }
                        })
                    }

                    // Значения запасов
                    addView(TextView(context).apply {
                        text = if (i < problem.supplies.size) {
                            String.format("%.0f", problem.supplies[i])
                        } else {
                            // For dummy/balanced rows
                            String.format("%.0f", 0.0)
                        }
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
                        text = if (j < problem.demands.size) {
                            String.format("%.0f", problem.demands[j])
                        } else {
                            String.format("%.0f", 0.0)
                        }
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
    private fun displayOptimizationSteps(optimizationSteps: List<OptimizationStep>, problem: TransportationProblem) {
        // Добавляем заголовок секции оптимизации
        val optimizationHeader = TextView(this).apply {
            text = getString(R.string.optimization_header)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 24, 0, 16)
            setTypeface(null, Typeface.BOLD)
        }
        solutionStepsContainer.addView(optimizationHeader)
        val firstStep = optimizationSteps.firstOrNull() ?: return
        val solutionMatrix = firstStep.currentSolution
        val basicZeroCells = firstStep.basicZeroCells ?: emptyList()

        var occupiedCells = 0
        for (i in solutionMatrix.indices) {
            for (j in solutionMatrix[i].indices) {
                if (solutionMatrix[i][j] > 0 || basicZeroCells.contains(Pair(i, j))) {
                    occupiedCells++
                }
            }
        }
        val rows = solutionMatrix.size
        val cols = solutionMatrix[0].size
        val requiredCells = rows + cols - 1
        val isDegeneratePlan = occupiedCells < requiredCells

        val degeneracyInfo = TextView(this).apply {
            text = "Число занятых клеток должно быть равно m + n - 1. " +
                    "$rows+$cols-1 = $requiredCells. Занятых клеток получилось $occupiedCells. " +
                    "Следовательно, опорный план ${if (isDegeneratePlan) "является" else "не является"} вырожденным."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 8, 0, 8)
        }
        solutionStepsContainer.addView(degeneracyInfo)

        // Рассчитываем значение целевой функции
        var totalCost = 0.0
        for (i in solutionMatrix.indices) {
            for (j in solutionMatrix[i].indices) {
                if (i < problem.costs.size && j < problem.costs[0].size) {
                    // Учитываем положительные значения
                    if (solutionMatrix[i][j] > 0) {
                        totalCost += solutionMatrix[i][j] * problem.costs[i][j]
                    }
                    else if (basicZeroCells.contains(Pair(i, j))) {
                    }
                }
            }
        }

        // Добавляем информацию о целевой функции
        val functionValueInfo = TextView(this).apply {
            text = "Значение целевой функции для этого опорного плана равно:\n" +
                    "F(x) = " + buildFunctionFormula(solutionMatrix, problem.costs) + " = " +
                    String.format("%.0f", totalCost)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 8, 0, 16)
        }
        solutionStepsContainer.addView(functionValueInfo)

        // Проверяем, есть ли шаги для отображения
        if (optimizationSteps.isEmpty()) {
            val noStepsText = TextView(this).apply {
                text = getString(R.string.optimization_no_steps)
                setPadding(0, 8, 0, 8)
            }
            solutionStepsContainer.addView(noStepsText)
            return
        }

        // Отображаем каждый шаг оптимизации
        optimizationSteps.forEach { step ->
            try {
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

                // Выбираем тип таблицы в зависимости от содержимого шага
                val tableLayout = when {
                    // Если есть оценки и потенциалы, показываем матрицу с ними
                    step.evaluations != null && step.potentialsU != null && step.potentialsV != null -> {
                        createMatrixTableWithEvaluations(
                            step.currentSolution,
                            problem,
                            step.potentialsU,
                            step.potentialsV,
                            step.evaluations
                        )
                    }
                    step.cyclePoints != null && step.cyclePoints.isNotEmpty() -> {
                        createMatrixTableWithCycle(
                            step.currentSolution,
                            problem,
                            step.cyclePoints
                        )
                    }
                    else -> {
                        // Здесь нужно передать фиктивный SolutionStep, так как в новой версии createMatrixTable
                        // ожидает этот параметр, но для шагов оптимизации у нас нет информации о предпочтениях
                        val dummyStep = SolutionStep(
                            stepNumber = 0,
                            description = "",
                            selectedRow = -1,
                            selectedCol = -1,
                            quantity = 0.0,
                            currentSolution = step.currentSolution,
                            remainingSupplies = DoubleArray(0),
                            remainingDemands = DoubleArray(0),
                            doublePreferenceCells = null,
                            singlePreferenceCells = null,
                            basicZeroCells = null
                        )
                        createMatrixTable(step.currentSolution, problem, dummyStep)
                    }
                }

                horizontalScrollView.addView(tableLayout)
                stepContainer.addView(horizontalScrollView)
                solutionStepsContainer.addView(stepContainer)
            } catch (e: Exception) {
                Log.e("SolutionActivity", "Error displaying optimization step: ${e.message}")
                val errorText = TextView(this).apply {
                    text = "Ошибка при отображении шага: ${e.message}"
                    setTextColor(Color.RED)
                }
                solutionStepsContainer.addView(errorText)
            }
        }
    }

    private fun createMatrixTableWithEvaluations(
        solution: Array<DoubleArray>,
        problem: TransportationProblem,
        potentialsU: DoubleArray,
        potentialsV: DoubleArray,
        evaluations: Array<DoubleArray>
    ): TableLayout {
        return TableLayout(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundResource(android.R.color.background_light)
            setPadding(16, 16, 16, 16)

            // Добавляем заголовок с потенциалами V
            addView(TableRow(context).apply {
                // Первая ячейка - U/V
                addView(TextView(context).apply {
                    text = "U\\V"
                    gravity = Gravity.CENTER
                    setPadding(8, 8, 8, 8)
                })

                for (j in solution[0].indices) {
                    addView(TextView(context).apply {
                        text = String.format("%.0f",
                            if (j < potentialsV.size) potentialsV[j] else 0.0)
                        gravity = Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                        setBackgroundColor(Color.LTGRAY)
                    })
                }

                // Последняя ячейка - пусто
                addView(TextView(context).apply {
                    text = ""
                    setPadding(8, 8, 8, 8)
                })
            })

            // Добавляем строки с данными и оценками
            for (i in solution.indices) {
                addView(TableRow(context).apply {
                    addView(TextView(context).apply {
                        text = String.format("%.0f",
                            if (i < potentialsU.size) potentialsU[i] else 0.0)
                        gravity = Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                        setBackgroundColor(Color.LTGRAY)
                    })

                    // Значения поставок и оценок
                    for (j in solution[i].indices) {
                        val cellLayout = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(8, 8, 8, 8)

                            // Если клетка базисная, показываем только поставку
                            if (solution[i][j] > 0) {
                                addView(TextView(context).apply {
                                    text = String.format("%.0f", solution[i][j])
                                    gravity = Gravity.CENTER
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                                })
                            }
                            // Если клетка свободная, показываем поставку и оценку
                            else {
                                addView(TextView(context).apply {
                                    text = "-"
                                    gravity = Gravity.CENTER
                                })

                                addView(TextView(context).apply {
                                    text = String.format("%.0f",
                                        if (i < evaluations.size && j < evaluations[i].size) evaluations[i][j] else 0.0)
                                    gravity = Gravity.CENTER
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                    setTextColor(
                                        if (i < evaluations.size && j < evaluations[i].size && evaluations[i][j] < 0) Color.RED
                                        else if (i < evaluations.size && j < evaluations[i].size && evaluations[i][j] > 0) Color.BLUE
                                        else Color.BLACK
                                    )
                                })
                            }
                        }
                        addView(cellLayout)
                    }

                    // Значения запасов
                    addView(TextView(context).apply {
                        text = String.format("%.0f", problem.supplies[i])
                        gravity = Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                    })
                })
            }

            // Добавляем строку потребностей
            addView(TableRow(context).apply {
                addView(TextView(context).apply {
                    text = ""
                    setPadding(8, 8, 8, 8)
                })

                for (j in solution[0].indices) {
                    addView(TextView(context).apply {
                        text = String.format("%.0f", problem.demands[j])
                        gravity = Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                    })
                }

                addView(TextView(context).apply {
                    text = ""
                    setPadding(8, 8, 8, 8)
                })
            })
        }
    }

    fun createMatrixTableWithCycle(
        solution: Array<DoubleArray>,
        problem: TransportationProblem,
        cycle: List<Pair<Int, Int>>
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

                    // Значения поставок
                    for (j in solution[i].indices) {
                        val cell = Pair(i, j)
                        val inCycle = cycle.any { it.first == i && it.second == j }
                        val isPlus = cycle.indexOfFirst { it.first == i && it.second == j } % 2 == 0

                        addView(TextView(context).apply {
                            text = if (solution[i][j] > 0) {
                                String.format("%.0f", solution[i][j])
                            } else {
                                "-"
                            }
                            gravity = Gravity.CENTER
                            setPadding(8, 8, 8, 8)

                            // Выделяем цветом клетки цикла
                            if (inCycle) {
                                setBackgroundColor(
                                    if (isPlus) Color.parseColor("#90EE90") // Светло-зеленый для +
                                    else Color.parseColor("#FFB6C1") // Светло-розовый для -
                                )
                            }
                        })
                    }

                    // Значения запасов
                    addView(TextView(context).apply {
                        text = String.format("%.0f", problem.supplies[i])
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
                        text = String.format("%.0f", problem.demands[j])
                        gravity = Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                    })
                }

                addView(TextView(context).apply {
                    text = ""
                    setPadding(8, 8, 8, 8)
                })
            })
        }
    }

    private fun calculateTotalCost(
        solution: Array<DoubleArray>,
        problem: TransportationProblem,
        basicZeroCells: List<Pair<Int, Int>>? = null
    ): Double {
        var totalCost = 0.0
        for (i in solution.indices) {
            for (j in solution[i].indices) {
                if (i < problem.costs.size && j < problem.costs[0].size) {
                    totalCost += solution[i][j] * problem.costs[i][j]
                }
                else if (basicZeroCells?.contains(Pair(i, j)) == true) {
                }
            }
        }
        return totalCost
    }
    private fun buildFunctionFormula(
        solution: Array<DoubleArray>,
        costs: Array<DoubleArray>,
        basicZeroCells: List<Pair<Int, Int>>? = null
    ): String {
        val terms = mutableListOf<String>()

        for (i in solution.indices) {
            for (j in solution[i].indices) {
                if (i < costs.size && j < costs[0].size) {
                    if (solution[i][j] > 0) {
                        terms.add("${costs[i][j].toInt()}*${solution[i][j].toInt()}")
                    } else if (basicZeroCells?.contains(Pair(i, j)) == true) {
                        terms.add("${costs[i][j].toInt()}*0")
                    }
                } else {
                    if (solution[i][j] > 0) {
                        terms.add("0*${solution[i][j].toInt()}")
                    } else if (basicZeroCells?.contains(Pair(i, j)) == true) {
                        terms.add("0*0")
                    }
                }
            }
        }

        return terms.joinToString(" + ")
    }
}