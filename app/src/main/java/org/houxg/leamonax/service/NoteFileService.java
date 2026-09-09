package org.houxg.leamonax.service;

import android.net.Uri;
import android.text.TextUtils;

import com.elvishew.xlog.XLog;

import org.bson.types.ObjectId;
import org.houxg.leamonax.Leamonax;
import org.houxg.leamonax.database.NoteFileDataStore;
import org.houxg.leamonax.model.Account;
import org.houxg.leamonax.model.NoteFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import okio.BufferedSource;
import okio.Okio;
import okio.Sink;

public class NoteFileService {

    private static final String TAG = "NoteFileService:";

    private static final String SCHEME = "file";
    private static final String IMAGE_PATH = "getImage";
    private static final String IMAGE_PATH_WITH_SLASH = "/getImage";

    public static String convertFromLocalIdToServerId(String localId) {
        NoteFile noteFile = NoteFileDataStore.getByLocalId(localId);
        return noteFile == null ? null : noteFile.getServerId();
    }

    public static Uri createImageFile(long noteLocalId, String filePath) {
        NoteFile noteFile = new NoteFile();
        noteFile.setNoteId(noteLocalId);
        noteFile.setLocalId(new ObjectId().toString());
        noteFile.setLocalPath(filePath);
        noteFile.setIsAttach(false);
        if (!noteFile.save()) {
            SelectedImageStore.from(Leamonax.getContext()).deleteIfManaged(new File(filePath));
            throw new IllegalStateException("Unable to persist selected image relation");
        }
        return getLocalImageUri(noteFile.getLocalId());
    }

    public static Uri getLocalImageUri(String localId) {
        return new Uri.Builder().scheme(SCHEME).path(IMAGE_PATH).appendQueryParameter("id", localId).build();
    }

    public static void deleteLocalImage(Uri imageUri) {
        if (!isLocalImageUri(imageUri)) {
            throw new IllegalArgumentException("Not a local image URI: " + imageUri);
        }
        String localId = imageUri.getQueryParameter("id");
        NoteFile noteFile = NoteFileDataStore.getByLocalId(localId);
        if (noteFile == null) {
            return;
        }
        String localPath = noteFile.getLocalPath();
        if (!noteFile.delete()) {
            throw new IllegalStateException("Unable to delete local image relation " + localId);
        }
        if (!TextUtils.isEmpty(localPath)) {
            SelectedImageStore store = SelectedImageStore.from(Leamonax.getContext());
            File file = new File(localPath);
            if (store.isManaged(file) && !store.deleteIfManaged(file)) {
                throw new IllegalStateException("Unable to delete managed image " + localId);
            }
        }
    }

    public static Uri getServerImageUri(String serverId) {
        Uri uri = Uri.parse(Account.getCurrent().getHost());
        return uri.buildUpon().appendEncodedPath("api/file/getImage").appendQueryParameter("fileId", serverId).build();
    }

    public static boolean isLocalImageUri(Uri uri) {
        return SCHEME.equals(uri.getScheme()) && IMAGE_PATH_WITH_SLASH.equals(uri.getPath());
    }

    public static String getImagePath(Uri uri) {
        String localId = uri.getQueryParameter("id");
        NoteFile noteFile = NoteFileDataStore.getByLocalId(localId);
        if (noteFile == null) {
            return null;
        }
        if (!TextUtils.isEmpty(noteFile.getLocalPath())) {
            File file = new File(noteFile.getLocalPath());
            return file.isFile() ? noteFile.getLocalPath() : null;
        } else {
            return null;
        }
    }

    public static List<NoteFile> getRelatedNoteFiles(long noteLocalId) {
        return NoteFileDataStore.getAllRelated(noteLocalId);
    }

    public static InputStream getImage(String localId) {
        NoteFile noteFile = NoteFileDataStore.getByLocalId(localId);
        if (noteFile == null) {
            return null;
        }
        String filePath = null;
        if (isLocalFileExist(noteFile.getLocalPath())) {
            filePath = noteFile.getLocalPath();
            XLog.i(TAG + "use local image, path=" + filePath);
        } else {
            String url = NoteFileService.getUrl(Account.getCurrent().getHost(), noteFile.getServerId(), Account.getCurrent().getAccessToken());
            XLog.i(TAG + "use server image, url=" + url);
            try {
                filePath = NoteFileService.getImageFromServer(Uri.parse(url), Leamonax.getContext().getCacheDir());
                noteFile.setLocalPath(filePath);
                XLog.i(TAG + "download finished, path=" + filePath);
                noteFile.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        FileInputStream inputStream = null;
        try {
            if (!TextUtils.isEmpty(filePath)) {
                inputStream = new FileInputStream(filePath);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return inputStream;
    }

    private static String getImageFromServer(Uri targetUri, File parentDir) throws IOException {
        URI target = URI.create(targetUri.toString());
        String fileName = String.format(Locale.US, "leanote-%s.png", new ObjectId().toString());
        File file = new File(parentDir, fileName);
//        XLog.i(TAG + "target=" + target.toString() + ", file=" + file.getAbsolutePath());

        InputStream input = target.toURL().openStream();
        BufferedSource source = Okio.buffer(Okio.source(input));
        Sink output = Okio.sink(file);
        source.readAll(output);
        source.close();
        output.flush();
        output.close();
        return file.getAbsolutePath();
    }

    private static String getUrl(String baseUrl, String serverId, String token) {
        return String.format(Locale.US, "%s/api/file/getImage?fileId=%s&token=%s", baseUrl, serverId, token);
    }

    private static boolean isLocalFileExist(String path) {
        if (!TextUtils.isEmpty(path)) {
            File file = new File(path);
            return file.isFile();
        }
        return false;
    }
}
