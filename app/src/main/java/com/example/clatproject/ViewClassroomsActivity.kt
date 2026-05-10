package com.example.clatproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clatproject.database.Appdatabase
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewClassroomsActivity : AppCompatActivity() {

    private lateinit var roomAdapter: RoomAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_classrooms)

        val db = Appdatabase.getDatabase(this)

        // --- UI References ---
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val menuBtn = findViewById<LinearLayout>(R.id.menuIconClickView)
        val navigationView = findViewById<NavigationView>(R.id.navigationview)
        val btnFind = findViewById<Button>(R.id.btnFindRooms)
        val spinnerDept = findViewById<Spinner>(R.id.spinnerDept)
        val spinnerBuilding = findViewById<Spinner>(R.id.spinnerBuilding)
        val etSlot = findViewById<EditText>(R.id.etSlot)
        val tvHeader = findViewById<TextView>(R.id.tvResultHeader)
        val rvResults = findViewById<RecyclerView>(R.id.rvResults)

        // --- RecyclerView Setup ---
        roomAdapter = RoomAdapter(mutableListOf())
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = roomAdapter

        setupSpinners()
        fetchUserInfo(navigationView)

        // --- Search Logic ---
        btnFind.setOnClickListener {
            val dept = spinnerDept.selectedItem.toString()
            val building = spinnerBuilding.selectedItem.toString()
            val slot = etSlot.text.toString().trim().uppercase()

            val selectedFloor = findViewById<Spinner>(R.id.spinnerFloor).selectedItem.toString()
            val floorDigit = when(selectedFloor) {
                "Ground Floor" -> "G"
                "1st Floor" -> "1"
                "2nd Floor" -> "2"
                "3rd Floor" -> "3"
                "4th Floor" -> "4"
                "5th Floor" -> "5"
                "6th Floor" -> "6"
                "7th Floor" -> "7"
                else -> ""
            }

            if (slot.isEmpty()) {
                etSlot.error = "Enter slot"
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val rooms = db.Classroomdao().getAvailableRooms(dept, slot, building, floorDigit)

                    withContext(Dispatchers.Main) {
                        if (rooms.isEmpty()) {
                            tvHeader.visibility = View.GONE
                            roomAdapter.submitList(emptyList())
                            Toast.makeText(this@ViewClassroomsActivity, "No available rooms found", Toast.LENGTH_SHORT).show()
                        } else {
                            tvHeader.visibility = View.VISIBLE
                            roomAdapter.submitList(rooms)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewClassroomsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // --- Menu Logic ---
        menuBtn.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.nav_book -> {
                    startActivity(Intent(this, BookClassroomActivity::class.java))
                    finish()
                }
                R.id.nav_view -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
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

    private fun setupSpinners() {
        val depts = arrayOf("CSE", "IT", "ECE")
        val deptAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, depts)
        findViewById<Spinner>(R.id.spinnerDept).adapter = deptAdapter

        val buildings = arrayOf("PRP", "SJT")
        val buildAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, buildings)
        findViewById<Spinner>(R.id.spinnerBuilding).adapter = buildAdapter

        val floors = arrayOf("Ground Floor", "1st Floor", "2nd Floor", "3rd Floor", "4th Floor", "5th Floor", "6th Floor", "7th Floor")
        val floorAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, floors)
        findViewById<Spinner>(R.id.spinnerFloor).adapter = floorAdapter
    }

    private fun fetchUserInfo(navigationView: NavigationView) {
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

                    if (role == "faculty") {
                        tvUserRoleNav?.text = "Faculty"
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
}
