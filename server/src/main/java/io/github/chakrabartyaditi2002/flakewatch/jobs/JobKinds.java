package io.github.chakrabartyaditi2002.flakewatch.jobs;

/** Job kinds (PLAN §8.1). */
public final class JobKinds {

    public static final String PROCESS_UPLOAD = "PROCESS_UPLOAD";
    public static final String RECLASSIFY_TESTS = "RECLASSIFY_TESTS";
    public static final String RECLASSIFY_AGING = "RECLASSIFY_AGING";
    public static final String PURGE_RAW_FILES = "PURGE_RAW_FILES";
    public static final String PURGE_OLD_DATA = "PURGE_OLD_DATA";

    private JobKinds() {}
}
