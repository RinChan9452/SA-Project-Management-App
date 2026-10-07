package com.projectsa.project;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * F3 search box and filters of the project list, as given in the URL of {@code /projects}.
 * Values that make no sense (unknown status, letters in an ID, page 0) are ignored instead of causing an error,
 * so a bookmarked or edited URL always shows a list.
 *
 * @param q      text typed in the search box (trimmed), or null
 * @param status only projects in this status, or null for all
 * @param saleId only projects of this Sale in charge, or null for all
 * @param mine   only projects where the logged-in employee is in charge or assigned
 * @param page   1-based page number
 */
public record ProjectSearch(String q, ProjectStatus status, Long saleId, boolean mine, int page) {

    public static final int PAGE_SIZE = 20;
    static final int MAX_QUERY_LENGTH = 100;
    /** Highest page whose first row still fits in an int offset; any higher page shows the last page anyway. */
    static final int MAX_PAGE = Integer.MAX_VALUE / PAGE_SIZE;

    /** Builds the search from raw request parameters. */
    public static ProjectSearch of(String q, String status, String saleId, String mine, String page) {
        String text = q == null || q.isBlank() ? null : q.trim();
        if (text != null && text.length() > MAX_QUERY_LENGTH) {
            text = text.substring(0, MAX_QUERY_LENGTH);
        }
        Long sale = parseLong(saleId);
        Long pageNumber = parseLong(page);
        return new ProjectSearch(text, parseStatus(status), sale != null && sale > 0 ? sale : null,
                "true".equalsIgnoreCase(mine) || "on".equalsIgnoreCase(mine),
                pageNumber == null || pageNumber < 1 || pageNumber > Integer.MAX_VALUE ? 1
                        : (int) Math.min(pageNumber, MAX_PAGE));
    }

    /** Lower-case LIKE pattern for name/customer (partial match), with %, _ and ! escaped by '!'; null if no text. */
    String likePattern() {
        if (q == null) {
            return null;
        }
        String escaped = q.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** The project ID when the search text is a number (a leading # is allowed, e.g. "#12"), else null. */
    Long idValue() {
        if (q == null) {
            return null;
        }
        return parseLong(q.startsWith("#") ? q.substring(1) : q);
    }

    /** Whether any search text or filter is set (to tell "no projects yet" from "nothing found"). */
    public boolean isFiltered() {
        return q != null || status != null || saleId != null || mine;
    }

    /** Link to another page of the same search, keeping every filter. */
    public String url(int pageNumber) {
        // Values go in as URI variables so that characters like & or # in the search text are encoded too
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/projects");
        Map<String, Object> values = new LinkedHashMap<>();
        if (q != null) {
            b.queryParam("q", "{q}");
            values.put("q", q);
        }
        if (status != null) {
            b.queryParam("status", status.name());
        }
        if (saleId != null) {
            b.queryParam("saleId", saleId);
        }
        if (mine) {
            b.queryParam("mine", true);
        }
        if (pageNumber > 1) {
            b.queryParam("page", pageNumber);
        }
        return b.encode().buildAndExpand(values).toUriString();
    }

    private static ProjectStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ProjectStatus.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
