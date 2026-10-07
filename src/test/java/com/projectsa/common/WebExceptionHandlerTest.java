package com.projectsa.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;

/** The red messages {@link WebExceptionHandler} shows on the dashboard. */
class WebExceptionHandlerTest {

    /** UC06 files that are each allowed can be too large together, so the message names both limits. */
    @Test
    void tooLargeUploadNamesBothLimits() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        FlashMap flash = new FlashMap();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flash);

        assertThat(new WebExceptionHandler().tooLarge(request)).isEqualTo("redirect:/dashboard");
        assertThat((String) flash.get("error")).contains("at most 50 MB").contains("at most 110 MB");
    }
}
