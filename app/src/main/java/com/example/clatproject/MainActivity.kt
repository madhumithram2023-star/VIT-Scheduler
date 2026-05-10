package com.example.clatproject

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clatproject.database.Appdatabase
import com.example.clatproject.database.Classroom
import com.example.clatproject.database.Slot
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var occupancyChart: BarChart
    private var allocationListener: ListenerRegistration? = null
    private var configListener: ListenerRegistration? = null
    private var currentUserName: String = ""
    private var activeAllocations: List<Map<String, Any>> = mutableListOf()
    
    private var selectedDate: Calendar = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
    private var dateOverrides = mutableMapOf<String, String>()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = getFileName(it)
            if (!name.lowercase().endsWith(".csv")) {
                Toast.makeText(this, "Please select a valid CSV file", Toast.LENGTH_SHORT).show()
                return@let
            }
            processSelectedFile(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val db = Appdatabase.getDatabase(this)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvTotalRooms = findViewById<TextView>(R.id.tvTotalRoomsCount)
        val tvAssigned = findViewById<TextView>(R.id.tvAssignedCount)
        val tvUtilization = findViewById<TextView>(R.id.tvUtilizationPercent)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcomeUser)
        val tvTimetable = findViewById<TextView>(R.id.tvCurrentTimetable)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        occupancyChart = findViewById(R.id.occupancyChart)
        occupancyChart.setNoDataText("Select a day with active slots to view analytics.")
        occupancyChart.setNoDataTextColor(android.graphics.Color.GRAY)
        setupDrawer(drawerLayout, navigationView)
        findViewById<View>(R.id.btnBookClassroom).setOnClickListener {
            startActivity(Intent(this, BookClassroomActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.menuIconClick).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        findViewById<View>(R.id.uploadCard).setOnClickListener {
            filePickerLauncher.launch("*/*")
        }
        findViewById<ImageButton>(R.id.btnExport).setOnClickListener {
            exportAllocationsFromFirebase()
        }
        findViewById<View>(R.id.btnReset).setOnClickListener {
            resetSystem(tvStatus, tvAssigned, tvUtilization)
        }
        findViewById<View>(R.id.viewClassroomsCard).setOnClickListener {
            startActivity(Intent(this, ViewClassroomsActivity::class.java))
        }
        findViewById<View>(R.id.cardAssigned).setOnClickListener {
            showActiveAllocationsDialog()
        }
        findViewById<View>(R.id.btnBookHall).setOnClickListener {
            startActivity(Intent(this, BookHallActivity::class.java))
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val roomCount = db.Classroomdao().getTotalRoomsCountSync()
            if (roomCount == 0) {
                importClassroomsFromAssets(db)
                importSlotsFromAssets(db)
            }
            withContext(Dispatchers.Main) {
                fetchUserName(tvWelcome, db, tvTotalRooms, tvAssigned, tvUtilization, tvTimetable)
                startGlobalConfigListener(tvTimetable, db, tvTotalRooms, tvAssigned, tvUtilization)
                applyRoleRestrictions(navigationView, tvTimetable)
            }
        }
    }
    private fun startGlobalConfigListener(tvTimetable: TextView, db: Appdatabase, tvTotal: TextView, tvAssigned: TextView, tvUtil: TextView) {
        configListener = FirebaseFirestore.getInstance().collection("date_configs")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    dateOverrides.clear()
                    for (doc in snapshot.documents) {
                        dateOverrides[doc.id] = doc.getString("followsDay") ?: "AUTO"
                    }
                    refreshDashboardWithCurrentSettings(db, tvTotal, tvAssigned, tvUtil, tvTimetable)
                }
            }
    }

    private fun refreshDashboardWithCurrentSettings(db: Appdatabase, tvTotal: TextView, tvAssigned: TextView, tvUtil: TextView, tvTimetable: TextView) {
        FirebaseFirestore.getInstance().collection("allocations").get().addOnSuccessListener { allocSnapshot ->
            val allocations = allocSnapshot.documents.map { it.data ?: emptyMap() }
            lifecycleScope.launch(Dispatchers.IO) {
                refreshDashboardCounts(db, tvTotal, tvAssigned, tvUtil, allocations, tvTimetable)
            }
        }
    }

    private fun fetchUserName(tvWelcome: TextView, db: Appdatabase, tvTotal: TextView, tvAssigned: TextView, tvUtil: TextView, tvTimetable: TextView) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("email", user.email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val name = snapshot.documents[0].getString("username") ?: "User"
                    currentUserName = name
                    tvWelcome.text = "Welcome, $name 👋"
                    startLiveDashboardSync(db, tvTotal, tvAssigned, tvUtil, tvTimetable)
                }
            }
    }

    private fun startLiveDashboardSync(db: Appdatabase, tvTotal: TextView, tvAssigned: TextView, tvUtil: TextView, tvTimetable: TextView) {
        allocationListener = FirebaseFirestore.getInstance().collection("allocations")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val allocations = snapshot.documents.map { it.data ?: emptyMap() }
                lifecycleScope.launch(Dispatchers.IO) {
                    refreshDashboardCounts(db, tvTotal, tvAssigned, tvUtil, allocations, tvTimetable)
                }
            }
    }

    private fun applyRoleRestrictions(navigationView: NavigationView, tvTimetable: TextView) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        
        db.collection("users")
            .whereEqualTo("email", user.email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val role = snapshot.documents[0].getString("role") ?: "faculty"
                    val headerView = navigationView.getHeaderView(0)
                    val tvUserRoleNav = headerView.findViewById<TextView>(R.id.tvUserRoleNav)
                    
                    runOnUiThread {
                        if (role == "faculty") {
                            tvUserRoleNav?.text = "Faculty"
                            findViewById<View>(R.id.btnReset).visibility = View.GONE
                            findViewById<View>(R.id.uploadCard).visibility = View.GONE
                            navigationView.menu.findItem(R.id.nav_reset).isVisible = false
                            navigationView.menu.findItem(R.id.nav_upload_course).isVisible = false
                            tvTimetable.setOnClickListener { showDatePickerDialog(tvTimetable) }
                        } else {
                            tvUserRoleNav?.text = "Administrator"
                            findViewById<View>(R.id.btnReset).visibility = View.VISIBLE
                            findViewById<View>(R.id.uploadCard).visibility = View.VISIBLE
                            navigationView.menu.findItem(R.id.nav_reset).isVisible = true
                            navigationView.menu.findItem(R.id.nav_upload_course).isVisible = true
                            tvTimetable.setOnClickListener { 
                                showAdminScheduleOptions(tvTimetable)
                            }
                        }
                    }
                }
            }
    }

    private fun showDatePickerDialog(tvTimetable: TextView) {
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate.set(year, month, day)
            val db = Appdatabase.getDatabase(this)
            refreshDashboardWithCurrentSettings(db, findViewById(R.id.tvTotalRoomsCount), findViewById(R.id.tvAssignedCount), findViewById(R.id.tvUtilizationPercent), tvTimetable)
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showAdminScheduleOptions(tvTimetable: TextView) {
        val isSaturday = selectedDate.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_schedule_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.optionChangeDate).setOnClickListener {
            dialog.dismiss()
            showDatePickerDialog(tvTimetable)
        }

        val assignTimetableOption = dialogView.findViewById<View>(R.id.optionAssignTimetable)
        if (isSaturday) {
            assignTimetableOption.visibility = View.VISIBLE
            assignTimetableOption.setOnClickListener {
                dialog.dismiss()
                showDayOverrideDialog(tvTimetable)
            }
        } else {
            assignTimetableOption.visibility = View.GONE
        }

        dialog.show()
    }

    private fun showDayOverrideDialog(tvTimetable: TextView) {
        val dateStr = dateFormatter.format(selectedDate.time)
        val days = arrayOf("Auto (Default)", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
        AlertDialog.Builder(this)
            .setTitle("Assign Schedule for $dateStr")
            .setItems(days) { _, which ->
                val day = if (which == 0) "AUTO" else days[which].uppercase()
                FirebaseFirestore.getInstance().collection("date_configs").document(dateStr)
                    .set(hashMapOf("followsDay" to day))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Schedule set for $dateStr", Toast.LENGTH_SHORT).show()
                    }
            }.show()
    }

    private fun setupDrawer(drawerLayout: DrawerLayout, navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> drawerLayout.closeDrawer(GravityCompat.START)
                R.id.nav_upload_course -> filePickerLauncher.launch("*/*")
                R.id.nav_book -> startActivity(Intent(this, BookClassroomActivity::class.java))
                R.id.nav_view -> startActivity(Intent(this, ViewClassroomsActivity::class.java))
                R.id.nav_reset -> resetSystem(findViewById(R.id.tvStatus), findViewById(R.id.tvAssignedCount), findViewById(R.id.tvUtilizationPercent))
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

    private suspend fun refreshDashboardCounts(db: Appdatabase, tvTotal: TextView, tvAssigned: TextView, tvUtil: TextView, allocations: List<Map<String, Any>>, tvTimetable: TextView) {
        val totalRoomsCount = db.Classroomdao().getTotalRoomsCountSync()
        val dateStr = dateFormatter.format(selectedDate.time)
        
        val override = dateOverrides[dateStr]
        val dayToFollow = if (override != null && override != "AUTO") override 
                         else SimpleDateFormat("EEEE", Locale.ENGLISH).format(selectedDate.time).uppercase()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val nowStr = timeFormat.format(Calendar.getInstance().time)

        val allSlots = db.SlotDao().getSlotsByDay(dayToFollow)
        val activeSlots = allSlots.filter { isTimeBetween(nowStr, it.startTime, it.endTime) }.map { it.slotName.uppercase() }

        val activeList = mutableListOf<Map<String, Any>>()
        var count = 0
        for (alloc in allocations) {
            val allocDate = alloc["date"] as? String
            // FIX: If no date is attached (CSV upload), it is treated as a repeating booking.
            // It should be counted if it belongs to an active slot for the CURRENT dayToFollow.
            val isCorrectDate = if (allocDate != null) allocDate == dateStr else true

            if (isCorrectDate) {
                val bookedSlots = (alloc["slot"] as? String ?: "").uppercase().split("+").map { it.trim() }
                if (activeSlots.any { it in bookedSlots }) {
                    count++
                    activeList.add(alloc)
                }
            }
        }
        
        activeAllocations = activeList

        withContext(Dispatchers.Main) {
            tvTotal.text = totalRoomsCount.toString()
            tvAssigned.text = count.toString()
            if (totalRoomsCount > 0) {
                val percent = (count * 100) / totalRoomsCount
                tvUtil.text = "$percent%"
            } else {
                tvUtil.text = "0%"
            }
            updateOccupancyChart(allSlots, allocations, dateStr, dayToFollow)
            
            val formattedDate = displayDateFormat.format(selectedDate.time)
            val isOverrideActive = override != null && override != "AUTO"
            
            if (isOverrideActive) {
                tvTimetable.text = "$formattedDate • Following $override Timetable"
            } else {
                tvTimetable.text = formattedDate
            }
        }
    }

    private fun updateOccupancyChart(daySlots: List<Slot>, allocations: List<Map<String, Any>>, dateStr: String, dayToFollow: String) {
        if (daySlots.isEmpty()) {
            occupancyChart.clear()
            occupancyChart.setNoDataText("No slots defined for $dayToFollow.")
            occupancyChart.invalidate()
            return
        }

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        val colors = mutableListOf<Int>()

        val sortedSlots = daySlots.sortedBy { it.startTime }

        sortedSlots.forEachIndexed { index, slot ->
            var count = 0
            val currentSlotName = slot.slotName.uppercase()
            val currentSlotParts = currentSlotName.split("+").map { it.trim() }

            allocations.forEach { alloc ->
                val allocDate = alloc["date"] as? String
                // FIX: Ensure repeating CSV allocations show up when a timetable override is active
                val isCorrectDate = if (allocDate != null) allocDate == dateStr else true
                
                if (isCorrectDate) {
                    val bookedSlots = (alloc["slot"] as? String ?: "").uppercase().split("+").map { it.trim() }
                    if (currentSlotParts.any { it in bookedSlots } || bookedSlots.any { it in currentSlotParts }) {
                        count++
                    }
                }
            }
            
            entries.add(BarEntry(index.toFloat(), count.toFloat()))
            labels.add(slot.slotName)
            colors.add(if (slot.type.uppercase() == "LAB") android.graphics.Color.parseColor("#008080") else android.graphics.Color.parseColor("#6200EE"))
        }

        val dataSet = BarDataSet(entries, "Rooms Booked").apply {
            this.colors = colors
            valueTextColor = android.graphics.Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = if (value > 0) value.toInt().toString() else ""
            }
        }

        occupancyChart.apply {
            data = BarData(dataSet)
            xAxis.valueFormatter = object : ValueFormatter() { override fun getFormattedValue(value: Float): String = labels.getOrNull(value.toInt()) ?: "" }
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = -45f
            xAxis.setDrawGridLines(false)

            axisLeft.axisMinimum = 0f
            axisLeft.granularity = 1f
            axisLeft.setDrawGridLines(false)
            
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    private fun isTimeBetween(now: String, start: String, end: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.ENGLISH)
            val nowTime = sdf.parse(now)
            val startTime = sdf.parse(start)
            val endTime = sdf.parse(end)
            nowTime != null && startTime != null && endTime != null && nowTime >= startTime && nowTime <= endTime
        } catch (e: Exception) { false }
    }

    private fun showActiveAllocationsDialog() {
        if (activeAllocations.isEmpty()) {
            Toast.makeText(this, "No classes are currently in session.", Toast.LENGTH_SHORT).show()
            return
        }

        val recyclerView = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ActiveClassAdapter(activeAllocations)
        }

        AlertDialog.Builder(this)
            .setTitle("Classes Currently in Session")
            .setView(recyclerView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun processSelectedFile(uri: Uri) {
        val db = Appdatabase.getDatabase(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val lines = inputStream.bufferedReader().readLines()
                    var successCount = 0
                    var failCount = 0
                    val snapshot = FirebaseFirestore.getInstance().collection("allocations").get().await()
                    val existingAllocations = snapshot.documents.map { it.data ?: emptyMap() }.toMutableList()

                    for (line in lines.drop(1)) {
                        if (line.isBlank()) continue
                        val tokens = line.split(",").map { it.trim() }
                        if (tokens.size < 5) { failCount++; continue }

                        val facultyId = tokens[0]
                        val facultyName = tokens[1]
                        val slot = tokens[2]
                        val dept = tokens[3]
                        val subject = tokens[4]

                        val requestedSlots = slot.split("+").map { it.trim().uppercase() }
                        val typeReq = if (slot.uppercase().startsWith("L")) "LAB" else "THEORY"

                        var availableRooms = db.Classroomdao().getRoomsByDept(dept, typeReq)
                        var selectedRoom: Classroom? = null
                        for (room in availableRooms) {
                            var hasConflict = false
                            for (alloc in existingAllocations) {
                                val occupiedSlots = (alloc["slot"] as? String ?: "").uppercase().split("+").map { it.trim() }
                                if (requestedSlots.any { it in occupiedSlots } && (alloc["roomNo"] == room.roomNo || alloc["facultyId"] == facultyId)) {
                                    hasConflict = true; break
                                }
                            }
                            if (!hasConflict) { selectedRoom = room; break }
                        }

                        if (selectedRoom != null) {
                            val data = hashMapOf("roomNo" to selectedRoom.roomNo, "building" to selectedRoom.building, "slot" to slot, "dept" to dept, "subject" to subject, "faculty" to facultyName, "facultyId" to facultyId)
                            try {
                                FirebaseFirestore.getInstance().collection("allocations").add(data).await()
                                existingAllocations.add(data)
                                successCount++
                            } catch (e: Exception) { failCount++ }
                        } else { failCount++ }
                    }
                    withContext(Dispatchers.Main) { findViewById<TextView>(R.id.tvStatus).text = "Uploaded: $successCount | Failed: $failCount" }
                }
            } catch (e: Exception) { Log.e("UPLOAD", "Error: ${e.message}") }
        }
    }

    private fun resetSystem(tvStatus: TextView?, tvAssigned: TextView, tvUtil: TextView) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_reset, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnResetConfirm).setOnClickListener {
            dialog.dismiss()
            val db = FirebaseFirestore.getInstance()
            db.collection("allocations").get().addOnSuccessListener { result ->
                val batch = db.batch()
                for (doc in result) batch.delete(doc.reference)
                batch.commit().addOnSuccessListener {
                    tvStatus?.text = "System Reset Done"
                    tvAssigned.text = "0"
                    tvUtil.text = "0%"
                    Toast.makeText(this, "All allocations deleted", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialogView.findViewById<View>(R.id.btnResetCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun exportAllocationsFromFirebase() {
        FirebaseFirestore.getInstance().collection("allocations").get().addOnSuccessListener { result ->
            if (result.isEmpty) { Toast.makeText(this, "No data", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
            val csvBuilder = StringBuilder("FacultyId,Faculty,Slot,Dept,Subject,RoomNo,Building\n")
            for (doc in result) csvBuilder.append("${doc.getString("facultyId")},${doc.getString("faculty")},${doc.getString("slot")},${doc.getString("dept")},${doc.getString("subject")},${doc.getString("roomNo")},${doc.getString("building")}\n")
            try {
                val fileName = "Classroom_Allocations.csv"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply { 
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) 
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) { 
                        contentResolver.openOutputStream(uri)?.use { it.write(csvBuilder.toString().toByteArray()) }
                        Toast.makeText(this, "Saved to Downloads", Toast.LENGTH_SHORT).show() 
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, fileName)
                    FileOutputStream(file).use { it.write(csvBuilder.toString().toByteArray()) }
                    Toast.makeText(this, "Saved to ${file.absolutePath}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show() }
        }
    }

    private suspend fun importClassroomsFromAssets(db: Appdatabase) {
        try {
            assets.open("SJT_DETAILS.csv").bufferedReader().useLines { lines ->
                val list = lines.drop(1).mapNotNull { line ->
                    val t = line.split(",").map { it.trim() }
                    if (t.size >= 4) Classroom(t[0], t[1], t[2], t[3]) else null
                }.toList()
                db.Classroomdao().insertAll(list)
            }
        } catch (e: Exception) { Log.e("INIT", "Room import failed") }
    }

    private suspend fun importSlotsFromAssets(db: Appdatabase) {
        try {
            assets.open("SLOT_DETAILS.csv").bufferedReader().useLines { lines ->
                val list = lines.drop(1).mapNotNull { line ->
                    val t = line.split(",").map { it.trim() }
                    if (t.size >= 5) Slot(t[0], t[1], t[2], t[3], t[4]) else null
                }.toList()
                db.SlotDao().insertAll(list)
            }
        } catch (e: Exception) { Log.e("INIT", "Slot import failed") }
    }

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            else "file.csv"
        } ?: "file.csv"
    }

    override fun onDestroy() {
        super.onDestroy()
        allocationListener?.remove()
        configListener?.remove()
    }

    private class ActiveClassAdapter(private val allocations: List<Map<String, Any>>) : RecyclerView.Adapter<ActiveClassAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvRoomNo: TextView = view.findViewById(R.id.tvRoomNo)
            val tvSlot: TextView = view.findViewById(R.id.tvSlot)
            val tvSubject: TextView = view.findViewById(R.id.tvSubject)
            val tvFaculty: TextView = view.findViewById(R.id.tvFaculty)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_active_class, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val alloc = allocations[position]
            holder.tvRoomNo.text = "Room: ${alloc["roomNo"] ?: "N/A"}"
            holder.tvSlot.text = "Slot: ${alloc["slot"] ?: "N/A"}"
            holder.tvSubject.text = alloc["subject"]?.toString() ?: "No Subject"
            holder.tvFaculty.text = "Faculty: ${alloc["faculty"] ?: "N/A"}"
        }

        override fun getItemCount() = allocations.size
    }
}
