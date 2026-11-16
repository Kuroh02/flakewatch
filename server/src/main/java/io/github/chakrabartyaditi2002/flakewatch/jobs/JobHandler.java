package io.github.chakrabartyaditi2002.flakewatch.jobs;

/**
 * Does the work for one kind of job. {@link #handle} runs inside the job's transaction, and the
 * job is marked done in that same transaction, so the work and its completion commit together.
 */
public interface JobHandler {

    String kind();

    void handle(Job job);

    /** Called in a separate transaction when the job has failed for the last time. */
    default void onDead(Job job, String error) {}
}
