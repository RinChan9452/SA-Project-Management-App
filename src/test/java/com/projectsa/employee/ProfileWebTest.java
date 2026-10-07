package com.projectsa.employee;

import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectsa.auth.CurrentUser;
import java.util.Arrays;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** F4 Edit my profile over HTTP. Picture replacement on disk is tested in {@link ProfilePictureTest}. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileWebTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @Autowired
    MockMvc mvc;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    PasswordEncoder passwordEncoder;

    Employee tech;
    Employee pm;

    @BeforeEach
    void setUp() {
        tech = new Employee("Tia Tech", "tia@example.com", passwordEncoder.encode("oldpassword"), Role.TECH);
        tech.addSkill("CCNA");
        tech.addSkill("Linux");
        tech = employees.saveAndFlush(tech);
        pm = employees.saveAndFlush(new Employee("Max Pm", "max@example.com", passwordEncoder.encode("oldpassword"), Role.PM));
    }

    Employee reload(Employee e) {
        return employees.findWithSkills(e.getId()).orElseThrow();
    }

    @Test
    void profileNeedsLogin() throws Exception {
        mvc.perform(get("/profile")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/employees/{id}/avatar", tech.getId())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void pageShowsMyDataAndSkillsOnlyForTech() throws Exception {
        mvc.perform(get("/profile").with(user(user(tech))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("tia@example.com")))
                .andExpect(content().string(Matchers.containsString("Please Assign Your Skills :")))
                .andExpect(content().string(Matchers.containsString("Linux")));
        mvc.perform(get("/profile").with(user(user(pm))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("My Skills"))));
    }

    @Test
    void changeNameUpdatesTheLoggedInUserToo() throws Exception {
        MvcResult result = mvc.perform(post("/profile/name").param("name", "  Tia Engineer ")
                        .with(csrf()).with(user(user(tech))))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Your name was changed."))
                .andReturn();
        assertThat(reload(tech).getName()).isEqualTo("Tia Engineer");
        SecurityContext saved = (SecurityContext) result.getRequest().getSession().getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(((CurrentUser) saved.getAuthentication().getPrincipal()).getName()).isEqualTo("Tia Engineer");
    }

    @Test
    void blankNameIsRejected() throws Exception {
        mvc.perform(post("/profile/name").param("name", "  ").with(csrf()).with(user(user(tech))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("nameForm", "name"));
        assertThat(reload(tech).getName()).isEqualTo("Tia Tech");
    }

    @Test
    void roleEmailAndIdCannotBeChangedByTamperingTheForm() throws Exception {
        mvc.perform(post("/profile/name").param("name", "New Name").param("role", "SALE")
                        .param("email", "boss@example.com").param("id", pm.getId().toString())
                        .param("employeeId", pm.getId().toString())
                        .with(csrf()).with(user(user(tech))))
                .andExpect(redirectedUrl("/profile"));
        Employee after = reload(tech);
        assertThat(after.getName()).isEqualTo("New Name");
        assertThat(after.getRole()).isEqualTo(Role.TECH);
        assertThat(after.getEmail()).isEqualTo("tia@example.com");
        assertThat(reload(pm).getName()).isEqualTo("Max Pm");
    }

    @Test
    void changePasswordStoresBcryptHash() throws Exception {
        mvc.perform(post("/profile/password").param("currentPassword", "oldpassword")
                        .param("newPassword", "newpassword1").param("confirmPassword", "newpassword1")
                        .with(csrf()).with(user(user(tech))))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Your password was changed."));
        String hash = reload(tech).getPasswordHash();
        assertThat(hash).startsWith("$2");
        assertThat(passwordEncoder.matches("newpassword1", hash)).isTrue();
    }

    @Test
    void passwordErrorsAllShowInOneRound() throws Exception {
        mvc.perform(post("/profile/password").param("currentPassword", "wrong")
                        .param("newPassword", "short").param("confirmPassword", "other")
                        .with(csrf()).with(user(user(tech))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordForm", "currentPassword", "newPassword", "confirmPassword"))
                .andExpect(content().string(Matchers.containsString("Current password is incorrect")))
                .andExpect(content().string(Matchers.containsString("Passwords do not match")))
                // typed passwords are never sent back
                .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"wrong\""))));
        assertThat(passwordEncoder.matches("oldpassword", reload(tech).getPasswordHash())).isTrue();
    }

    /** BCrypt refuses more than 72 bytes; 30 Thai letters are only 30 characters but 90 bytes. */
    @Test
    void newPasswordOver72BytesIsAFormErrorNotACrash() throws Exception {
        String thai30 = "ก".repeat(30);
        mvc.perform(post("/profile/password").param("currentPassword", "oldpassword")
                        .param("newPassword", thai30).param("confirmPassword", thai30)
                        .with(csrf()).with(user(user(tech))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordForm", "newPassword"))
                .andExpect(content().string(Matchers.containsString("Password is too long")));
        assertThat(passwordEncoder.matches("oldpassword", reload(tech).getPasswordHash())).isTrue();
    }

    @Test
    void missingCurrentPasswordIsRejected() throws Exception {
        mvc.perform(post("/profile/password").param("newPassword", "newpassword1").param("confirmPassword", "newpassword1")
                        .with(csrf()).with(user(user(tech))))
                .andExpect(model().attributeHasFieldErrors("passwordForm", "currentPassword"));
        assertThat(passwordEncoder.matches("oldpassword", reload(tech).getPasswordHash())).isTrue();
    }

    @Test
    void pictureMustBeRealJpgOrPngUpTo2Mb() throws Exception {
        MockMultipartFile fakePng = new MockMultipartFile("picture", "me.png", "image/png", "not an image".getBytes());
        mvc.perform(multipart("/profile/picture").file(fakePng).with(csrf()).with(user(user(tech))))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("pictureForm", "picture"))
                .andExpect(content().string(Matchers.containsString("Only PNG, JPG files are allowed")));

        byte[] big = Arrays.copyOf(PNG, (int) ProfileService.MAX_PICTURE_BYTES + 1);
        mvc.perform(multipart("/profile/picture").file(new MockMultipartFile("picture", "big.png", "image/png", big))
                        .with(csrf()).with(user(user(tech))))
                .andExpect(model().attributeHasFieldErrors("pictureForm", "picture"))
                .andExpect(content().string(Matchers.containsString("too large (max 2 MB)")));

        mvc.perform(multipart("/profile/picture").with(csrf()).with(user(user(tech))))
                .andExpect(model().attributeHasFieldErrors("pictureForm", "picture"));
        assertThat(reload(tech).getProfilePicturePath()).isNull();
    }

    @Test
    void pictureIsSavedAndShownToEveryone() throws Exception {
        mvc.perform(multipart("/profile/picture").file(new MockMultipartFile("picture", "C:\\me.png", "image/png", PNG))
                        .with(csrf()).with(user(user(tech))))
                .andExpect(redirectedUrl("/profile"));
        String path = reload(tech).getProfilePicturePath();
        assertThat(path).startsWith("avatars/").endsWith(".png").doesNotContain("me");

        mvc.perform(get("/employees/{id}/avatar", tech.getId()).with(user(user(pm))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        mvc.perform(get("/profile").with(user(user(reload(tech)))))
                .andExpect(content().string(Matchers.containsString("/employees/" + tech.getId() + "/avatar?v=")));
    }

    @Test
    void noPictureMeansDefaultAvatarAnd404() throws Exception {
        mvc.perform(get("/employees/{id}/avatar", pm.getId()).with(user(user(tech)))).andExpect(status().isNotFound());
        mvc.perform(get("/employees/{id}/avatar", 999_999).with(user(user(tech)))).andExpect(status().isNotFound());
        mvc.perform(get("/profile").with(user(user(pm))))
                .andExpect(content().string(Matchers.containsString("bi-person-circle")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/avatar?v="))));
    }

    @Test
    void techAddsSkillsWithoutDuplicates() throws Exception {
        mvc.perform(post("/profile/skills").param("skill", " Fortinet NSE4 ").with(csrf()).with(user(user(tech))))
                .andExpect(redirectedUrl("/profile"));
        assertThat(reload(tech).getSkills()).extracting(EngineerSkill::getSkill).contains("Fortinet NSE4");

        mvc.perform(post("/profile/skills").param("skill", "ccna").with(csrf()).with(user(user(tech))))
                .andExpect(model().attributeHasFieldErrors("skillForm", "skill"))
                .andExpect(content().string(Matchers.containsString("You already have this skill")));
        mvc.perform(post("/profile/skills").param("skill", "   ").with(csrf()).with(user(user(tech))))
                .andExpect(model().attributeHasFieldErrors("skillForm", "skill"));
        mvc.perform(post("/profile/skills").param("skill", "x".repeat(101)).with(csrf()).with(user(user(tech))))
                .andExpect(model().attributeHasFieldErrors("skillForm", "skill"));
        assertThat(reload(tech).getSkills()).hasSize(3);
    }

    @Test
    void techRemovesOwnSkillButKeepsAtLeastOne() throws Exception {
        Long linux = reload(tech).getSkills().stream().filter(s -> s.getSkill().equals("Linux")).findFirst().orElseThrow().getId();
        mvc.perform(post("/profile/skills/{id}/remove", linux).with(csrf()).with(user(user(tech))))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Skill removed: Linux"));
        Long last = reload(tech).getSkills().getFirst().getId();
        mvc.perform(post("/profile/skills/{id}/remove", last).with(csrf()).with(user(user(tech))))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error", "You must keep at least one skill"));
        assertThat(reload(tech).getSkills()).extracting(EngineerSkill::getSkill).containsExactly("CCNA");
    }

    @Test
    void cannotRemoveSomeoneElsesSkill() throws Exception {
        Employee other = new Employee("Ted Tech", "ted@example.com", "x", Role.TECH);
        other.addSkill("Docker");
        other.addSkill("K8s");
        other = employees.saveAndFlush(other);
        Long theirs = other.getSkills().getFirst().getId();
        mvc.perform(post("/profile/skills/{id}/remove", theirs).with(csrf()).with(user(user(tech))))
                .andExpect(status().isNotFound());
        assertThat(reload(other).getSkills()).hasSize(2);
    }

    @Test
    void nonTechHasNoSkills() throws Exception {
        mvc.perform(post("/profile/skills").param("skill", "CCNA").with(csrf()).with(user(user(pm))))
                .andExpect(status().isForbidden());
        Long techSkill = reload(tech).getSkills().getFirst().getId();
        mvc.perform(post("/profile/skills/{id}/remove", techSkill).with(csrf()).with(user(user(pm))))
                .andExpect(status().isForbidden());
        assertThat(reload(pm).getSkills()).isEmpty();
    }

    @Test
    void postsNeedCsrf() throws Exception {
        mvc.perform(post("/profile/name").param("name", "X").with(user(user(tech)))).andExpect(status().isForbidden());
        assertThat(reload(tech).getName()).isEqualTo("Tia Tech");
    }
}
