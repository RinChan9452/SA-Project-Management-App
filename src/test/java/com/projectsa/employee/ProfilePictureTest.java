package com.projectsa.employee;

import static com.projectsa.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectsa.common.FieldValidationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * F4 picture replacement on disk. Not {@code @Transactional}: the old file is only deleted after the change is
 * committed, so this test really commits and cleans up after itself.
 */
@SpringBootTest
class ProfilePictureTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 2};

    @Autowired
    ProfileService profileService;
    @Autowired
    EmployeeRepository employees;
    @Value("${app.upload-dir}")
    String uploadDir;

    Employee pm;

    @BeforeEach
    void setUp() {
        pm = employees.save(new Employee("Pic Pm", "pic-" + UUID.randomUUID() + "@example.com", "x", Role.PM));
    }

    @AfterEach
    void cleanUp() throws Exception {
        String path = employees.findById(pm.getId()).orElseThrow().getProfilePicturePath();
        if (path != null) {
            Files.deleteIfExists(file(path));
        }
        employees.deleteById(pm.getId());
    }

    Path file(String relative) {
        return Path.of(uploadDir).resolve(relative);
    }

    @Test
    void newPictureReplacesTheOldFile() throws Exception {
        profileService.changePicture(user(pm), new MockMultipartFile("picture", "a.png", "image/png", PNG));
        String first = employees.findById(pm.getId()).orElseThrow().getProfilePicturePath();
        assertThat(file(first)).exists().hasBinaryContent(PNG);

        profileService.changePicture(user(pm), new MockMultipartFile("picture", "b.jpg", "image/jpeg", JPG));
        String second = employees.findById(pm.getId()).orElseThrow().getProfilePicturePath();
        assertThat(second).startsWith("avatars/").endsWith(".jpg").isNotEqualTo(first);
        assertThat(file(second)).exists().hasBinaryContent(JPG);
        assertThat(file(first)).doesNotExist();
    }

    @Test
    void rejectedPictureKeepsTheOldOne() throws Exception {
        profileService.changePicture(user(pm), new MockMultipartFile("picture", "a.png", "image/png", PNG));
        String first = employees.findById(pm.getId()).orElseThrow().getProfilePicturePath();

        assertThatThrownBy(() -> profileService.changePicture(user(pm),
                new MockMultipartFile("picture", "doc.png", "image/png", "%PDF-1.4".getBytes())))
                .isInstanceOf(FieldValidationException.class);
        assertThat(employees.findById(pm.getId()).orElseThrow().getProfilePicturePath()).isEqualTo(first);
        assertThat(file(first)).exists();
    }
}
