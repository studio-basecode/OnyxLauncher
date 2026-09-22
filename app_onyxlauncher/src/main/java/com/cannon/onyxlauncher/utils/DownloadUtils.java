package com.cannon.onyxlauncher.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.Callable;

import com.cannon.onyxlauncher.*;
import org.apache.commons.io.*;

@SuppressWarnings("IOStreamConstructor")
public class DownloadUtils {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) OnyxLauncher/1.0";
    private static final int TIME_OUT = 15000;

    public static URL normalizeUrl(String url) throws MalformedURLException {
        if (url == null) throw new MalformedURLException("URL is null");
        String trimmed = url.trim();
        // Replace spaces with %20 if unencoded
        if (trimmed.contains(" ")) {
            trimmed = trimmed.replace(" ", "%20");
        }
        return new URL(trimmed);
    }

    public static void download(String url, OutputStream os) throws IOException {
        download(normalizeUrl(url), os);
    }

    public static void download(URL url, OutputStream os) throws IOException {
        InputStream is = null;
        HttpURLConnection conn = null;
        try {
            URL currentUrl = url;
            int redirects = 0;
            while (redirects < 5) {
                conn = (HttpURLConnection) currentUrl.openConnection();
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setConnectTimeout(TIME_OUT);
                conn.setReadTimeout(TIME_OUT);
                conn.setInstanceFollowRedirects(true);
                conn.setDoInput(true);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307 || responseCode == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    if (newUrl != null && !newUrl.isEmpty()) {
                        currentUrl = normalizeUrl(newUrl);
                        conn.disconnect();
                        redirects++;
                        continue;
                    }
                }

                if (responseCode < 200 || responseCode >= 300) {
                    throw new IOException("Server returned HTTP " + responseCode + ": " + conn.getResponseMessage());
                }
                break;
            }

            is = conn.getInputStream();
            IOUtils.copy(is, os);
        } catch (IOException e) {
            throw new IOException("Unable to download from " + url, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    public static String downloadString(String url) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        download(url, bos);
        bos.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void downloadFile(String url, File out) throws IOException {
        FileUtils.ensureParentDirectory(out);
        try (FileOutputStream fileOutputStream = new FileOutputStream(out)) {
            download(url, fileOutputStream);
        }
    }

    public static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                             Tools.DownloaderFeedback monitor) throws IOException {
        FileUtils.ensureParentDirectory(outputFile);

        URL currentUrl = normalizeUrl(urlInput);
        HttpURLConnection conn = null;
        InputStream readStr = null;
        try {
            int redirects = 0;
            while (redirects < 5) {
                conn = (HttpURLConnection) currentUrl.openConnection();
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setConnectTimeout(TIME_OUT);
                conn.setReadTimeout(TIME_OUT);
                conn.setInstanceFollowRedirects(true);
                conn.setDoInput(true);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307 || responseCode == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    if (newUrl != null && !newUrl.isEmpty()) {
                        currentUrl = normalizeUrl(newUrl);
                        conn.disconnect();
                        redirects++;
                        continue;
                    }
                }

                if (responseCode < 200 || responseCode >= 300) {
                    throw new IOException("Server returned HTTP " + responseCode + ": " + conn.getResponseMessage());
                }
                break;
            }

            readStr = conn.getInputStream();
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                int current;
                int overall = 0;
                int length = conn.getContentLength();

                if (buffer == null) buffer = new byte[65535];

                while ((current = readStr.read(buffer)) != -1) {
                    overall += current;
                    fos.write(buffer, 0, current);
                    if (monitor != null) {
                        monitor.updateProgress(overall, length);
                    }
                }
            }
        } catch (IOException e) {
            throw new IOException("Unable to download from " + urlInput, e);
        } finally {
            if (readStr != null) {
                try {
                    readStr.close();
                } catch (Exception ignored) {}
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    public static <T> T downloadStringCached(String url, String cacheName, ParseCallback<T> parseCallback) throws IOException, ParseException{
        File cacheDestination = new File(Tools.DIR_CACHE, "string_cache/"+cacheName);
        if(cacheDestination.isFile() &&
                cacheDestination.canRead() &&
                System.currentTimeMillis() < (cacheDestination.lastModified() + 86400000)) {
            try {
                String cachedString = Tools.read(new FileInputStream(cacheDestination));
                return parseCallback.process(cachedString);
            }catch(IOException e) {
                Log.i("DownloadUtils", "Failed to read the cached file", e);
            }catch (ParseException e) {
                Log.i("DownloadUtils", "Failed to parse the cached file", e);
            }
        }
        String urlContent = DownloadUtils.downloadString(url);
        // if we download the file and fail parsing it, we will yeet outta there
        // and not cache the unparseable sting. We will return this after trying to save the downloaded
        // string into cache
        T parseResult = parseCallback.process(urlContent);

        boolean tryWriteCache;
        if(cacheDestination.exists()) {
            tryWriteCache = cacheDestination.canWrite();
        } else {
            tryWriteCache = FileUtils.ensureParentDirectorySilently(cacheDestination);
        }

        if(tryWriteCache) try {
            Tools.write(cacheDestination.getAbsolutePath(), urlContent);
        }catch(IOException e) {
            Log.i("DownloadUtils", "Failed to cache the string", e);
        }
        return parseResult;
    }

    private static <T> T downloadFile(Callable<T> downloadFunction) throws IOException{
        try {
            return downloadFunction.call();
        } catch (IOException e){
            throw e;
        } catch (InterruptedException e) {
            InterruptedIOException iioe = new InterruptedIOException("Download interrupted");
            iioe.initCause(e);
            throw iioe;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static boolean verifyFile(File file, String sha1) {
        return file.exists() && Tools.compareSHA1(file, sha1);
    }

    public static <T> T ensureSha1(File outputFile, @Nullable String sha1, Callable<T> downloadFunction) throws IOException {
        // Skip if needed (treat null or empty/blank sha1 as unverified)
        if(sha1 == null || sha1.trim().isEmpty()) {
            // If the file exists and we don't know it's SHA1, don't try to redownload it.
            if(outputFile.exists()) return null;
            else return downloadFile(downloadFunction);
        }

        int attempts = 0;
        boolean fileOkay = verifyFile(outputFile, sha1);
        T result = null;
        while (attempts < 5 && !fileOkay){
            attempts++;
            downloadFile(downloadFunction);
            fileOkay = verifyFile(outputFile, sha1);
        }
        if(!fileOkay) throw new SHA1VerificationException("SHA1 verification failed after 5 download attempts for " + outputFile.getName());
        return result;
    }

    /**
     * Get the content length for a given URL.
     * @param url the URL to get the length for
     * @return the length in bytes or -1 if not available
     * @throws IOException if an I/O error occurs.
     */
    public static long getContentLength(String url) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
        urlConnection.setRequestMethod("HEAD");
        urlConnection.setDoInput(false);
        urlConnection.setDoOutput(false);
        urlConnection.connect();
        int responseCode = urlConnection.getResponseCode();
        if(responseCode >= 200 && responseCode <= 299) return urlConnection.getContentLength();
        return -1;
    }

    public interface ParseCallback<T> {
        T process(String input) throws ParseException;
    }
    public static class ParseException extends Exception {
        public ParseException(Exception e) {
            super(e);
        }
    }

    public static class SHA1VerificationException extends IOException {
        public SHA1VerificationException(String message) {
            super(message);
        }
    }
}

