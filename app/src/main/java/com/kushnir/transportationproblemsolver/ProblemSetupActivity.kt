package com.kushnir.transportationproblemsolver

import android.content.Intent
import android.os.Bundle
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

        btnNext.setOnClickListener {
            if (validateInput()) {
                val numRows = spinnerRows.selectedItem.toString().toInt()
                val numCols = spinnerCols.selectedItem.toString().toInt()

                val intent = Intent(this, ProblemInputActivity::class.java).apply {
                    putExtra("numRows", numRows)
                    putExtra("numCols", numCols)
                }
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_select_dimensions),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun validateInput(): Boolean {
        return spinnerRows.selectedItemPosition > 0 &&
                spinnerCols.selectedItemPosition > 0
    }
}