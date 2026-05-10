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

class BookClassroomActivity : AppCompatActivity() {

    private val calendar = Calendar.getInstance()
    private var currentUserRole: String = "faculty"
    private var currentUserName: String = ""
    private var currentUserFacultyId: String = ""

    data class ManualBooking(
        val roomNo: String,
        val building: String,
        val date: String,
        val slot: String,
        val faculty: String,
        val course: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_classroom)

        val etRoom = findViewById<EditText>(R.id.etRoom)
        val etBuilding = findViewById<EditText>(R.id.etBuilding)
        val etBookingDate = findViewById<EditText>(R.id.etBookingDate)
        val etSlot = findViewById<EditText>(R.id.etSlot)
        val etFaculty = findViewById<EditText>(R.id.etFaculty)
        val etCourse = findViewById<EditText>(R.id.etCourse)
        val etFacultyId = findViewById<EditText>(R.id.etFacultyId)
        val btnBook = findViewById<Button>(R.id.btnConfirmBooking)
        val btnViewBookings = findViewById<Button>(R.id.btnViewMyBookings)
        val btnRemove = findViewById<Button>(R.id.btnRemoveBooking)
        
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val menuIcon = findViewById<ImageView>(R.id.menuIcon)

        val db = FirebaseFirestore.getInstance()

        // Fetch User Info and Restrict Faculty
        fetchUserInfo(etFaculty, etFacultyId, navigationView)

        // Set Default Date (Today)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        etBookingDate.setText(sdf.format(calendar.time))

        // Date Picker Dialog
        etBookingDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                etBookingDate.setText(sdf.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Setup Hamburger Menu
        menuIcon.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.nav_book -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
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

        // --- CONFIRM BOOKING ---
        btnBook.setOnClickListener {
            val roomNo = etRoom.text.toString().trim()
            val building = etBuilding.text.toString().trim()
            val date = etBookingDate.text.toString().trim()
            val slot = etSlot.text.toString().trim()
            val faculty = etFaculty.text.toString().trim()
            val course = etCourse.text.toString().trim()
            val facultyId = etFacultyId.text.toString().trim()

            if (roomNo.isEmpty() || building.isEmpty() || slot.isEmpty() || date.isEmpty() || faculty.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val requestedSlots = slot.uppercase().split("+").map { it.trim() }

            db.collection("allocations")
                .whereEqualTo("roomNo", roomNo)
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
                        Toast.makeText(this, "Conflict! Room already booked for these slots on $date.", Toast.LENGTH_LONG).show()
                    } else {
                        val data = hashMapOf(
                            "roomNo" to roomNo,
                            "building" to building,
                            "date" to date,
                            "slot" to slot,
                            "faculty" to faculty,
                            "subject" to course,
                            "facultyId" to facultyId,
                            "isPrebooked" to true
                        )

                        db.collection("allocations").add(data).addOnSuccessListener {
                            Toast.makeText(this, "Booked successfully for $date!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
        }

        // --- VIEW BOOKINGS ---
        btnViewBookings.setOnClickListener {
            showReservationsDialog()
        }

        // --- REMOVE BOOKING ---
        btnRemove.setOnClickListener {
            val roomNo = etRoom.text.toString().trim()
            val date = etBookingDate.text.toString().trim()
            val slot = etSlot.text.toString().trim()

            if (roomNo.isEmpty() || slot.isEmpty() || date.isEmpty()) {
                Toast.makeText(this, "Please select Room, Date, and Slot to cancel", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("allocations")
                .whereEqualTo("roomNo", roomNo)
                .whereEqualTo("date", date)
                .whereEqualTo("slot", slot)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty) {
                        Toast.makeText(this, "No such booking found at this slot and date", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "Error removing booking", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showReservationsDialog() {
        val db = FirebaseFirestore.getInstance()
        var query = db.collection("allocations").whereEqualTo("isPrebooked", true)
        
        if (currentUserRole == "faculty") {
            query = query.whereEqualTo("faculty", currentUserName)
        }

        query.get().addOnSuccessListener { result ->
            if (result.isEmpty) {
                Toast.makeText(this, "No active manual bookings found.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val bookings = result.documents.map { doc ->
                ManualBooking(
                    doc.getString("roomNo") ?: "N/A",
                    doc.getString("building") ?: "N/A",
                    doc.getString("date") ?: "N/A",
                    doc.getString("slot") ?: "N/A",
                    doc.getString("faculty") ?: "N/A",
                    doc.getString("subject") ?: "N/A"
                )
            }.sortedBy { it.date }

            val recyclerView = RecyclerView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutManager = LinearLayoutManager(this@BookClassroomActivity)
                adapter = ManualBookingAdapter(bookings)
            }

            AlertDialog.Builder(this)
                .setTitle(if (currentUserRole == "admin") "All Manual Bookings" else "My Manual Bookings")
                .setView(recyclerView)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun fetchUserInfo(etFaculty: EditText, etFacultyId: EditText, navigationView: NavigationView) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        
        db.collection("users")
            .whereEqualTo("email", user.email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    currentUserRole = snapshot.documents[0].getString("role") ?: "faculty"
                    currentUserName = snapshot.documents[0].getString("username") ?: ""
                    currentUserFacultyId = snapshot.documents[0].getString("facultyId") ?: ""
                    
                    val headerView = navigationView.getHeaderView(0)
                    val tvUserRoleNav = headerView.findViewById<TextView>(R.id.tvUserRoleNav)

                    if (currentUserRole == "faculty") {
                        tvUserRoleNav?.text = "Faculty"
                        etFaculty.setText(currentUserName)
                        etFaculty.isEnabled = false
                        if (currentUserFacultyId.isNotEmpty()) {
                            etFacultyId.setText(currentUserFacultyId)
                            etFacultyId.isEnabled = false
                        }
                        // Hide Admin options in menu
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

    private class ManualBookingAdapter(private val bookings: List<ManualBooking>) : RecyclerView.Adapter<ManualBookingAdapter.ViewHolder>() {
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
            holder.name.text = "Manual Reservation"
            holder.room.text = "${b.roomNo} (${b.building})"
            holder.date.text = b.date
            holder.slot.text = b.slot
            holder.faculty.text = b.faculty
            holder.purpose.text = b.course
        }

        override fun getItemCount() = bookings.size
    }
}
