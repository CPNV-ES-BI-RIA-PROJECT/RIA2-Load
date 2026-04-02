package com.load.service.sql;

import com.load.dto.Rows.EventRow;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class SqlScriptService {

    private static final DateTimeFormatter REMOTE_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String REMOTE_PREFIX = "bi1-julien/load/";

    public String generate(EventRow event) {
        StringBuilder sb = new StringBuilder(1024);

        sb.append("INSERT INTO events (\n");
        sb.append("    uid,\n");
        sb.append("    dtstamp,\n");
        sb.append("    dtstart,\n");
        sb.append("    dtend,\n");
        sb.append("    summary,\n");
        sb.append("    description,\n");
        sb.append("    categories,\n");
        sb.append("    organizer,\n");
        sb.append("    attendee,\n");
        sb.append("    location,\n");
        sb.append("    timezone\n");
        sb.append(")\n");
        sb.append("VALUES (\n");
        sb.append("           ").append(SqlValueEncoder.v(event.uid())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.dtstamp())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.dtstart())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.dtend())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.summary())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.description())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.categories())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.organizer())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.attendee())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.location())).append(",\n");
        sb.append("           ").append(SqlValueEncoder.v(event.timezone())).append("\n");
        sb.append("       );");

        return sb.toString();
    }

    public String generateRemotePath() {
        return REMOTE_PREFIX + LocalDateTime.now().format(REMOTE_TIMESTAMP_FORMAT) + ".sql";
    }

    public String deriveSqlRemote(String remote) {
        if (remote == null || remote.isBlank()) {
            return generateRemotePath();
        }

        String normalizedRemote = remote.trim();
        if (normalizedRemote.endsWith(".sql")) {
            return normalizedRemote;
        }
        if (normalizedRemote.endsWith(".json")) {
            return normalizedRemote.substring(0, normalizedRemote.length() - 5) + ".sql";
        }
        return normalizedRemote + ".sql";
    }
}
