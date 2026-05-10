package com.example.clatproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.clatproject.database.Classroom

class RoomAdapter(private var rooms: List<Classroom>) : RecyclerView.Adapter<RoomAdapter.ViewHolder>() {

    fun submitList(newList: List<Classroom>) {
        rooms = newList
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(android.R.id.text1)
        val text2: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val room = rooms[position]
        holder.text1.text = "Room ${room.roomNo}"
        holder.text2.text = "Building: ${room.building} | Dept: ${room.dept}"
    }

    override fun getItemCount() = rooms.size
}