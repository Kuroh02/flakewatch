package io.github.chakrabartyaditi2002.flakewatch.jobs;

/** Another worker took over this job after our lease expired, so our work must roll back. */
public class LeaseLostException extends RuntimeException {

    public LeaseLostException(long jobId) {
        super("Lost the lease on job " + jobId + "; rolling back");
    }
}
