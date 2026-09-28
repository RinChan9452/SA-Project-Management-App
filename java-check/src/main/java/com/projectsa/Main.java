package com.projectsa;

public class Main {
    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version") + " is working!");
        System.out.println("ทดสอบภาษาไทย (Thai text check)");

        ProjectStatus status = ProjectStatus.NEW_PROJECT;
        status = status.moveTo(ProjectStatus.WAITING_FOR_ASSIGN_ENGINEER);
        status = status.moveTo(ProjectStatus.WORKING);
        status = status.moveTo(ProjectStatus.TESTING);
        status = status.moveTo(ProjectStatus.FINISH);
        System.out.println("Project reached: " + status);
    }
}
