package com.example.client;

import com.example.ai.WorkJournal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The shared 작업 일지 as the server sent it: every player's requests in this world. */
public final class WorkJournalClient {

    private static final Map<String, WorkJournal.Job> JOBS = new LinkedHashMap<>();

    private WorkJournalClient() {
    }

    public static synchronized void apply(WorkJournal.Update update) {
        if (update == null) {
            return;
        }
        if (update.reset) {
            JOBS.clear();
        }
        WorkJournal.Job job = update.job;
        if (job != null && job.id != null) {
            if (job.lines == null) {
                job.lines = new ArrayList<>();
            }
            JOBS.put(job.id, job);
            while (JOBS.size() > WorkJournal.MAX_JOBS) {
                JOBS.remove(JOBS.keySet().iterator().next());
            }
        }
    }

    public static synchronized void clear() {
        JOBS.clear();
    }

    /** Newest first. */
    public static synchronized List<WorkJournal.Job> list() {
        List<WorkJournal.Job> out = new ArrayList<>(JOBS.values());
        out.sort(Comparator.comparingLong((WorkJournal.Job j) -> j.time).reversed());
        return out;
    }

    public static synchronized WorkJournal.Job get(String id) {
        return id == null ? null : JOBS.get(id);
    }
}
