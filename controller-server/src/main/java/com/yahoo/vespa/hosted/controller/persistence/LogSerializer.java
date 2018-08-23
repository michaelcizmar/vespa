package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.log.LogLevel;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.deployment.LogEntry;
import com.yahoo.vespa.hosted.controller.deployment.Step;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

/**
 * Serialisation of LogRecord objects. Not all fields are stored!
 *
 * @author jonmv
 */
class LogSerializer {

    private static final String idField = "id";
    private static final String levelField = "level";
    private static final String timestampField = "at";
    private static final String messageField = "message";

    byte[] toJson(Map<Step, List<LogEntry>> log) {
        try {
            return SlimeUtils.toJsonBytes(toSlime(log));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Slime toSlime(Map<Step, List<LogEntry>> log) {
        Slime root = new Slime();
        Cursor logObject = root.setObject();
        log.forEach((step, entries) -> {
            Cursor recordsArray = logObject.setArray(RunSerializer.valueOf(step));
            entries.forEach(entry -> toSlime(entry, recordsArray.addObject()));
        });
        return root;
    }

    private void toSlime(LogEntry entry, Cursor entryObject) {
        entryObject.setLong(idField, entry.id());
        entryObject.setLong(timestampField, entry.at());
        entryObject.setString(levelField, entry.level().getName());
        entryObject.setString(messageField, entry.message());
    }

    Map<Step, List<LogEntry>> fromJson(byte[] logJson, long after) {
        return fromJson(Collections.singletonList(logJson), after);
    }

    Map<Step, List<LogEntry>> fromJson(List<byte[]> logJsons, long after) {
        return fromSlime(logJsons.stream()
                                 .map(SlimeUtils::jsonToSlime)
                                 .collect(Collectors.toList()),
                         after);
    }

    Map<Step, List<LogEntry>> fromSlime(List<Slime> slimes, long after) {
        Map<Step, List<LogEntry>> log = new HashMap<>();
        slimes.forEach(slime -> slime.get().traverse((ObjectTraverser) (stepName, entryArray) -> {
            Step step = RunSerializer.stepOf(stepName);
            List<LogEntry> entries = log.computeIfAbsent(step, __ -> new ArrayList<>());
            entryArray.traverse((ArrayTraverser) (__, entryObject) -> {
                LogEntry entry = fromSlime(entryObject);
                if (entry.id() > after)
                    entries.add(entry);
            });
        }));
        return log;
    }

    private LogEntry fromSlime(Inspector entryObject) {
        return new LogEntry(entryObject.field(idField).asLong(),
                            entryObject.field(timestampField).asLong(),
                            LogLevel.parse(entryObject.field(levelField).asString()),
                            entryObject.field(messageField).asString());
    }

}
