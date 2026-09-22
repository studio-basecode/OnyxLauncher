package com.cannon.onyxlauncher.modloaders.modpacks.api;

import androidx.annotation.Nullable;

import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ModDownloader {
    private static final ThreadLocal<byte[]> sThreadLocalBuffer = new ThreadLocal<>();
    private final ThreadPoolExecutor mDownloadPool = new ThreadPoolExecutor(4,4,100, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());
    private final AtomicBoolean mTerminator = new AtomicBoolean(false);
    private final AtomicLong mDownloadSize = new AtomicLong(0);
    private final Object mExceptionSyncPoint = new Object();
    private final File mDestinationDirectory;
    private final boolean mUseFileCount;
    private IOException mFirstIOException;
    private long mTotalSize;

    public ModDownloader(File destinationDirectory) {
        this(destinationDirectory, false);
    }

    public ModDownloader(File destinationDirectory, boolean useFileCount) {
        this.mDownloadPool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        this.mDestinationDirectory = destinationDirectory;
        this.mUseFileCount = useFileCount;
    }

    public void submitDownload(int fileSize, String relativePath, @Nullable String downloadHash, String... url) {
        if(mUseFileCount) mTotalSize += 1;
        else mTotalSize += fileSize;
        mDownloadPool.execute(new DownloadTask(url, new File(mDestinationDirectory, relativePath), downloadHash));
    }

    public void submitDownload(FileInfoProvider infoProvider) {
        if(!mUseFileCount) throw new RuntimeException("This method can only be used in a file-counting ModDownloader");
        mTotalSize += 1;
        mDownloadPool.execute(new FileInfoQueryTask(infoProvider));
    }

    public void awaitFinish(Tools.DownloaderFeedback feedback) throws IOException {
        try {
            mDownloadPool.shutdown();
            while(!mDownloadPool.awaitTermination(20, TimeUnit.MILLISECONDS) && !mTerminator.get()) {
                feedback.updateProgress((int) mDownloadSize.get(), (int) mTotalSize);
            }
            if(mTerminator.get()) {
                mDownloadPool.shutdownNow();
                synchronized (mExceptionSyncPoint) {
                    if(mFirstIOException == null) mExceptionSyncPoint.wait();
                    throw mFirstIOException;
                }
            }
        }catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static byte[] getThreadLocalBuffer() {
        byte[] buffer = sThreadLocalBuffer.get();
        if(buffer != null) return buffer;
        buffer = new byte[8192];
        sThreadLocalBuffer.set(buffer);
        return buffer;
    }

    private void downloadFailed(IOException exception) {
        mTerminator.set(true);
        synchronized (mExceptionSyncPoint) {
            if(mFirstIOException == null) {
                mFirstIOException = exception;
                mExceptionSyncPoint.notify();
            }
        }
    }

    class FileInfoQueryTask implements Runnable {
        private final FileInfoProvider mFileInfoProvider;
        public FileInfoQueryTask(FileInfoProvider fileInfoProvider) {
            this.mFileInfoProvider = fileInfoProvider;
        }
        @Override
        public void run() {
            try {
                FileInfo fileInfo = mFileInfoProvider.getFileInfo();
                if(fileInfo == null) {
                    if (mUseFileCount) mDownloadSize.addAndGet(1);
                    return;
                }
                File dest = new File(mDestinationDirectory, fileInfo.relativePath);
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                new DownloadTask(new String[]{fileInfo.url}, dest, fileInfo.sha1).run();
            } catch (Throwable e) {
                if (e instanceof InterruptedIOException || e instanceof InterruptedException) {
                    downloadFailed(new InterruptedIOException("Interrupted"));
                } else {
                    android.util.Log.w("ModDownloader", "Failed to query/download file: " + e.getMessage());
                    if (mUseFileCount) mDownloadSize.addAndGet(1);
                }
            }
        }
    }

    class DownloadTask implements Runnable, Tools.DownloaderFeedback {
        private final String[] mDownloadUrls;
        private final File mDestination;
        private final String mSha1;
        private int last = 0;

        public DownloadTask(String[] downloadurls,
                            File downloadDestination, String downloadHash) {
            this.mDownloadUrls = downloadurls;
            this.mDestination = downloadDestination;
            this.mSha1 = downloadHash;
        }

        @Override
        public void run() {
            boolean success = false;
            for(String sourceUrl : mDownloadUrls) {
                try {
                    File parent = mDestination.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    DownloadUtils.ensureSha1(mDestination, mSha1, (Callable<Void>) () -> {
                        IOException exception = tryDownload(sourceUrl);
                        if(exception != null) {
                            throw exception;
                        }
                        return null;
                    });
                    success = true;
                    break;
                } catch (InterruptedIOException e) {
                    downloadFailed(new InterruptedIOException("Interrupted"));
                    return;
                } catch (Throwable e) {
                    android.util.Log.w("ModDownloader", "Failed to download " + mDestination.getName() + " from " + sourceUrl + ": " + e.getMessage());
                }
            }
            if (!success && mUseFileCount) {
                // Advance counter so progress reaches 100%
                mDownloadSize.addAndGet(1);
            }
        }

        private IOException tryDownload(String sourceUrl) throws InterruptedException {
            IOException exception = null;
            for (int i = 0; i < 3; i++) {
                try {
                    DownloadUtils.downloadFileMonitored(sourceUrl, mDestination, getThreadLocalBuffer(), this);
                    if(mUseFileCount) mDownloadSize.addAndGet(1);
                    return null;
                } catch (InterruptedIOException e) {
                    throw new InterruptedException();
                } catch (IOException e) {
                    exception = e;
                }
                if(!mUseFileCount) {
                    mDownloadSize.addAndGet(-last);
                    last = 0;
                }
            }
            return exception;
        }

        @Override
        public void updateProgress(int curr, int max) {
            if(mUseFileCount) return;
            mDownloadSize.addAndGet(curr - last);
            last = curr;
        }
    }

    public static class FileInfo {
        public final String url;
        public final String relativePath;
        public final String sha1;

        public FileInfo(String url, String relativePath, @Nullable String sha1) {
            this.url = url;
            this.relativePath = relativePath;
            this.sha1 = sha1;
        }
    }

    public interface FileInfoProvider {
        FileInfo getFileInfo() throws IOException;
    }
}
