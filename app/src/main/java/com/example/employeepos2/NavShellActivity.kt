package com.example.employeepos2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class NavShellActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nav_shell)

        val navView: BottomNavigationView = findViewById(R.id.bottom_nav_v2)

        // Default Start Screen
        loadFragment(HomeFragmentV2())

        navView.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragmentV2()
                R.id.nav_jobs -> JobsFragmentV2()
                R.id.nav_inventory -> InventoryFragmentV2()
                R.id.nav_profile -> ProfileFragmentV2()
                else -> HomeFragmentV2()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container_v2, fragment)
            .commit()
    }
}