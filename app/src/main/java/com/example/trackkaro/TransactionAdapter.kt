package com.example.trackkaro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private var transactions: List<Transaction> = emptyList(),
    private val onLongClick: (Transaction) -> Unit = {}
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Exposed so MainActivity can read the item being swiped
    fun getItemAt(position: Int): Transaction = transactions[position]

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconCircle: View    = view.findViewById(R.id.iconCircle)
        val tvIcon: ImageView   = view.findViewById(R.id.tvIcon)
        val title: TextView     = view.findViewById(R.id.tvTransactionTitle)
        val date: TextView      = view.findViewById(R.id.tvTransactionDate)
        val amount: TextView    = view.findViewById(R.id.tvTransactionAmount)
        val type: TextView      = view.findViewById(R.id.tvTransactionType)
        val sourceBadge: TextView = view.findViewById(R.id.tvSourceBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tx  = transactions[position]
        val ctx = holder.itemView.context
        val sym = SharedPrefHelper.get(ctx).currencySymbol

        holder.title.text = tx.title
        holder.date.text  = dateFormat.format(Date(tx.date))

        if (tx.type == "Income") {
            holder.amount.text = "+$sym%.2f".format(tx.amount)
            holder.amount.setTextColor(ContextCompat.getColor(ctx, R.color.accent_green))
            holder.type.text       = "Income"
            holder.type.setTextColor(ContextCompat.getColor(ctx, R.color.accent_green))
            holder.type.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_completed)
            holder.iconCircle.background = ContextCompat.getDrawable(ctx, R.drawable.bg_icon_circle_green)
            holder.tvIcon.setImageResource(R.drawable.ic_salary)
            holder.tvIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.accent_green))
        } else {
            holder.amount.text = "-$sym%.2f".format(tx.amount)
            holder.amount.setTextColor(ContextCompat.getColor(ctx, R.color.expense_red))
            holder.type.text       = "Expense"
            holder.type.setTextColor(ContextCompat.getColor(ctx, R.color.orange_accent))
            holder.type.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_pending)
            holder.iconCircle.background = ContextCompat.getDrawable(ctx, R.drawable.bg_icon_circle_red)
            holder.tvIcon.setImageResource(getCategoryIcon(tx.category))
            holder.tvIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.expense_red))
        }

        holder.itemView.setOnLongClickListener { onLongClick(tx); true }

        // Source badge
        when (tx.source) {
            "SMS" -> {
                holder.sourceBadge.visibility = View.VISIBLE
                holder.sourceBadge.text = "SMS"
                holder.sourceBadge.setTextColor(ContextCompat.getColor(ctx, R.color.blue_accent))
            }
            "Notification" -> {
                holder.sourceBadge.visibility = View.VISIBLE
                holder.sourceBadge.text = "AUTO"
                holder.sourceBadge.setTextColor(ContextCompat.getColor(ctx, R.color.accent_green))
            }
            else -> holder.sourceBadge.visibility = View.GONE
        }
    }

    private fun getCategoryIcon(category: String): Int = when (category.lowercase()) {
        "food"     -> R.drawable.ic_food
        "travel"   -> R.drawable.ic_travel
        "shopping" -> R.drawable.ic_shopping
        else       -> R.drawable.ic_salary
    }

    override fun getItemCount(): Int = transactions.size

    /** DiffUtil-powered update — animates only changed rows, no full rebind flicker. */
    fun updateList(newList: List<Transaction>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = transactions.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(o: Int, n: Int) = transactions[o].id == newList[n].id
            override fun areContentsTheSame(o: Int, n: Int) = transactions[o] == newList[n]
        })
        transactions = newList
        diff.dispatchUpdatesTo(this)
    }
}
