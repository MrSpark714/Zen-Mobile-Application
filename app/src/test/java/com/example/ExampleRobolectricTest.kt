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

  @Test
  fun `bottom nav is hidden on weekly timetable and analytics screens and analytics shows redesigned header`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("zen_prefs", Context.MODE_PRIVATE)
      .edit()
      .putBoolean(OnboardingActivity.PREF_KEY_MIGRATION, false)
      .commit()
    SecureProfileStore.markProfileSetupSeen(context)

    val controller = Robolectric.buildActivity(MainActivity::class.java)
      .create()
      .start()
      .resume()
      .visible()
    val activity = controller.get()
    ShadowLooper.idleMainLooper()

    val root = activity.findViewById<View>(android.R.id.content)
    root.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
    )
    root.layout(0, 0, 1080, 1920)
    ShadowLooper.idleMainLooper()

    val bottomNav = activity.findViewById<View>(R.id.bottom_nav_container)
    assertEquals(View.VISIBLE, bottomNav.visibility)

    // Open Analytics via Overall Standing card
    val cardStanding = activity.findViewById<View>(R.id.card_overall_standing)
    assertNotNull(cardStanding)
    cardStanding.performClick()
    ShadowLooper.idleMainLooper()

    assertEquals(View.GONE, bottomNav.visibility)
    val viewAnalytics = activity.findViewById<View>(R.id.view_analytics)
    assertEquals(View.VISIBLE, viewAnalytics.visibility)
    val heading = activity.findViewById<TextView>(R.id.tv_analytics_heading)
    assertEquals("Attendance Analytics", heading.text.toString())
    assertNotNull(activity.findViewById<View>(R.id.filter_analytics_all))
    assertNotNull(activity.findViewById<View>(R.id.filter_analytics_theory))
    assertNotNull(activity.findViewById<View>(R.id.filter_analytics_lab))
    assertEquals("75%", activity.findViewById<TextView>(R.id.tv_analytics_min_req_badge).text.toString())

    // Switch to Weekly Timetable from Analytics
    activity.findViewById<View>(R.id.btn_analytics_to_schedule).performClick()
    ShadowLooper.idleMainLooper()

    assertEquals(View.GONE, bottomNav.visibility)
    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.view_weekly_schedule).visibility)

    val dayAll = activity.findViewById<TextView>(R.id.filter_day_all)
    val dayMon = activity.findViewById<TextView>(R.id.filter_day_mon)
    assertEquals(androidx.core.content.ContextCompat.getColor(activity, R.color.zen_on_accent), dayAll.currentTextColor)
    assertEquals(androidx.core.content.ContextCompat.getColor(activity, R.color.zen_text_secondary), dayMon.currentTextColor)

    dayMon.performClick()
    ShadowLooper.idleMainLooper()
    assertEquals(androidx.core.content.ContextCompat.getColor(activity, R.color.zen_text_secondary), dayAll.currentTextColor)
    assertEquals(androidx.core.content.ContextCompat.getColor(activity, R.color.zen_on_accent), dayMon.currentTextColor)

    // Return to Dashboard -> Bottom nav reappears
    activity.findViewById<View>(R.id.btn_back_from_schedule).performClick()
    ShadowLooper.idleMainLooper()

    assertEquals(View.VISIBLE, bottomNav.visibility)
    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.view_today_classes).visibility)
  }

  @Test
  fun `delete modals and clear local profile modal use redesigned layout and headings`() {
    val activity = Robolectric.buildActivity(ProfileSetupActivity::class.java)
      .create()
      .start()
      .resume()
      .get()
    ShadowLooper.idleMainLooper()

    activity.findViewById<android.widget.EditText>(R.id.et_profile_name).setText("Sohaib")
    activity.findViewById<android.widget.EditText>(R.id.et_profile_semester).setText("Semester 5")
    activity.findViewById<android.widget.EditText>(R.id.et_profile_degree).setText("BS Computer Science")

    val btnClear = activity.findViewById<View>(R.id.btn_profile_clear)
    btnClear.visibility = View.VISIBLE
    btnClear.performClick()
    ShadowLooper.idleMainLooper()

    val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
    assertNotNull(dialog)
    assertEquals("Clear Local Profile", dialog.findViewById<TextView>(R.id.tv_delete_popup_title).text.toString())
    assertEquals("Sohaib", dialog.findViewById<TextView>(R.id.tv_delete_item_preview).text.toString())
    assertEquals("Encrypted", dialog.findViewById<TextView>(R.id.tv_delete_preview_badge).text.toString())
    dialog.findViewById<View>(R.id.btn_popup_delete_confirm).performClick()
    ShadowLooper.idleMainLooper()

    assertEquals("", activity.findViewById<android.widget.EditText>(R.id.et_profile_name).text.toString())
    assertEquals(View.GONE, btnClear.visibility)

    var noteDeleted = false
    val note = com.example.model.Note("App Feature Improvements", "Line 1\nLine 2\nLine 3", 1760400000000L, true, "", "")
    com.example.util.DeleteConfirmationDialog.showForNote(activity, note, null, null, false, 0L) {
      noteDeleted = true
    }
    ShadowLooper.idleMainLooper()

    val noteDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
    assertNotNull(noteDialog)
    assertEquals("Delete Note?", noteDialog.findViewById<TextView>(R.id.tv_delete_popup_title).text.toString())
    assertEquals("App Feature Improvements", noteDialog.findViewById<TextView>(R.id.tv_delete_item_preview).text.toString())
    assertEquals("★ Important", noteDialog.findViewById<TextView>(R.id.tv_delete_preview_badge).text.toString())
    assertEquals("3 items in note", noteDialog.findViewById<TextView>(R.id.tv_delete_preview_meta_left).text.toString())
    noteDialog.findViewById<View>(R.id.btn_popup_delete_confirm).performClick()
    ShadowLooper.idleMainLooper()
    assertTrue(noteDeleted)

    var taskDeleted = false
    val task = com.example.model.Task("Complete HCI Diagram Assignment", false, 1760400000000L)
    com.example.util.DeleteConfirmationDialog.showForTask(activity, task) {
      taskDeleted = true
    }
    ShadowLooper.idleMainLooper()

    val taskDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
    assertEquals("Delete Task?", taskDialog.findViewById<TextView>(R.id.tv_delete_popup_title).text.toString())
    assertEquals("Complete HCI Diagram Assignment", taskDialog.findViewById<TextView>(R.id.tv_delete_item_preview).text.toString())
    taskDialog.findViewById<View>(R.id.btn_popup_delete_confirm).performClick()
    ShadowLooper.idleMainLooper()
    assertTrue(taskDeleted)

    var classDeleted = false
    val schedule = com.example.model.ClassSchedule("Monday", "Operating System", "08:30 AM", "10:20 AM", 2, "Theory", "A3")
    com.example.util.DeleteConfirmationDialog.showForSchedule(activity, schedule) {
      classDeleted = true
    }
    ShadowLooper.idleMainLooper()

    val classDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
    assertEquals("Remove Class?", classDialog.findViewById<TextView>(R.id.tv_delete_popup_title).text.toString())
    assertEquals("Operating System", classDialog.findViewById<TextView>(R.id.tv_delete_item_preview).text.toString())
    assertEquals("Theory", classDialog.findViewById<TextView>(R.id.tv_delete_preview_badge).text.toString())
    classDialog.findViewById<View>(R.id.btn_popup_delete_confirm).performClick()
    ShadowLooper.idleMainLooper()
    assertTrue(classDeleted)
  }

  @Test
  fun `note editor date picker popup renders custom dark calendar and applies selected date`() {
    val activity = Robolectric.buildActivity(NoteEditorActivity::class.java)
      .create()
      .start()
      .resume()
      .get()
    ShadowLooper.idleMainLooper()

    activity.findViewById<View>(R.id.btn_insert_date).performClick()
    ShadowLooper.idleMainLooper()

    val dateDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
    assertNotNull(dateDialog)
    val rows = dateDialog.findViewById<android.widget.LinearLayout>(R.id.container_calendar_rows)
    assertNotNull(rows)
    assertEquals(6, rows.childCount)

    val presetTomorrow = dateDialog.findViewById<TextView>(R.id.preset_tomorrow)
    presetTomorrow.performClick()
    ShadowLooper.idleMainLooper()
    assertEquals(androidx.core.content.ContextCompat.getColor(activity, R.color.zen_on_accent), presetTomorrow.currentTextColor)

    dateDialog.findViewById<View>(R.id.opt_attach_badge).performClick()
    dateDialog.findViewById<View>(R.id.btn_popup_apply).performClick()
    ShadowLooper.idleMainLooper()

    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layout_target_date_badge).visibility)
  }

  @Test
  fun `backup confirm restore dialog renders redesigned inventory grid and actions`() {
    val activity = Robolectric.buildActivity(BackupActivity::class.java)
      .create()
      .start()
      .resume()
      .get()
    ShadowLooper.idleMainLooper()

    val notes = listOf(
      com.example.model.Note("Note 1", "Body", 100L),
      com.example.model.Note("Note 2", "Body", 200L)
    )
    val schedules = listOf(
      com.example.model.ClassSchedule("Monday", "Algorithms", "09:00 AM", "10:00 AM", 3, "Theory", "A1")
    )
    val records = listOf(
      com.example.model.AttendanceHistory("Algorithms", 3, "Theory", 100L, "Present")
    )
    val payload = com.example.model.ZenBackupPayload(
      com.example.model.ZenBackupPayload.TYPE_FULL,
      notes,
      emptyList(),
      schedules,
      records
    )

    activity.showImportConfirmationDialog(payload)
    ShadowLooper.idleMainLooper()

    val restoreDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
    assertNotNull(restoreDialog)
    assertEquals("Confirm Restore", restoreDialog.findViewById<TextView>(R.id.tv_restore_title).text.toString())
    assertEquals("Encrypted Backup Package", restoreDialog.findViewById<TextView>(R.id.tv_restore_subtitle).text.toString())
    assertEquals("2", restoreDialog.findViewById<TextView>(R.id.tv_restore_notes_count).text.toString())
    assertEquals("0", restoreDialog.findViewById<TextView>(R.id.tv_restore_tasks_count).text.toString())
    assertEquals("1", restoreDialog.findViewById<TextView>(R.id.tv_restore_schedules_count).text.toString())
    assertEquals("1", restoreDialog.findViewById<TextView>(R.id.tv_restore_attendance_count).text.toString())
    val btnConfirm = restoreDialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_restore_confirm)
    val btnCancel = restoreDialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_restore_cancel)
    assertNotNull(btnConfirm)
    assertNotNull(btnCancel)
    assertEquals(1, btnConfirm.maxLines)
    assertEquals(btnCancel.height, btnConfirm.height)
    btnCancel.performClick()
    ShadowLooper.idleMainLooper()
    assertFalse(restoreDialog.isShowing)
  }
}
