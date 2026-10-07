package com.projectsa.project;

import java.util.ArrayList;
import java.util.List;

/** UC04 form: the ticked engineers, plus the skill filter so it is kept after an error. */
public class AssignForm {

    private List<Long> engineerIds = new ArrayList<>();

    private String skill;

    public List<Long> getEngineerIds() {
        return engineerIds;
    }

    public void setEngineerIds(List<Long> engineerIds) {
        this.engineerIds = engineerIds == null ? new ArrayList<>() : engineerIds;
    }

    public String getSkill() {
        return skill;
    }

    public void setSkill(String skill) {
        this.skill = skill;
    }
}
