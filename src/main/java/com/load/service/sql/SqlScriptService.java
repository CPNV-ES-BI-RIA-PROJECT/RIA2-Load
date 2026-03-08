package com.load.service.sql;

import com.load.dto.Rows.EventRow;
import org.springframework.stereotype.Service;

@Service
public class SqlScriptService {

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

    public static String deriveSqlRemote(String remote) {
        if (remote == null || remote.isBlank()) return "script.sql";
        if (remote.endsWith(".json")) return remote.substring(0, remote.length() - 5) + ".sql";
        if (remote.endsWith(".sql")) return remote;
        return remote + ".sql";
    }
}