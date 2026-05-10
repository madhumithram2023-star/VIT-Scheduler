package com.example.clatproject

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class BookHallActivity : AppCompatActivity() {

    private val calendar = Calendar.getInstance()
    private val hallsByCategory = mutableMapOf<String, MutableList<Hall>>()
    private var selectedHall: Hall? = null
    private var currentUserRole: String = "faculty"
    private var currentUserName: String = ""

    data class Hall(val name: String, val roomNo: String, val building: String) {
        override fun toString(): String = "$name ($roomNo, $building)"
    }

    data class HallBooking(
        val hallName: String,
        val roomNo: String,
        val date: String,
        val slot: String,
        val faculty: String,
        val purpose: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_hall)

        val autoCompleteCategory = findViewById<AutoCompleteTextView>(R.id.autoCompleteCategory)
        val autoCompleteHall = findViewById<AutoCompleteTextView>(R.id.autoCompleteHall)
        val etBookingDate = findViewById<EditText>(R.id.etBookingDate)
        val etSlot = findViewById<EditText>(R.id.etSlot)
        val etFaculty = findViewById<EditText>(R.id.etFaculty)
        val etPurpose = findViewById<EditText>(R.id.etPurpose)
        val btnBook = findViewById<Button>(R.id.btnConfirmBooking)
        val btnViewBookings = findViewById<Button>(R.id.btnViewBookings)
        val btnCancelBooking = findViewById<Button>(R.id.btnCancelBooking)
        
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val menuIcon = findViewById<ImageView>(R.id.menuIcon)

        val db = FirebaseFirestore.getInstance()

        fetchUserInfo(etFaculty, navigationView)
        loadAndCategorizeHalls()

        val categories = listOf("Gallery", "Smart Classrooms", "Online Exam Hall", "Vaial Lab", "SBST Lab", "Others")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        autoCompleteCategory.setAdapter(categoryAdapter)

        autoCompleteCategory.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = categories[position]
            val halls = hallsByCategory[selectedCategory] ?: emptyList<Hall>()
            val hallAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, halls)
            autoCompleteHall.setText("")
            selectedHall = null
            autoCompleteHall.setAdapter(hallAdapter)
        }

        autoCompleteHall.setOnItemClickListener { _, _, position, _ ->
            val adapter = autoCompleteHall.adapter as ArrayAdapter<Hall>
            selectedHall = adapter.getItem(position)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        etBookingDate.setText(sdf.format(calendar.time))
        etBookingDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                etBookingDate.setText(sdf.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        menuIcon.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        setupNavigation(drawerLayout, navigationView)

        btnBook.setOnClickListener {
            val hall = selectedHall
            val date = etBookingDate.text.toString().trim()
            val slot = etSlot.text.toString().trim()
            val faculty = etFaculty.text.toString().trim()
            val purpose = etPurpose.text.toString().trim()

            if (hall == null || date.isEmpty() || slot.isEmpty() || faculty.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val requestedSlots = slot.uppercase().split("+").map { it.trim() }

            db.collection("allocations")
                .whereEqualTo("roomNo", hall.roomNo)
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener { result ->
                    var hasConflict = false
                    for (doc in result) {
                        val occupiedSlots = (doc.getString("slot") ?: "").uppercase().split("+").map { it.trim() }
                        if (requestedSlots.any { it in occupiedSlots }) {
                            hasConflict = true
                            break
                        }
                    }

                    if (hasConflict) {
                        Toast.makeText(this, "Conflict! Hall already booked for these slots.", Toast.LENGTH_LONG).show()
                    } else {
                        val data = hashMapOf(
                            "roomNo" to hall.roomNo,
                            "building" to hall.building,
                            "date" to date,
                            "slot" to slot,
                            "faculty" to faculty,
                            "subject" to purpose,
                            "hallName" to hall.name,
                            "isHallBooking" to true
                        )

                        db.collection("allocations").add(data).addOnSuccessListener {
                            Toast.makeText(this, "Hall booked successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
        }

        btnViewBookings.setOnClickListener {
            showHallBookingsDialog()
        }

        btnCancelBooking.setOnClickListener {
            val hall = selectedHall
            val date = etBookingDate.text.toString().trim()
            val slot = etSlot.text.toString().trim()

            if (hall == null || date.isEmpty() || slot.isEmpty()) {
                Toast.makeText(this, "Please select Hall, Date and Slot to cancel", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("allocations")
                .whereEqualTo("isHallBooking", true)
                .whereEqualTo("roomNo", hall.roomNo)
                .whereEqualTo("date", date)
                .whereEqualTo("slot", slot)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty) {
                        Toast.makeText(this, "No such hall booking found", Toast.LENGTH_LONG).show()
                    } else {
                        var deletedCount = 0
                        for (doc in result) {
                            val bookingFaculty = doc.getString("faculty") ?: ""
                            if (currentUserRole == "admin" || bookingFaculty.lowercase() == currentUserName.lowercase()) {
                                db.collection("allocations").document(doc.id).delete()
                                deletedCount++
                            }
                        }
                        
                        if (deletedCount > 0) {
                            Toast.makeText(this, "Cancelled $deletedCount booking(s) successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "Permission Denied: You can only cancel your own bookings", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error cancelling booking", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun fetchUserInfo(etFaculty: EditText, navigationView: NavigationView) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("email", user.email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    currentUserRole = snapshot.documents[0].getString("role") ?: "faculty"
                    currentUserName = snapshot.documents[0].getString("username") ?: ""
                    
                    val headerView = navigationView.getHeaderView(0)
                    val tvUserRoleNav = headerView.findViewById<TextView>(R.id.tvUserRoleNav)

                    if (currentUserRole == "faculty") {
                        tvUserRoleNav?.text = "Faculty"
                        etFaculty.setText(currentUserName)
                        etFaculty.isEnabled = false
                        navigationView.menu.findItem(R.id.nav_upload_course).isVisible = false
                        navigationView.menu.findItem(R.id.nav_reset).isVisible = false
                    } else {
                        tvUserRoleNav?.text = "Administrator"
                        navigationView.menu.findItem(R.id.nav_upload_course).isVisible = true
                        navigationView.menu.findItem(R.id.nav_reset).isVisible = true
                    }
                }
            }
    }

    private fun showHallBookingsDialog() {
        val db = FirebaseFirestore.getInstance()
        db.collection("allocations")
            .whereEqualTo("isHallBooking", true)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "No hall bookings found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val allBookings = result.documents.map { doc ->
                    HallBooking(
                        doc.getString("hallName") ?: "Unknown Hall",
                        doc.getString("roomNo") ?: "N/A",
                        doc.getString("date") ?: "N/A",
                        doc.getString("slot") ?: "N/A",
                        doc.getString("faculty") ?: "N/A",
                        doc.getString("subject") ?: "N/A"
                    )
                }

                val currentBookings = allBookings.filter { !isBookingExpired(it.date, it.slot) }.sortedBy { it.date }

                if (currentBookings.isEmpty()) {
                    Toast.makeText(this, "No active hall bookings found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val recyclerView = RecyclerView(this).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    layoutManager = LinearLayoutManager(this@BookHallActivity)
                    adapter = HallBookingAdapter(currentBookings)
                }

                AlertDialog.Builder(this)
                    .setTitle("Current Hall Bookings")
                    .setView(recyclerView)
                    .setPositiveButton("Close", null)
                    .show()
            }
    }

    private fun isBookingExpired(dateStr: String, slot: String): Boolean {
        try {
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = Calendar.getInstance()
            val todayStr = sdfDate.format(today.time)

            if (dateStr < todayStr) return true
            if (dateStr > todayStr) return false

            val timeParts = slot.split("-")
            if (timeParts.size < 2) return false 

            val endTimeStr = timeParts[1].trim().replace(".", ":")
            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            val nowTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(today.time)

            val nowTime = sdfTime.parse(nowTimeStr)
            val endTime = sdfTime.parse(endTimeStr)

            return endTime != null && nowTime != null && nowTime.after(endTime)
        } catch (e: Exception) {
            return false
        }
    }

    private fun loadAndCategorizeHalls() {
        try {
            assets.open("GALLERY.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(",").map { it.trim() }
                    if (tokens.size >= 3) {
                        val hall = Hall(tokens[0], tokens[1], tokens[2])
                        val category = when {
                            hall.name.uppercase().contains("GALLERY") -> "Gallery"
                            hall.name.uppercase().contains("SMART CLASSROOM") -> "Smart Classrooms"
                            hall.name.uppercase().contains("ONLINE") -> "Online Exam Hall"
                            hall.name.uppercase().contains("VAIAL") -> "Vaial Lab"
                            hall.name.uppercase().contains("SBST") -> "SBST Lab"
                            else -> "Others"
                        }
                        hallsByCategory.getOrPut(category) { mutableListOf() }.add(hall)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading halls", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigation(drawerLayout: DrawerLayout, navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.nav_book -> {
                    startActivity(Intent(this, BookClassroomActivity::class.java))
                    finish()
                }
                R.id.nav_view -> {
                    startActivity(Intent(this, ViewClassroomsActivity::class.java))
                    finish()
                }
                R.id.nav_logout -> {
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private class HallBookingAdapter(private val bookings: List<HallBooking>) : RecyclerView.Adapter<HallBookingAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvHallName)
            val room: TextView = view.findViewById(R.id.tvRoomNo)
            val date: TextView = view.findViewById(R.id.tvDate)
            val slot: TextView = view.findViewById(R.id.tvSlot)
            val faculty: TextView = view.findViewById(R.id.tvFaculty)
            val purpose: TextView = view.findViewById(R.id.tvPurpose)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hall_booking, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val b = bookings[position]
            holder.name.text = b.hallName
            holder.room.text = b.roomNo
            holder.date.text = b.date
            holder.slot.text = b.slot
            holder.faculty.text = b.faculty
            holder.purpose.text = b.purpose
        }

        override fun getItemCount() = bookings.size
    }
}
