package com.example

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.util.SecureProfileStore
import com.example.util.ThemeHelper
import com.google.android.material.materialswitch.MaterialSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ZEN", appName)
  }

  @Test
  fun `profile setup renders visible form without FLAG_SECURE black screen and opens date picker`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = Intent(context, ProfileSetupActivity::class.java).apply {
      putExtra(ProfileSetupActivity.EXTRA_IS_FIRST_LAUNCH, true)
    }
    val activity = Robolectric.buildActivity(ProfileSetupActivity::class.java, intent)
      .create()
      .start()
      .resume()
      .get()

    val flags = activity.window.attributes.flags
    assertEquals(0, flags and WindowManager.LayoutParams.FLAG_SECURE)

    val title = activity.findViewById<TextView>(R.id.tv_profile_screen_title)
    val saveBtn = activity.findViewById<View>(R.id.btn_profile_save)
    val skipBtn = activity.findViewById<View>(R.id.btn_profile_skip_bottom)
    assertNotNull(title)
    assertNotNull(saveBtn)
    assertNotNull(skipBtn)
    assertEquals(View.VISIBLE, saveBtn.visibility)
    assertEquals(View.VISIBLE, skipBtn.visibility)

    val btnStartDate = activity.findViewById<View>(R.id.btn_semester_start_date)
    assertNotNull(btnStartDate)
    btnStartDate.performClick()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

    val fragment = activity.supportFragmentManager.findFragmentByTag("SEMESTER_START_PICKER")
    assertNotNull(fragment)
  }

  @Test
  fun `profile setup skip marks seen and launches MainActivity`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = Intent(context, ProfileSetupActivity::class.java).apply {
      putExtra(ProfileSetupActivity.EXTRA_IS_FIRST_LAUNCH, true)
    }
    val activity = Robolectric.buildActivity(ProfileSetupActivity::class.java, intent)
      .create()
      .start()
      .resume()
      .get()

    assertFalse(SecureProfileStore.hasSeenProfileSetup(context))
    activity.findViewById<View>(R.id.btn_profile_skip_bottom).performClick()

    assertTrue(SecureProfileStore.hasSeenProfileSetup(context))
    assertTrue(activity.isFinishing)
    val nextIntent = Shadows.shadowOf(activity).nextStartedActivity
    assertNotNull(nextIntent)
    assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
  }

  @Test
  fun `main activity loads dashboard cleanly when onboarding and profile setup complete`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE)
      .edit()
      .putBoolean(OnboardingActivity.PREF_KEY_MIGRATION, false)
      .commit()
    SecureProfileStore.markProfileSetupSeen(context)

    val activity = Robolectric.buildActivity(MainActivity::class.java)
      .create()
      .start()
      .resume()
      .get()

    assertFalse(activity.isFinishing)
    ShadowLooper.idleMainLooper()
    assertNotNull(activity.findViewById<View>(R.id.view_pager))
    assertNotNull(activity.findViewById<View>(R.id.bottom_nav_container))
    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.bottom_nav_container).visibility)
  }

  @Test
  fun `onboarding start fresh routes to ProfileSetupActivity on first launch`() {
    val activity = Robolectric.buildActivity(OnboardingActivity::class.java)
      .create()
      .start()
      .resume()
      .get()

    activity.findViewById<View>(R.id.btn_onboarding_fresh).performClick()
    assertTrue(activity.isFinishing)
    val nextIntent = Shadows.shadowOf(activity).nextStartedActivity
    assertNotNull(nextIntent)
    assertEquals(ProfileSetupActivity::class.java.name, nextIntent.component?.className)
    assertTrue(nextIntent.getBooleanExtra(ProfileSetupActivity.EXTRA_IS_FIRST_LAUNCH, false))
  }

  @Test
  fun `settings and storage screen renders sections, switches scope, toggles haptic, and passes integrity check`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val activity = Robolectric.buildActivity(BackupActivity::class.java)
      .create()
      .start()
      .resume()
      .get()

    assertFalse(activity.isFinishing)
    assertNotNull(activity.findViewById<View>(R.id.card_scope_full))
    assertNotNull(activity.findViewById<View>(R.id.card_scope_attendance))
    assertNotNull(activity.findViewById<View>(R.id.card_scope_notes))
    assertNotNull(activity.findViewById<View>(R.id.btn_export_backup))
    assertNotNull(activity.findViewById<View>(R.id.btn_import_backup))
    assertNotNull(activity.findViewById<View>(R.id.btn_share_timetable))
    assertNotNull(activity.findViewById<View>(R.id.card_settings_activity_log))

    val tvLastBackup = activity.findViewById<TextView>(R.id.tv_last_backup_time)
    assertEquals("Last backup: Never", tvLastBackup.text.toString())

    val tvVersion = activity.findViewById<TextView>(R.id.tv_settings_version_backup)
    assertTrue(tvVersion.text.toString().startsWith("V"))

    // Scope card interactions
    activity.findViewById<View>(R.id.card_scope_attendance).performClick()
    activity.findViewById<View>(R.id.card_scope_notes).performClick()
    activity.findViewById<View>(R.id.card_scope_full).performClick()

    // Haptic toggle persistence
    val hapticSwitch = activity.findViewById<MaterialSwitch>(R.id.switch_haptic_feedback)
    assertTrue(hapticSwitch.isChecked)
    hapticSwitch.isChecked = false
    assertFalse(ThemeHelper.isHapticEnabled(context))
    hapticSwitch.isChecked = true
    assertTrue(ThemeHelper.isHapticEnabled(context))

    // Integrity check execution
    val tvIntegrity = activity.findViewById<TextView>(R.id.tv_integrity_status)
    tvIntegrity.text = "Pending"
    activity.findViewById<View>(R.id.btn_db_integrity_check).performClick()

    val deadline = System.currentTimeMillis() + 3000L
    while (System.currentTimeMillis() < deadline && tvIntegrity.text.toString() == "Pending") {
      ShadowLooper.idleMainLooper()
      Thread.sleep(25)
    }
    assertEquals("Pass", tvIntegrity.text.toString())
  }
}
