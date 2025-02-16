package com.kushnir.transportationproblemsolver

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProblemSetupActivity : AppCompatActivity() {
    private lateinit var spinnerRows: Spinner
    private lateinit var spinnerCols: Spinner
    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_problem_setup)

        spinnerRows = findViewById(R.id.spinner_rows)
        spinnerCols = findViewById(R.id.spinner_cols)
        btnNext = findViewById(R.id.btn_next)

        // Настройка адаптеров для спиннеров
        val sizes = (2..10).toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sizes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerRows.adapter = adapter
        spinnerCols.adapter = adapter
        spinnerRows.setSelection(0)
        spinnerCols.setSelection(0)

        spinnerRows.prompt = getString(R.string.select_size_prompt)
        spinnerCols.prompt = getString(R.string.select_size_prompt)

        btnNext.setOnClickListener {
            val numRows = spinnerRows.selectedItem?.toString()?.toInt() ?: 2
            val numCols = spinnerCols.selectedItem?.toString()?.toInt() ?: 2

            val intent = Intent(this, ProblemInputActivity::class.java).apply {
                putExtra("numRows", numRows)
                putExtra("numCols", numCols)
            }
            startActivity(intent)
        }

    }

    private fun validateInput(): Boolean {
        return true  // всегда возвращаем значение по умолчанию 2
    }
}