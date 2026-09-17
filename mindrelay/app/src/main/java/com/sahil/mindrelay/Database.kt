package com.sahil.mindrelay

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

// Local-first storage. No network permission is declared by the app.
class MindRelayDb(context: Context) : SQLiteOpenHelper(context, "mindrelay.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE projects(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'Active',
            current_state TEXT NOT NULL DEFAULT '',
            next_action TEXT NOT NULL DEFAULT '',
            goal_notes TEXT NOT NULL DEFAULT '',
            last_worked_at INTEGER NOT NULL DEFAULT 0
        )""".trimIndent())
        db.execSQL("""CREATE TABLE sessions(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            project_id INTEGER NOT NULL,
            title TEXT NOT NULL,
            started_at INTEGER NOT NULL,
            ended_at INTEGER,
            status TEXT NOT NULL DEFAULT 'Active'
        )""".trimIndent())
        db.execSQL("""CREATE TABLE session_entries(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id INTEGER NOT NULL,
            kind TEXT NOT NULL,
            body TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE captures(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT NOT NULL DEFAULT 'Idea',
            body TEXT NOT NULL,
            project_id INTEGER,
            session_id INTEGER,
            created_at INTEGER NOT NULL,
            is_voice INTEGER NOT NULL DEFAULT 0,
            archived INTEGER NOT NULL DEFAULT 0
        )""".trimIndent())
        db.execSQL("""CREATE TABLE memories(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            type TEXT NOT NULL,
            body TEXT NOT NULL,
            tags TEXT NOT NULL DEFAULT '',
            project_id INTEGER,
            source_capture_id INTEGER,
            source_session_id INTEGER,
            revisit_at INTEGER,
            archived INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE tasks(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            project_id INTEGER,
            completed INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("CREATE INDEX idx_captures_created ON captures(created_at DESC)")
        db.execSQL("CREATE INDEX idx_memories_updated ON memories(updated_at DESC)")
        db.execSQL("CREATE INDEX idx_sessions_project ON sessions(project_id, started_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun projects(status: String? = null): List<ProjectRow> = queryRows(
        "SELECT * FROM projects ${if (status == null) "" else "WHERE status=?"} ORDER BY last_worked_at DESC, id DESC",
        if (status == null) emptyArray() else arrayOf(status)
    ) { ProjectRow(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5), it.getLong(6)) }

    fun project(id: Long): ProjectRow? = projects().firstOrNull { it.id == id }

    fun insertProject(name: String, state: String, nextAction: String, notes: String): Long {
        val now = System.currentTimeMillis()
        return writableDatabase.insert("projects", null, ContentValues().apply {
            put("name", name.trim())
            put("current_state", state.trim())
            put("next_action", nextAction.trim())
            put("goal_notes", notes.trim())
            put("last_worked_at", now)
        })
    }

    fun updateProject(project: ProjectRow) {
        writableDatabase.update("projects", ContentValues().apply {
            put("name", project.name)
            put("status", project.status)
            put("current_state", project.currentState)
            put("next_action", project.nextAction)
            put("goal_notes", project.goalNotes)
            put("last_worked_at", project.lastWorkedAt)
        }, "id=?", arrayOf(project.id.toString()))
    }

    fun deleteProject(id: Long) {
        writableDatabase.delete("session_entries", "session_id IN (SELECT id FROM sessions WHERE project_id=?)", arrayOf(id.toString()))
        writableDatabase.delete("sessions", "project_id=?", arrayOf(id.toString()))
        writableDatabase.delete("tasks", "project_id=?", arrayOf(id.toString()))
        writableDatabase.delete("captures", "project_id=?", arrayOf(id.toString()))
        writableDatabase.delete("memories", "project_id=?", arrayOf(id.toString()))
        writableDatabase.delete("projects", "id=?", arrayOf(id.toString()))
    }

    fun sessions(projectId: Long): List<SessionRow> = queryRows(
        "SELECT * FROM sessions WHERE project_id=? ORDER BY started_at DESC", arrayOf(projectId.toString())
    ) { SessionRow(it.getLong(0), it.getLong(1), it.getString(2), it.getLong(3), if (it.isNull(4)) null else it.getLong(4), it.getString(5)) }

    fun session(id: Long): SessionRow? = queryRows(
        "SELECT * FROM sessions WHERE id=?", arrayOf(id.toString())
    ) { SessionRow(it.getLong(0), it.getLong(1), it.getString(2), it.getLong(3), if (it.isNull(4)) null else it.getLong(4), it.getString(5)) }.firstOrNull()

    fun startSession(projectId: Long, title: String): Long {
        val now = System.currentTimeMillis()
        return writableDatabase.insert("sessions", null, ContentValues().apply {
            put("project_id", projectId); put("title", title); put("started_at", now); put("status", "Active")
        })
    }

    fun sessionEntries(sessionId: Long): List<SessionEntryRow> = queryRows(
        "SELECT * FROM session_entries WHERE session_id=? ORDER BY created_at ASC", arrayOf(sessionId.toString())
    ) { SessionEntryRow(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getLong(4)) }

    fun finishSessionWithHandoff(
        sessionId: Long,
        projectId: Long,
        completed: String,
        discovered: String,
        unresolved: String,
        nextAction: String,
        promoteDiscovery: Boolean
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val handoff = listOf(
                completed.takeIf { it.isNotBlank() }?.let { "Completed: $it" },
                discovered.takeIf { it.isNotBlank() }?.let { "Discoveries: $it" },
                unresolved.takeIf { it.isNotBlank() }?.let { "Unresolved: $it" },
                nextAction.takeIf { it.isNotBlank() }?.let { "Next: $it" }
            ).filterNotNull().joinToString("\n\n")
            if (handoff.isNotBlank()) addSessionEntry(sessionId, "Handoff", handoff)
            db.update("sessions", ContentValues().apply { put("ended_at", now); put("status", "Done") }, "id=?", arrayOf(sessionId.toString()))
            db.update("projects", ContentValues().apply {
                put("current_state", unresolved.trim().ifBlank { completed.trim() })
                put("next_action", nextAction.trim())
                put("last_worked_at", now)
            }, "id=?", arrayOf(projectId.toString()))
            if (promoteDiscovery && discovered.isNotBlank()) {
                db.insert("memories", null, ContentValues().apply {
                    put("title", "Discovery from session")
                    put("type", "Fix")
                    put("body", discovered.trim())
                    put("tags", "")
                    put("project_id", projectId)
                    put("source_session_id", sessionId)
                    put("created_at", now)
                    put("updated_at", now)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun addSessionEntry(sessionId: Long, kind: String, body: String) {
        writableDatabase.insert("session_entries", null, ContentValues().apply {
            put("session_id", sessionId); put("kind", kind); put("body", body.trim()); put("created_at", System.currentTimeMillis())
        })
    }

    fun captures(filter: String? = null): List<CaptureRow> {
        val where = when (filter) {
            "Ideas" -> "AND type='Idea'"
            "To-dos" -> "AND type='To-do'"
            "Questions" -> "AND type='Question'"
            "Voice" -> "AND is_voice=1"
            else -> ""
        }
        return queryRows("SELECT * FROM captures WHERE archived=0 $where ORDER BY created_at DESC", emptyArray()) {
            CaptureRow(it.getLong(0), it.getString(1), it.getString(2), if (it.isNull(3)) null else it.getLong(3), if (it.isNull(4)) null else it.getLong(4), it.getLong(5), it.getInt(6) == 1, it.getInt(7) == 1)
        }
    }

    fun capture(id: Long): CaptureRow? = queryRows("SELECT * FROM captures WHERE id=?", arrayOf(id.toString())) {
        CaptureRow(it.getLong(0), it.getString(1), it.getString(2), if (it.isNull(3)) null else it.getLong(3), if (it.isNull(4)) null else it.getLong(4), it.getLong(5), it.getInt(6) == 1, it.getInt(7) == 1)
    }.firstOrNull()

    fun insertCapture(type: String, body: String, projectId: Long?, sessionId: Long?, voice: Boolean): Long = writableDatabase.insert("captures", null, ContentValues().apply {
        put("type", type.ifBlank { "Idea" }); put("body", body.trim()); if (projectId != null) put("project_id", projectId); if (sessionId != null) put("session_id", sessionId); put("created_at", System.currentTimeMillis()); put("is_voice", if (voice) 1 else 0)
    })

    fun archiveCapture(id: Long) { writableDatabase.update("captures", ContentValues().apply { put("archived", 1) }, "id=?", arrayOf(id.toString())) }
    fun deleteCapture(id: Long) { writableDatabase.delete("captures", "id=?", arrayOf(id.toString())) }

    fun memories(filter: String? = null): List<MemoryRow> {
        val type = when (filter) {
            "Fixes" -> "Fix"
            "People" -> "Person"
            "Ideas" -> "Idea"
            "Places" -> "Place"
            else -> null
        }
        return queryRows(
            "SELECT * FROM memories WHERE archived=0 ${if (type == null) "" else "AND type=?"} ORDER BY updated_at DESC",
            if (type == null) emptyArray() else arrayOf(type)
        ) {
            MemoryRow(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), if (it.isNull(5)) null else it.getLong(5), if (it.isNull(6)) null else it.getLong(6), if (it.isNull(7)) null else it.getLong(7), if (it.isNull(8)) null else it.getLong(8), it.getInt(9) == 1, it.getLong(10), it.getLong(11))
        }
    }

    fun memory(id: Long): MemoryRow? = queryRows("SELECT * FROM memories WHERE id=?", arrayOf(id.toString())) {
        MemoryRow(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), if (it.isNull(5)) null else it.getLong(5), if (it.isNull(6)) null else it.getLong(6), if (it.isNull(7)) null else it.getLong(7), if (it.isNull(8)) null else it.getLong(8), it.getInt(9) == 1, it.getLong(10), it.getLong(11))
    }.firstOrNull()

    fun insertMemory(title: String, type: String, body: String, tags: String, projectId: Long?, sourceCaptureId: Long?, sourceSessionId: Long?, revisit: Long?): Long {
        val now = System.currentTimeMillis()
        return writableDatabase.insert("memories", null, ContentValues().apply {
            put("title", title.trim()); put("type", type); put("body", body.trim()); put("tags", tags.trim()); if (projectId != null) put("project_id", projectId); if (sourceCaptureId != null) put("source_capture_id", sourceCaptureId); if (sourceSessionId != null) put("source_session_id", sourceSessionId); if (revisit != null) put("revisit_at", revisit); put("created_at", now); put("updated_at", now)
        })
    }

    fun updateMemory(memory: MemoryRow) {
        writableDatabase.update("memories", ContentValues().apply {
            put("title", memory.title); put("type", memory.type); put("body", memory.body); put("tags", memory.tags)
            if (memory.projectId != null) put("project_id", memory.projectId) else putNull("project_id")
            if (memory.sourceCaptureId != null) put("source_capture_id", memory.sourceCaptureId) else putNull("source_capture_id")
            if (memory.sourceSessionId != null) put("source_session_id", memory.sourceSessionId) else putNull("source_session_id")
            if (memory.revisitAt != null) put("revisit_at", memory.revisitAt) else putNull("revisit_at")
            put("archived", if (memory.archived) 1 else 0); put("updated_at", System.currentTimeMillis())
        }, "id=?", arrayOf(memory.id.toString()))
    }

    fun archiveMemory(id: Long) { writableDatabase.update("memories", ContentValues().apply { put("archived", 1); put("updated_at", System.currentTimeMillis()) }, "id=?", arrayOf(id.toString())) }
    fun deleteMemory(id: Long) { writableDatabase.delete("memories", "id=?", arrayOf(id.toString())) }

    fun tasks(projectId: Long? = null): List<TaskRow> = queryRows(
        "SELECT * FROM tasks ${if (projectId == null) "" else "WHERE project_id=?"} ORDER BY completed ASC, created_at DESC",
        if (projectId == null) emptyArray() else arrayOf(projectId.toString())
    ) { TaskRow(it.getLong(0), it.getString(1), if (it.isNull(2)) null else it.getLong(2), it.getInt(3) == 1, it.getLong(4)) }

    fun insertTask(title: String, projectId: Long?): Long = writableDatabase.insert("tasks", null, ContentValues().apply { put("title", title.trim()); if (projectId != null) put("project_id", projectId); put("created_at", System.currentTimeMillis()) })
    fun setTaskComplete(id: Long, complete: Boolean) { writableDatabase.update("tasks", ContentValues().apply { put("completed", if (complete) 1 else 0) }, "id=?", arrayOf(id.toString())) }

    fun search(query: String): List<SearchRow> {
        val q = "%${query.trim()}%"
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<SearchRow>()
        queryRows("SELECT id,name,next_action FROM projects WHERE name LIKE ? OR current_state LIKE ? OR next_action LIKE ? LIMIT 20", arrayOf(q,q,q)) { SearchRow("Projects", it.getLong(0), it.getString(1), it.getString(2), "folder") }.also { out += it }
        queryRows("SELECT s.id,s.title,p.name FROM sessions s JOIN projects p ON p.id=s.project_id WHERE s.title LIKE ? LIMIT 20", arrayOf(q)) { SearchRow("Sessions", it.getLong(0), it.getString(1), it.getString(2), "schedule") }.also { out += it }
        queryRows("SELECT id,title,tags FROM memories WHERE archived=0 AND (title LIKE ? OR body LIKE ? OR tags LIKE ?) LIMIT 20", arrayOf(q,q,q)) { SearchRow("Memories", it.getLong(0), it.getString(1), it.getString(2), "bookmark") }.also { out += it }
        queryRows("SELECT id,body,type FROM captures WHERE archived=0 AND body LIKE ? LIMIT 20", arrayOf(q)) { SearchRow("Captures", it.getLong(0), it.getString(1), it.getString(2), "inbox") }.also { out += it }
        return out
    }

    fun exportJson(): String {
        val root = JSONObject().put("schema", 1).put("exportedAt", System.currentTimeMillis())
        fun table(name: String, columns: Array<String>): JSONArray {
            val arr = JSONArray()
            queryRows("SELECT * FROM $name", emptyArray()) { c ->
                val row = JSONObject()
                columns.forEachIndexed { i, col -> row.put(col, if (c.isNull(i)) JSONObject.NULL else c.getString(i)) }
                arr.put(row)
            }
            return arr
        }
        root.put("projects", table("projects", arrayOf("id","name","status","current_state","next_action","goal_notes","last_worked_at")))
        root.put("sessions", table("sessions", arrayOf("id","project_id","title","started_at","ended_at","status")))
        root.put("session_entries", table("session_entries", arrayOf("id","session_id","kind","body","created_at")))
        root.put("captures", table("captures", arrayOf("id","type","body","project_id","session_id","created_at","is_voice","archived")))
        root.put("memories", table("memories", arrayOf("id","title","type","body","tags","project_id","source_capture_id","source_session_id","revisit_at","archived","created_at","updated_at")))
        root.put("tasks", table("tasks", arrayOf("id","title","project_id","completed","created_at")))
        return root.toString(2)
    }

    fun importJson(json: String, replace: Boolean): Pair<Boolean, String> = runCatching {
        val root = JSONObject(json)
        require(root.getInt("schema") == 1) { "Unsupported backup schema" }
        writableDatabase.beginTransaction()
        try {
            if (replace) listOf("session_entries","sessions","captures","memories","tasks","projects").forEach { writableDatabase.delete(it, null, null) }
            insertJsonTable(root.getJSONArray("projects"), "projects", arrayOf("id","name","status","current_state","next_action","goal_notes","last_worked_at"), replace)
            insertJsonTable(root.getJSONArray("sessions"), "sessions", arrayOf("id","project_id","title","started_at","ended_at","status"), replace)
            insertJsonTable(root.getJSONArray("session_entries"), "session_entries", arrayOf("id","session_id","kind","body","created_at"), replace)
            insertJsonTable(root.getJSONArray("captures"), "captures", arrayOf("id","type","body","project_id","session_id","created_at","is_voice","archived"), replace)
            insertJsonTable(root.getJSONArray("memories"), "memories", arrayOf("id","title","type","body","tags","project_id","source_capture_id","source_session_id","revisit_at","archived","created_at","updated_at"), replace)
            insertJsonTable(root.getJSONArray("tasks"), "tasks", arrayOf("id","title","project_id","completed","created_at"), replace)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        true to "Backup imported successfully"
    }.getOrElse { false to (it.message ?: "Backup import failed") }

    private fun insertJsonTable(arr: JSONArray, table: String, columns: Array<String>, replace: Boolean) {
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val values = ContentValues()
            columns.forEach { col ->
                if (obj.has(col)) {
                    val v = obj.get(col)
                    if (v == JSONObject.NULL) values.putNull(col) else values.put(col, v.toString())
                }
            }
            writableDatabase.insertWithOnConflict(table, null, values, if (replace) SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun <T> queryRows(sql: String, args: Array<String>, mapper: (android.database.Cursor) -> T): List<T> {
        val out = mutableListOf<T>()
        readableDatabase.rawQuery(sql, args).use { c -> while (c.moveToNext()) out += mapper(c) }
        return out
    }
}

data class ProjectRow(val id: Long, val name: String, val status: String, val currentState: String, val nextAction: String, val goalNotes: String, val lastWorkedAt: Long)
data class SessionRow(val id: Long, val projectId: Long, val title: String, val startedAt: Long, val endedAt: Long?, val status: String)
data class SessionEntryRow(val id: Long, val sessionId: Long, val kind: String, val body: String, val createdAt: Long)
data class CaptureRow(val id: Long, val type: String, val body: String, val projectId: Long?, val sessionId: Long?, val createdAt: Long, val isVoice: Boolean, val archived: Boolean)
data class MemoryRow(val id: Long, val title: String, val type: String, val body: String, val tags: String, val projectId: Long?, val sourceCaptureId: Long?, val sourceSessionId: Long?, val revisitAt: Long?, val archived: Boolean, val createdAt: Long, val updatedAt: Long)
data class TaskRow(val id: Long, val title: String, val projectId: Long?, val completed: Boolean, val createdAt: Long)
data class SearchRow(val group: String, val id: Long, val title: String, val supporting: String, val icon: String)
