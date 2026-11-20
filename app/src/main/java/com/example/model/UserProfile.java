package com.example.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local-only encrypted student/user profile stored privately on-device.
 */
public class UserProfile implements Serializable {

    @SerializedName("full_name")
    private String fullName = "";

    @SerializedName("university_name")
    private String universityName = "";

    @SerializedName("current_semester")
    private String currentSemester = "";

    @SerializedName("semester_start_millis")
    private long semesterStartDateMillis = 0L;

    @SerializedName("semester_end_millis")
    private long semesterEndDateMillis = 0L;

    @SerializedName("main_degree_course")
    private String mainDegreeCourse = "";

    @SerializedName("current_course_focus")
    private String currentCourseFocus = "";

    @SerializedName("department")
    private String department = "";

    @SerializedName("student_id")
    private String studentId = "";

    @SerializedName("campus_section")
    private String campusSection = "";

    @SerializedName("target_attendance_percent")
    private int targetAttendancePercent = 0; // 0 means default 75%

    public UserProfile() {
    }

    public String getFullName() {
        return fullName != null ? fullName.trim() : "";
    }

    public void setFullName(String fullName) {
        this.fullName = fullName != null ? fullName.trim() : "";
    }

    public String getUniversityName() {
        return universityName != null ? universityName.trim() : "";
    }

    public void setUniversityName(String universityName) {
        this.universityName = universityName != null ? universityName.trim() : "";
    }

    public String getCurrentSemester() {
        return currentSemester != null ? currentSemester.trim() : "";
    }

    public void setCurrentSemester(String currentSemester) {
        this.currentSemester = currentSemester != null ? currentSemester.trim() : "";
    }

    public long getSemesterStartDateMillis() {
        return semesterStartDateMillis;
    }

    public void setSemesterStartDateMillis(long semesterStartDateMillis) {
        this.semesterStartDateMillis = semesterStartDateMillis;
    }

    public long getSemesterEndDateMillis() {
        return semesterEndDateMillis;
    }

    public void setSemesterEndDateMillis(long semesterEndDateMillis) {
        this.semesterEndDateMillis = semesterEndDateMillis;
    }

    public String getMainDegreeCourse() {
        return mainDegreeCourse != null ? mainDegreeCourse.trim() : "";
    }

    public void setMainDegreeCourse(String mainDegreeCourse) {
        this.mainDegreeCourse = mainDegreeCourse != null ? mainDegreeCourse.trim() : "";
    }

    public String getCurrentCourseFocus() {
        return currentCourseFocus != null ? currentCourseFocus.trim() : "";
    }

    public void setCurrentCourseFocus(String currentCourseFocus) {
        this.currentCourseFocus = currentCourseFocus != null ? currentCourseFocus.trim() : "";
    }

    public String getDepartment() {
        return department != null ? department.trim() : "";
    }

    public void setDepartment(String department) {
        this.department = department != null ? department.trim() : "";
    }

    public String getStudentId() {
        return studentId != null ? studentId.trim() : "";
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId != null ? studentId.trim() : "";
    }

    public String getCampusSection() {
        return campusSection != null ? campusSection.trim() : "";
    }

    public void setCampusSection(String campusSection) {
        this.campusSection = campusSection != null ? campusSection.trim() : "";
    }

    public int getTargetAttendancePercent() {
        return targetAttendancePercent;
    }

    public void setTargetAttendancePercent(int targetAttendancePercent) {
        this.targetAttendancePercent = targetAttendancePercent;
    }

    public int getEffectiveTargetPercent() {
        return (targetAttendancePercent >= 1 && targetAttendancePercent <= 100)
                ? targetAttendancePercent
                : 75;
    }

    public String getInitial() {
        String name = getFullName();
        if (name.isEmpty()) {
            return "";
        }
        return name.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    public String getAcademicSummaryLine() {
        List<String> parts = new ArrayList<>();
        if (!getCurrentSemester().isEmpty()) {
            parts.add(getCurrentSemester());
        }
        if (!getMainDegreeCourse().isEmpty()) {
            parts.add(getMainDegreeCourse());
        } else if (!getCurrentCourseFocus().isEmpty()) {
            parts.add(getCurrentCourseFocus());
        }
        if (!getUniversityName().isEmpty()) {
            parts.add(getUniversityName());
        }
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(" • ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public boolean hasAnyInfo() {
        return !getFullName().isEmpty()
                || !getUniversityName().isEmpty()
                || !getCurrentSemester().isEmpty()
                || semesterStartDateMillis > 0
                || semesterEndDateMillis > 0
                || !getMainDegreeCourse().isEmpty()
                || !getCurrentCourseFocus().isEmpty()
                || !getDepartment().isEmpty()
                || !getStudentId().isEmpty()
                || !getCampusSection().isEmpty()
                || targetAttendancePercent > 0;
    }
}
