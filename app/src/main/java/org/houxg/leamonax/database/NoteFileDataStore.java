package org.houxg.leamonax.database;


import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.houxg.leamonax.model.NoteFile;
import org.houxg.leamonax.model.NoteFile_Table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NoteFileDataStore {

    public static List<NoteFile> getAllRelated(long noteLocalId) {
        return SQLite.select()
                .from(NoteFile.class)
                .where(NoteFile_Table.noteLocalId.eq(noteLocalId))
                .queryList();
    }

    public static NoteFile getByLocalId(String localId) {
        return SQLite.select()
                .from(NoteFile.class)
                .where(NoteFile_Table.localId.eq(localId))
                .querySingle();
    }

    public static NoteFile getByServerId(String serverId) {
        return SQLite.select()
                .from(NoteFile.class)
                .where(NoteFile_Table.serverId.eq(serverId))
                .querySingle();
    }

    public static List<String> deleteExcept(long noteLocalId, Collection<String> excepts) {
        Set<String> reservedIds = new HashSet<>(excepts);
        List<String> removedPaths = new ArrayList<>();
        for (NoteFile noteFile : getAllRelated(noteLocalId)) {
            if (!reservedIds.contains(noteFile.getLocalId())) {
                addDeletedPath(noteFile, removedPaths);
            }
        }
        return removedPaths;
    }

    public static List<String> deleteAllRelated(long noteLocalId) {
        List<String> removedPaths = new ArrayList<>();
        for (NoteFile noteFile : getAllRelated(noteLocalId)) {
            addDeletedPath(noteFile, removedPaths);
        }
        return removedPaths;
    }

    private static void addDeletedPath(NoteFile noteFile, List<String> removedPaths) {
        String localPath = noteFile.getLocalPath();
        if (noteFile.delete() && localPath != null) {
            removedPaths.add(localPath);
        }
    }
}
