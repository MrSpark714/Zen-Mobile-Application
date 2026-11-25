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

  @Test
  fun `section schedule sync verifies encrypted packages, enforces cooldown and 304, rejects tampering, and smart-merges while keeping manual classes and attendance`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.database.AppDatabase.getInstance(context)

    // Seed 1 personal manual class + 1 past attendance record
    com.example.database.AppDatabase.databaseWriteExecutor.submit {
      db.classScheduleDao().deleteAllSchedules()
      db.attendanceHistoryDao().deleteAllHistory()
      val manualClass = com.example.model.ClassSchedule(
        "Wednesday", "Personal Elective", "02:00 PM", "03:00 PM", 2, "Theory", "E-101"
      )
      manualClass.source = com.example.model.ClassSchedule.SOURCE_MANUAL
      db.classScheduleDao().insert(manualClass)
      db.attendanceHistoryDao().insert(
        com.example.model.AttendanceHistory("Advanced Algorithms", 3, "Theory", 1000L, "Present")
      )
    }.get()

    // Generate ECDSA P-256 keypair in memory for test
    val kpg = java.security.KeyPairGenerator.getInstance("EC")
    kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
    val keyPair = kpg.generateKeyPair()
    val pubB64 = android.util.Base64.encodeToString(keyPair.public.encoded, android.util.Base64.NO_WRAP)

    assertTrue(com.example.util.ScheduleSyncPreferences.setPublicKeyOverride(context, pubB64))
    assertFalse(com.example.util.ScheduleSyncPreferences.setRepoBaseUrl(context, "http://insecure.example.com/"))
    assertFalse(com.example.util.ScheduleSyncPreferences.setRepoBaseUrl(context, "https://localhost/schedules/"))
    assertTrue(com.example.util.ScheduleSyncPreferences.setRepoBaseUrl(context, "https://raw.githubusercontent.com/test/zen-schedules/main/schedules/"))

    val profile = com.example.model.UserProfile().apply {
      universityName = "COMSATS University"
      department = "CS" // tests alias/normalization matching "Computer Science"
      currentSemester = "Semester 5" // tests normalization matching "5"
      campusSection = "Sec A" // tests normalization matching "A"
    }

    val gson = com.google.gson.GsonBuilder().disableHtmlEscaping().create()

    fun buildSignedAssets(version: String, roomForOs: String, includeDistributed: Boolean): Pair<String, String> {
      val pkg = com.example.model.ScheduleSyncModels.SectionSchedulePackage().apply {
        sectionId = "cu_cs_sem5_a"
        institute = "COMSATS University"
        department = "Computer Science"
        semester = "5"
        section = "A"
        this.version = version
        changelog = "Weekly section update $version"
        classes = mutableListOf(
          com.example.model.ScheduleSyncModels.SyncedClassEntry().apply {
            slotKey = "mon_cs501_theory"
            dayOfWeek = "Monday"
            subjectName = "Advanced Algorithms"
            startTime = "09:00 AM"
            endTime = "10:30 AM"
            creditHours = 3
            classType = "Theory"
            roomNumber = "C-201"
          },
          com.example.model.ScheduleSyncModels.SyncedClassEntry().apply {
            slotKey = "mon_cs502_theory"
            dayOfWeek = "Monday"
            subjectName = "Operating Systems"
            startTime = "11:00 AM"
            endTime = "12:30 PM"
            creditHours = 4
            classType = "Theory"
            roomNumber = roomForOs
          }
        ).apply {
          if (includeDistributed) {
            add(com.example.model.ScheduleSyncModels.SyncedClassEntry().apply {
              slotKey = "tue_cs503_theory"
              dayOfWeek = "Tuesday"
              subjectName = "Distributed Systems"
              startTime = "10:00 AM"
              endTime = "11:30 AM"
              creditHours = 3
              classType = "Theory"
              roomNumber = "B-104"
            })
          }
        }
      }

      val envelope = com.example.util.ScheduleCryptoVerifier.createSignedEnvelope(
        pkg, keyPair.private, pubB64, null
      )
      val envelopeJson = gson.toJson(envelope)
      val packageSha = com.example.util.ScheduleCryptoVerifier.sha256Hex(
        envelopeJson.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
      )

      val manifest = com.example.model.ScheduleSyncModels.ScheduleIndexManifest().apply {
        schemaVersion = 1
        repoName = "Test Repo"
        generatedAt = "2026-11-25T00:00:00Z"
        sections = listOf(
          com.example.model.ScheduleSyncModels.SectionIndexEntry().apply {
            sectionId = "cu_cs_sem5_a"
            institute = "COMSATS University"
            department = "Computer Science"
            semester = "5"
            section = "A"
            latestVersion = version
            packagePath = "sections/cu_cs_sem5_a.zensched"
            this.packageSha256 = packageSha
          }
        )
      }
      manifest.signature = com.example.util.ScheduleCryptoVerifier.signCanonical(
        com.example.util.ScheduleCryptoVerifier.buildIndexCanonicalString(manifest),
        keyPair.private
      )
      val indexJson = gson.toJson(manifest)
      return indexJson to envelopeJson
    }

    // 1. V1.0 fetch & smart merge
    val (indexV1, pkgV1) = buildSignedAssets("V1.0", "Lab 2", false)
    val resultV1 = com.example.database.AppDatabase.databaseWriteExecutor.submit<com.example.util.SectionScheduleSyncManager.SyncCheckResult> {
      com.example.util.SectionScheduleSyncManager.checkForScheduleUpdatesWithProfileSync(
        context,
        profile,
        true
      ) { url, _ ->
        if (url.endsWith("index.json")) {
          com.example.util.SectionScheduleSyncManager.HttpResponse(200, indexV1, "\"etag-v1\"")
        } else {
          com.example.util.SectionScheduleSyncManager.HttpResponse(200, pkgV1, "\"pkg-v1\"")
        }
      }
    }.get()

    assertEquals(com.example.util.SectionScheduleSyncManager.SyncOutcomeType.UPDATE_AVAILABLE, resultV1.outcome)
    assertNotNull(resultV1.preview)
    assertEquals(2, resultV1.preview.slotsToInsert.size)

    com.example.database.AppDatabase.databaseWriteExecutor.submit {
      com.example.util.SectionScheduleSyncManager.applyScheduleUpdateSync(context, resultV1.preview)
    }.get()

    val schedulesAfterV1 = com.example.database.AppDatabase.databaseWriteExecutor.submit<List<com.example.model.ClassSchedule>> {
      db.classScheduleDao().allSchedulesSync
    }.get()
    assertEquals(3, schedulesAfterV1.size) // 1 manual + 2 synced
    assertTrue(schedulesAfterV1.any { !it.isSynced && it.subjectName == "Personal Elective" })
    assertTrue(schedulesAfterV1.any { it.isSynced && it.subjectName == "Operating Systems" && it.roomNumber == "Lab 2" })

    // 2. Cooldown blocks automatic check within 6 hours (0 network calls)
    var networkCalls = 0
    val cooldownResult = com.example.database.AppDatabase.databaseWriteExecutor.submit<com.example.util.SectionScheduleSyncManager.SyncCheckResult> {
      com.example.util.SectionScheduleSyncManager.checkForScheduleUpdatesWithProfileSync(
        context,
        profile,
        false
      ) { _, _ ->
        networkCalls++
        com.example.util.SectionScheduleSyncManager.HttpResponse(200, indexV1, "\"etag-v1\"")
      }
    }.get()
    assertEquals(com.example.util.SectionScheduleSyncManager.SyncOutcomeType.COOLDOWN_ACTIVE, cooldownResult.outcome)
    assertEquals(0, networkCalls)

    // 3. After cooldown reset, ETag 304 Not Modified skips body download
    com.example.util.ScheduleSyncPreferences.resetCooldownForProfileChange(context)
    com.example.util.ScheduleSyncPreferences.setIndexETag(context, "\"etag-v1\"")
    val notModifiedResult = com.example.database.AppDatabase.databaseWriteExecutor.submit<com.example.util.SectionScheduleSyncManager.SyncCheckResult> {
      com.example.util.SectionScheduleSyncManager.checkForScheduleUpdatesWithProfileSync(
        context,
        profile,
        false
      ) { _, etag ->
        assertEquals("\"etag-v1\"", etag)
        com.example.util.SectionScheduleSyncManager.HttpResponse(304, null, "\"etag-v1\"")
      }
    }.get()
    assertEquals(com.example.util.SectionScheduleSyncManager.SyncOutcomeType.NOT_MODIFIED_304, notModifiedResult.outcome)

    // 4. Tampered public JSON fails cryptographic verification cleanly
    val (indexV11, pkgV11) = buildSignedAssets("V1.1", "Lab 4", true)
    val tamperedPkg = pkgV11.replace("V1.1", "V9.9")
    val tamperResult = com.example.database.AppDatabase.databaseWriteExecutor.submit<com.example.util.SectionScheduleSyncManager.SyncCheckResult> {
      com.example.util.SectionScheduleSyncManager.checkForScheduleUpdatesWithProfileSync(
        context,
        profile,
        true
      ) { url, _ ->
        if (url.endsWith("index.json")) {
          com.example.util.SectionScheduleSyncManager.HttpResponse(200, indexV11, "\"etag-v11\"")
        } else {
          com.example.util.SectionScheduleSyncManager.HttpResponse(200, tamperedPkg, "\"pkg-tampered\"")
        }
      }
    }.get()
    assertEquals(com.example.util.SectionScheduleSyncManager.SyncOutcomeType.VERIFICATION_FAILED, tamperResult.outcome)

    // 5. Valid V1.1 smart diff merge updates OS room to Lab 4, adds Distributed Systems, preserves Personal Elective & AttendanceHistory
    val resultV11 = com.example.database.AppDatabase.databaseWriteExecutor.submit<com.example.util.SectionScheduleSyncManager.SyncCheckResult> {
      com.example.util.SectionScheduleSyncManager.checkForScheduleUpdatesWithProfileSync(
        context,
        profile,
        true
      ) { url, _ ->
        if (url.endsWith("index.json")) {
          com.example.util.SectionScheduleSyncManager.HttpResponse(200, indexV11, "\"etag-v11\"")
        } else {
          com.example.util.SectionScheduleSyncManager.HttpResponse(200, pkgV11, "\"pkg-v11\"")
        }
      }
    }.get()
    assertEquals(com.example.util.SectionScheduleSyncManager.SyncOutcomeType.UPDATE_AVAILABLE, resultV11.outcome)
    assertEquals(1, resultV11.preview.slotsToInsert.size)
    assertEquals(1, resultV11.preview.slotsToUpdate.size)
    assertEquals(0, resultV11.preview.slotsToDelete.size)

    com.example.database.AppDatabase.databaseWriteExecutor.submit {
      com.example.util.SectionScheduleSyncManager.applyScheduleUpdateSync(context, resultV11.preview)
    }.get()

    val schedulesAfterV11 = com.example.database.AppDatabase.databaseWriteExecutor.submit<List<com.example.model.ClassSchedule>> {
      db.classScheduleDao().allSchedulesSync
    }.get()
    val attendanceAfterV11 = com.example.database.AppDatabase.databaseWriteExecutor.submit<List<com.example.model.AttendanceHistory>> {
      db.attendanceHistoryDao().allRecordsSync
    }.get()

    assertEquals(4, schedulesAfterV11.size) // 1 manual + 3 synced
    assertTrue(schedulesAfterV11.any { !it.isSynced && it.subjectName == "Personal Elective" })
    assertTrue(schedulesAfterV11.any { it.isSynced && it.subjectName == "Operating Systems" && it.roomNumber == "Lab 4" })
    assertTrue(schedulesAfterV11.any { it.isSynced && it.subjectName == "Distributed Systems" })
    assertEquals(1, attendanceAfterV11.size)

    // 6. Verify ScheduleUpdateDialog renders diff counts and single-line actions cleanly
    val activity = Robolectric.buildActivity(BackupActivity::class.java)
      .create()
      .start()
      .resume()
      .get()
    ShadowLooper.idleMainLooper()

    var confirmed = false
    val updateDialog = com.example.util.ScheduleUpdateDialog.show(
      activity,
      resultV11.preview,
      object : com.example.util.ScheduleUpdateDialog.Listener {
        override fun onConfirmUpdate(preview: com.example.model.ScheduleSyncModels.ScheduleUpdatePreview) {
          confirmed = true
        }
        override fun onSkipVersion(preview: com.example.model.ScheduleSyncModels.ScheduleUpdatePreview) {}
      }
    )
    ShadowLooper.idleMainLooper()

    assertEquals("Schedule Update", updateDialog.findViewById<TextView>(R.id.tv_sched_update_title).text.toString())
    assertEquals("V1.0 → V1.1", updateDialog.findViewById<TextView>(R.id.tv_sched_update_version_pill).text.toString())
    assertEquals("1", updateDialog.findViewById<TextView>(R.id.tv_sched_added_count).text.toString())
    assertEquals("1", updateDialog.findViewById<TextView>(R.id.tv_sched_updated_count).text.toString())
    assertEquals("0", updateDialog.findViewById<TextView>(R.id.tv_sched_removed_count).text.toString())
    val btnApply = updateDialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_sched_apply)
    assertEquals(1, btnApply.maxLines)
    btnApply.performClick()
    ShadowLooper.idleMainLooper()
    assertTrue(confirmed)

    // Verify BackupActivity contains new Section Schedule Sync controls
    assertNotNull(activity.findViewById<MaterialSwitch>(R.id.switch_auto_schedule_sync))
    assertNotNull(activity.findViewById<View>(R.id.btn_configure_schedule_repo))
    assertNotNull(activity.findViewById<View>(R.id.btn_check_schedule_sync_now))
  }
}
