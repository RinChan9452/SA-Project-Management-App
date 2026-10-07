package com.projectsa.common;

import static com.projectsa.TestData.employee;
import static com.projectsa.TestData.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectsa.employee.Employee;
import com.projectsa.employee.EmployeeRepository;
import com.projectsa.employee.Role;
import jakarta.servlet.RequestDispatcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Errors without their own page (400, 500, ...) get the app's error.html, never a stack trace; the 413 page names
 * the upload limits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorPageTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EmployeeRepository employees;

    Employee pm;

    @BeforeEach
    void setUp() {
        pm = employee(employees, "Max Pm", Role.PM);
    }

    /** What the servlet container does after an error: forward to /error with these attributes. */
    MockHttpServletRequestBuilder errorForward(int status, Exception exception) {
        return get("/error").accept(MediaType.TEXT_HTML).with(user(user(pm)))
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/projects/abc")
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, exception);
    }

    @Test
    void badRequestShowsFriendlyPage() throws Exception {
        mvc.perform(errorForward(400, new IllegalArgumentException("For input string: abc")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString("Request not valid")))
                .andExpect(content().string(Matchers.containsString("Back to dashboard")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Whitelabel"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("IllegalArgumentException"))));
    }

    @Test
    void serverErrorShowsFriendlyPageWithoutDetails() throws Exception {
        mvc.perform(errorForward(500, new IllegalStateException("secret internal detail")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(Matchers.containsString("Something went wrong")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Whitelabel"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret internal detail"))));
    }

    /** UC06 files that are each allowed can be too large together, so the page must not blame a single file. */
    @Test
    void tooLargeUploadNamesBothLimits() throws Exception {
        mvc.perform(errorForward(413, new IllegalStateException("upload too large")))
                .andExpect(status().is(413))
                .andExpect(content().string(Matchers.containsString("File too large")))
                .andExpect(content().string(Matchers.containsString("at most 50 MB")))
                .andExpect(content().string(Matchers.containsString("at most 110 MB")));
    }
}
