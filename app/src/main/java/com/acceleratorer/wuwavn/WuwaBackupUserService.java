package com.acceleratorer.wuwavn;

import android.os.Environment;
import android.os.RemoteException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class WuwaBackupUserService extends IWuwaBackupService.Stub {
    private static final Set<String> ALLOWED_RELATIVE_PATHS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/Engine.ini",
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini",
            "Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt"
    )));

    @Override
    public boolean exists(String absolutePath) throws RemoteException {
        return validate(absolutePath).isFile();
    }

    @Override
    public long length(String absolutePath) throws RemoteException {
        return validate(absolutePath).length();
    }

    @Override
    public byte[] readFile(String absolutePath, int maxBytes) throws RemoteException {
        File file = validate(absolutePath);
        if (!file.isFile()) {
            throw new RemoteException("File does not exist: " + file.getName());
        }
        if (file.length() > maxBytes) {
            throw new RemoteException("File is larger than maxBytes.");
        }

        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > maxBytes) {
                    throw new RemoteException("File exceeded maxBytes while reading.");
                }
            }
            return output.toByteArray();
        } catch (RemoteException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RemoteException("Read failed: " + exception.getMessage());
        }
    }

    private File validate(String absolutePath) throws RemoteException {
        if (absolutePath == null) {
            throw new RemoteException("Path is null.");
        }
        String rawPath = absolutePath.replace('\\', '/');
        if (rawPath.contains("..")) {
            throw new RemoteException("Blocked path traversal.");
        }

        try {
            File file = new File(absolutePath).getCanonicalFile();
            String normalized = file.getPath().replace('\\', '/');
            String externalRoot = Environment.getExternalStorageDirectory()
                    .getCanonicalFile()
                    .getPath()
                    .replace('\\', '/');

            for (String relativePath : ALLOWED_RELATIVE_PATHS) {
                String expected = new File(externalRoot, relativePath)
                        .getCanonicalFile()
                        .getPath()
                        .replace('\\', '/');
                if (normalized.equals(expected)) {
                    return file;
                }
            }
            throw new RemoteException("Blocked non-allowlisted path.");
        } catch (RemoteException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RemoteException("Path validation failed: " + exception.getMessage());
        }
    }
}
