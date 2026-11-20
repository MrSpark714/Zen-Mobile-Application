package com.example

import com.example.model.UserProfile
import org.junit.Assert.*
import org.junit.Test

/**
 * Local unit tests for ZEN domain models and utilities.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun userProfile_initialFallbackAndTrimming() {
    val profile = UserProfile()
    assertEquals("", profile.initial)
    assertFalse(profile.hasAnyInfo())

    profile.fullName = "  sohaib khan  "
    assertEquals("sohaib khan", profile.fullName)
    assertEquals("S", profile.initial)
    assertTrue(profile.hasAnyInfo())
  }

  @Test
  fun userProfile_academicSummaryAndTargetAttendance() {
    val profile = UserProfile()
    assertEquals(75, profile.effectiveTargetPercent)

    profile.currentSemester = "Semester 4"
    profile.mainDegreeCourse = "Bachelor of Cyber Security"
    profile.universityName = "FAST NUCES"
    profile.targetAttendancePercent = 80

    assertEquals("Semester 4 • Bachelor of Cyber Security • FAST NUCES", profile.academicSummaryLine)
    assertEquals(80, profile.effectiveTargetPercent)

    profile.targetAttendancePercent = 150
    assertEquals(75, profile.effectiveTargetPercent)
  }
}
