package com.example.ai.build;

import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** One finished build: the request and the recorded plan. Saved in the history tab. */
public final class BuildRecord {

    private static final Gson GSON = new Gson();

    public int version = 1;
    /** "build" (blocks) or "object" (display-entity parts). */
    public String kind = "build";
    public String id = "";
    public long createdAt;
    public String prompt = "";
    public String provider = "";
    public String model = "";
    public BuildPlan plan = new BuildPlan();
    /** Size it is placed at (objects only): the plan is already scaled, this is just for its name. */
    public double scale = 1;

    public static BuildRecord create(String prompt, String provider, String model, BuildPlan plan) {
        BuildRecord record = new BuildRecord();
        record.createdAt = System.currentTimeMillis();
        record.id = Long.toString(record.createdAt);
        record.prompt = prompt == null ? "" : prompt;
        record.provider = provider == null ? "" : provider;
        record.model = model == null ? "" : model;
        record.plan = plan;
        record.kind = plan.isObject() ? "object" : "build";
        return record;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static BuildRecord fromJson(String json) {
        BuildRecord record = GSON.fromJson(json, BuildRecord.class);
        if (record == null || record.plan == null || record.plan.ops == null) {
            throw new IllegalArgumentException("Not a build record");
        }
        if (record.plan.parts == null) {
            record.plan.parts = new java.util.ArrayList<>();
        }
        if (record.kind == null) {
            record.kind = record.plan.isObject() ? "object" : "build";
        }
        return record;
    }

    public byte[] toGzip() {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(toJson().getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static BuildRecord fromGzip(byte[] data) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            // Bound the inflated size so a bad packet cannot exhaust memory.
            byte[] json = gzip.readNBytes(16 * 1024 * 1024);
            return fromJson(new String(json, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Bad build record data", exception);
        }
    }
}
