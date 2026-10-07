package com.projectsa.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** F3: reading the search and filters from the URL, and building paging links. */
class ProjectSearchTest {

    @Test
    void readsValidParameters() {
        ProjectSearch s = ProjectSearch.of("  Acme  ", "TESTING", "7", "true", "3");
        assertThat(s.q()).isEqualTo("Acme");
        assertThat(s.status()).isEqualTo(ProjectStatus.TESTING);
        assertThat(s.saleId()).isEqualTo(7L);
        assertThat(s.mine()).isTrue();
        assertThat(s.page()).isEqualTo(3);
        assertThat(s.isFiltered()).isTrue();
    }

    @Test
    void ignoresValuesThatMakeNoSense() {
        ProjectSearch s = ProjectSearch.of("   ", "DONE", "abc", "maybe", "-2");
        assertThat(s.q()).isNull();
        assertThat(s.status()).isNull();
        assertThat(s.saleId()).isNull();
        assertThat(s.mine()).isFalse();
        assertThat(s.page()).isEqualTo(1);
        assertThat(s.isFiltered()).isFalse();
        assertThat(ProjectSearch.of(null, null, "0", null, "99999999999").page()).isEqualTo(1);
        assertThat(ProjectSearch.of(null, null, null, null, "x").page()).isEqualTo(1);
        assertThat(ProjectSearch.of(null, null, null, null, "2147483647").page()).isEqualTo(ProjectSearch.MAX_PAGE);
    }

    @Test
    void searchTextIsCutToMaxLength() {
        assertThat(ProjectSearch.of("a".repeat(500), null, null, null, null).q()).hasSize(ProjectSearch.MAX_QUERY_LENGTH);
    }

    @Test
    void likePatternIsLowerCaseAndEscapesWildcards() {
        assertThat(ProjectSearch.of("ACME", null, null, null, null).likePattern()).isEqualTo("%acme%");
        assertThat(ProjectSearch.of("50%_off!", null, null, null, null).likePattern()).isEqualTo("%50!%!_off!!%");
        assertThat(ProjectSearch.of(null, null, null, null, null).likePattern()).isNull();
    }

    @Test
    void numericTextIsAlsoAProjectId() {
        assertThat(ProjectSearch.of("12", null, null, null, null).idValue()).isEqualTo(12L);
        assertThat(ProjectSearch.of("#12", null, null, null, null).idValue()).isEqualTo(12L);
        assertThat(ProjectSearch.of("Acme 12", null, null, null, null).idValue()).isNull();
    }

    @Test
    void pageLinksKeepEveryFilterAndEncodeText() {
        ProjectSearch s = ProjectSearch.of("A&B #1 ไทย", "WORKING", "5", "on", "1");
        assertThat(s.url(2)).isEqualTo(
                "/projects?q=A%26B%20%231%20%E0%B9%84%E0%B8%97%E0%B8%A2&status=WORKING&saleId=5&mine=true&page=2");
        assertThat(s.url(1)).doesNotContain("page=");
        assertThat(ProjectSearch.of(null, null, null, null, null).url(1)).isEqualTo("/projects");
    }
}
