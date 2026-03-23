package com.example.trackkaro

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

class AllTransactionsActivity : AppCompatActivity() {

    private val viewModel: TransactionViewModel by viewModels()
    private lateinit var adapter: TransactionAdapter
    private lateinit var rvAll: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvCount: TextView
    private lateinit var etSearch: EditText

    // Full list kept for search filtering
    private var fullList: List<Transaction> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_transactions)

        rvAll      = findViewById(R.id.rvAllTransactions)
        emptyState = findViewById(R.id.emptyState)
        tvCount    = findViewById(R.id.tvTransactionCount)
        etSearch   = findViewById(R.id.etSearch)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupRecyclerView()
        setupSwipeToDelete()
        setupSearch()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(emptyList())
        rvAll.layoutManager = LinearLayoutManager(this)
        rvAll.adapter = adapter
    }

    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position    = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_ID.toInt()) return
                val transaction = adapter.getItemAt(position)

                // Delete immediately — LiveData will remove it from the list
                viewModel.delete(transaction)

                // Snackbar with Undo
                Snackbar.make(rvAll, "\"${transaction.title}\" deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") { viewModel.insert(transaction) }
                    .setActionTextColor(ContextCompat.getColor(this@AllTransactionsActivity, R.color.accent_green))
                    .show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvAll)
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterList(query: String) {
        val filtered = if (query.isBlank()) fullList
        else fullList.filter { tx ->
            tx.title.contains(query, ignoreCase = true) ||
            tx.category.contains(query, ignoreCase = true) ||
            tx.type.contains(query, ignoreCase = true)
        }
        adapter.updateList(filtered)
        updateEmptyState(filtered.isEmpty())
        tvCount.text = "${filtered.size} item${if (filtered.size != 1) "s" else ""}"
    }

    private fun observeData() {
        viewModel.allTransactions.observe(this) { list ->
            fullList = list ?: emptyList()
            // Re-apply current search filter
            filterList(etSearch.text?.toString() ?: "")
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvAll.visibility      = if (isEmpty) View.GONE    else View.VISIBLE
    }
}
