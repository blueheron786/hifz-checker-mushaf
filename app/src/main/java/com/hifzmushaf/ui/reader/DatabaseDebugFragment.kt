package com.hifzmushaf.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hifzmushaf.databinding.FragmentDatabaseDebugBinding
import kotlinx.coroutines.launch

class DatabaseDebugFragment : Fragment() {
    
    private var _binding: FragmentDatabaseDebugBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDatabaseDebugBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.analyzeButton.setOnClickListener {
            analyzeDatabase()
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }
    
    private fun analyzeDatabase() {
        binding.progressBar.visibility = View.VISIBLE
        binding.analyzeButton.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val analyzer = DatabaseSchemaAnalyzer(requireContext())
                val result = analyzer.analyzeDatabase()
                
                displayResults(result)
                
            } catch (e: Exception) {
                binding.resultText.text = "Error: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.analyzeButton.isEnabled = true
            }
        }
    }
    
    private fun displayResults(result: DatabaseAnalysisResult) {
        if (result.error != null) {
            binding.resultText.text = "Error: ${result.error}"
            return
        }
        
        val adapter = DatabaseTableAdapter(result.tableInfos.values.toList())
        binding.recyclerView.adapter = adapter
        
        val summary = buildString {
            appendLine("Database Analysis Results")
            appendLine("========================")
            appendLine("Tables found: ${result.tables.size}")
            appendLine()
            
            for (tableInfo in result.tableInfos.values) {
                appendLine("Table: ${tableInfo.name}")
                appendLine("Rows: ${tableInfo.rowCount}")
                appendLine("Columns: ${tableInfo.columns.joinToString(", ") { "${it.name}(${it.type})" }}")
                appendLine()
            }
        }
        
        binding.resultText.text = summary
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DatabaseTableAdapter(private val tables: List<TableInfo>) : 
    RecyclerView.Adapter<DatabaseTableAdapter.TableViewHolder>() {
    
    inner class TableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(android.R.id.text1)
        val detailText: TextView = itemView.findViewById(android.R.id.text2)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TableViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.two_line_list_item, parent, false)
        return TableViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TableViewHolder, position: Int) {
        val table = tables[position]
        
        holder.titleText.text = "${table.name} (${table.rowCount} rows)"
        
        val details = buildString {
            appendLine("Columns: ${table.columns.joinToString(", ") { it.name }}")
            if (table.sampleRows.isNotEmpty()) {
                appendLine("Sample data:")
                table.sampleRows.take(2).forEach { row ->
                    appendLine("  ${row.take(3).joinToString(" | ")}")
                }
            }
        }
        
        holder.detailText.text = details
    }
    
    override fun getItemCount() = tables.size
}
