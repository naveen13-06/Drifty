package Backend;

import CLI.Drifty_CLI;
import Utils.DriftyConstants;
import Utils.MessageBroker;
import Utils.ScannerFactory;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static Utils.DriftyUtility.isYoutubeLink;
import static Utils.DriftyUtility.yesNoValidation;
import static Utils.DriftyConstants.*;

/**
 * This class deals with downloading the file.
 */
public class FileDownloader implements Runnable {
    private static MessageBroker messageBroker = Drifty.messageBroker;
    // default number of threads to download with
    private static final int numberOfThreads = 3;
    // default threading threshold in bytes  50MB
    private static final long threadingThreshold = 1024 * 1024 * 50;
    private static String dir;
    private static String fileName;
    private static String link;
    private static long totalSize;
    private static URL url;
    private static boolean supportsMultithreading;
    private static final Scanner SC = ScannerFactory.getInstance();

    /**
     * This is a constructor to initialise values of link, fileName and dir variables.
     *
     * @param link     Link to the file that the user wants to download
     * @param fileName Filename of the file that the user wants to save as after it is downloaded
     * @param dir      Directory in which the file needs to be saved.
     */
    public FileDownloader(String link, String fileName, String dir) {
        FileDownloader.link = link;
        FileDownloader.fileName = fileName;
        FileDownloader.dir = dir;
        FileDownloader.supportsMultithreading = false;
    }

    /**
     * This function is used to get the value of dir variable.
     * @return The directory in which the user wants to save the file.
     */
    public static String getDir() {
        return dir;
    }

    /**
     * This method deals with downloading the file.
     */
    private static void downloadFile() {
        try {
            ReadableByteChannel readableByteChannel;
            try {
                if (FileDownloader.supportsMultithreading) {

                    List<FileOutputStream> fileOutputStreams = new ArrayList<>(FileDownloader.numberOfThreads);
                    List<Long> partSizes = new ArrayList<>(FileDownloader.numberOfThreads);
                    List<File> tempFiles = new ArrayList<>(FileDownloader.numberOfThreads);
                    List<DownloaderThread> downloaderThreads = new ArrayList<>(FileDownloader.numberOfThreads);
                    long partSize = Math.floorDiv(totalSize, FileDownloader.numberOfThreads);
                    long start, end;
                    FileOutputStream fileOut;
                    File file;
                    for (int i = 0; i < FileDownloader.numberOfThreads; i++) {
                        file = File.createTempFile(fileName.hashCode() + "" + i, ".tmp");
                        fileOut = new FileOutputStream(file);
                        start = i == 0 ? 0 : (i * partSize) + 1;
                        end = FileDownloader.numberOfThreads - 1 == i ? totalSize : ((i * partSize) + partSize);
                        DownloaderThread downloader = new DownloaderThread(url, fileOut, start, end);
                        downloader.start();
                        fileOutputStreams.add(fileOut);
                        partSizes.add(end - start);
                        downloaderThreads.add(downloader);
                        tempFiles.add(file);
                    }

                    ProgressBarThread progressBarThread = new ProgressBarThread(fileOutputStreams, partSizes, fileName);
                    progressBarThread.start();
                    // check if all file are downloaded
                    try {
                        while (!merge(fileOutputStreams, partSizes, downloaderThreads, tempFiles)) {
                            Thread.sleep(1000);
                        }
                        progressBarThread.setDownloading(false);
                        // keep main thread from closing the IO for short amt. of time so UI thread can finish and output
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                    } catch (InterruptedException ignored) {}
                } else {
                    InputStream urlStream = url.openStream();
                    System.out.println();
                    readableByteChannel = Channels.newChannel(urlStream);

                    FileOutputStream fos = new FileOutputStream(dir + fileName);
                    ProgressBarThread progressBarThread = new ProgressBarThread(fos, totalSize, fileName);
                    progressBarThread.start();
                    messageBroker.sendMessage("Downloading " + fileName + " ...", LOGGER_INFO, "download");
                    fos.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                    progressBarThread.setDownloading(false);
                    // keep main thread from closing the IO for short amt. of time so UI thread can finish and output
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {}
                }

            } catch (SecurityException e) {
                messageBroker.sendMessage("Write access to " + dir + fileName + " denied !", LOGGER_ERROR, "download");
            } catch (IOException e) {
                messageBroker.sendMessage(FAILED_TO_DOWNLOAD_CONTENTS, LOGGER_ERROR, "download");
            }
        } catch (NullPointerException e) {
            messageBroker.sendMessage(FAILED_TO_READ_DATA_STREAM, LOGGER_ERROR, "download");
        }
        if (dir.length() == 0) {
            dir = System.getProperty("user.dir");
        }
        if (!(dir.endsWith("\\"))) {
            dir = dir + System.getProperty("file.separator");
        }
    }

    /**
     * This method deals with downloading videos from YouTube in mp4 format.
     *
     * @param dirOfYt_dlp The directory of yt-dlp file. Default - "". If Drifty is run from its jar file, this argument will have the directory where yt-dlp has been extracted to (the temporary files' folder).
     * @throws InterruptedException When the I/O operation is interrupted using keyboard or such type of inputs.
     * @throws IOException          When an I/O problem appears while downloading the YouTube video.
     */
    private static void downloadFromYouTube(String dirOfYt_dlp) throws InterruptedException, IOException { // TODO - Use message broker here
        String fName = "";
        messageBroker.sendMessage(TRYING_TO_AUTO_DETECT_DOWNLOADS_FOLDER, LOGGER_INFO, "directory");
        ProcessBuilder processBuilder = new ProcessBuilder(dirOfYt_dlp + "yt-dlp", link, "--print", "title");
        processBuilder.inheritIO();
        System.out.print("Filename : ");
        Process yt_dlp = processBuilder.start();
        yt_dlp.waitFor();
        int exitValueOfYt_Dlp = yt_dlp.exitValue();
        if (exitValueOfYt_Dlp != 0) {
            return;
        }
        System.out.print(RENAME_FILE);
        String renameFile = SC.nextLine().toLowerCase();
        boolean yesOrNo = yesNoValidation(renameFile, "Would you like to rename this file? (Enter Y for yes and N for no) : ");
        if (yesOrNo) {
            System.out.print(ENTER_FILE_NAME_WITH_EXTENSION);
            fName = SC.nextLine();
        }
        System.out.println(TRYING_TO_DOWNLOAD_FILE);
        Drifty_CLI.logger.log(LOGGER_INFO, TRYING_TO_DOWNLOAD_FILE);
        if (fName.equals("")) {
            processBuilder = new ProcessBuilder(dirOfYt_dlp + "yt-dlp", "--quiet", "--progress", "-P", dir, link, "-o", "%(title)s.mp4");
        } else {
            processBuilder = new ProcessBuilder(dirOfYt_dlp + "yt-dlp", "--quiet", "--progress", "-P", dir, link, "-o", fName);
        }
        processBuilder.inheritIO();
        yt_dlp = processBuilder.start();
        yt_dlp.waitFor();
        exitValueOfYt_Dlp = yt_dlp.exitValue();
        if (exitValueOfYt_Dlp == 0) {
            System.out.println(SUCCESSFULLY_DOWNLOADED_FILE);
            Drifty_CLI.logger.log(LOGGER_INFO, SUCCESSFULLY_DOWNLOADED_FILE);
        } else if (exitValueOfYt_Dlp == 1) {
            System.out.println(FAILED_TO_DOWNLOAD_FILES);
            Drifty_CLI.logger.log(LOGGER_INFO, FAILED_TO_DOWNLOAD_FILES);
        }
    }

    /**
     * This method check if all the downloader threads are completed correctly and merges the downloaded parts.
     *
     * @param fileOutputStreams FileOutputStream of all the parts
     * @param partSizes         Size each of the parts
     * @param downloaderThreads DownloaderThreads of all the parts
     * @param tempFiles         Temporary files containing the parts
     * @return true if merge is successful and false if the file is still being downloaded
     * @throws IOException if the threads exit without downloading the whole part or if there are any IO error thrown by  getChannel().size() method of FileOutputStream
     */
    public static boolean merge(List<FileOutputStream> fileOutputStreams, List<Long> partSizes, List<DownloaderThread> downloaderThreads, List<File> tempFiles) throws IOException {
        //check if all file are downloaded
        int completed = 0;
        FileOutputStream fout;
        DownloaderThread downloaderThread;
        long partSize;
        for (int i = 0; i < FileDownloader.numberOfThreads; i++) {
            fout = fileOutputStreams.get(i);
            partSize = partSizes.get(i);
            downloaderThread = downloaderThreads.get(i);

            if (fout.getChannel().size() < partSize) {
                if (!downloaderThread.isAlive()) throw new IOException(THREAD_ERROR_ENCOUNTERED);
            } else if (!downloaderThread.isAlive()) completed++;
        }

        //check if it is merge-able
        if (completed == FileDownloader.numberOfThreads) {
            fout = new FileOutputStream(dir + fileName);
            long position = 0;
            for (int i = 0; i < FileDownloader.numberOfThreads; i++) {
                File f = tempFiles.get(i);
                FileInputStream fs = new FileInputStream(f);
                ReadableByteChannel rbs = Channels.newChannel(fs);
                fout.getChannel().transferFrom(rbs, position, f.length());
                position += f.length();
            }
            fout.close();
            return true;
        }
        return false;
    }

    /**
     * This is the overridden run method of the Runnable interface and deals with the main part of opening connections and downloading the file.
     */
    @Override
    public void run() {
        link = link.replace('\\', '/');
        if (!(link.startsWith("http://") || link.startsWith("https://"))) {
            link = "http://" + link;
        }
        if (link.startsWith("https://github.com/") || (link.startsWith("http://github.com/"))) {
            if (!(link.endsWith("?raw=true"))) {
                link = link + "?raw=true";
            }
        }
        try {
            // If link is of an YouTube video, then the following block of code will execute.
            if (isYoutubeLink(link)) {
                try {
                    downloadFromYouTube("");
                } catch (IOException e) {
                    try {
                        System.out.println(GETTING_READY_TO_DOWNLOAD_FILE);
                        Drifty_CLI.logger.log(LOGGER_INFO, GETTING_READY_TO_DOWNLOAD_FILE);
                        copyYt_dlp cy = new copyYt_dlp();
                        cy.copyToTemp();
                        try {
                            String tempDir = copyYt_dlp.tempDir;
                            if (!tempDir.endsWith("/")){
                                tempDir += "/";
                            }
                            downloadFromYouTube(tempDir);
                        } catch (InterruptedException ie) {
                            System.out.println(USER_INTERRUPTION);
                            Drifty_CLI.logger.log(LOGGER_ERROR, USER_INTERRUPTION + ie.getMessage());
                        } catch (IOException io1) {
                            System.out.println(FAILED_TO_DOWNLOAD_YOUTUBE_VIDEO);
                            Drifty_CLI.logger.log(LOGGER_ERROR, FAILED_TO_DOWNLOAD_YOUTUBE_VIDEO + io1.getMessage());
                        }
                    } catch (IOException io) {
                        System.out.println(FAILED_TO_INITIALISE_YOUTUBE_VIDEO);
                        Drifty_CLI.logger.log(LOGGER_ERROR, FAILED_TO_INITIALISE_YOUTUBE_VIDEO + io.getMessage());
                    }
                } catch (InterruptedException e) {
                    System.out.println(USER_INTERRUPTION);
                    Drifty_CLI.logger.log(LOGGER_ERROR, USER_INTERRUPTION + e.getMessage());
                }
            } else {
                url = new URL(link);
                URLConnection openConnection = url.openConnection();
                openConnection.connect();
                totalSize = openConnection.getHeaderFieldLong("Content-Length", -1);

                String acceptRange = openConnection.getHeaderField("Accept-Ranges");
                FileDownloader.supportsMultithreading = totalSize > threadingThreshold && acceptRange != null && acceptRange.equalsIgnoreCase("bytes");

                if (fileName.length() == 0) {
                    String[] webPaths = url.getFile().trim().split("/");
                    fileName = webPaths[webPaths.length - 1];
                }
                dir = dir.replace('/', '\\');
                if (dir.length() != 0) {
                    if (dir.equals(".\\\\") || dir.equals(".\\")) {
                        dir = "";
                    }
                } else {
                    System.out.println(INVALID_DIRECTORY);
                    Drifty_CLI.logger.log(LOGGER_ERROR, INVALID_DIRECTORY);
                }
                try {
                    new CheckDirectory(dir);
                } catch (IOException e) {
                    System.out.println(FAILED_TO_CREATE_DIRECTORY + dir + " ! " + e.getMessage());
                    Drifty_CLI.logger.log(LOGGER_ERROR, FAILED_TO_CREATE_DIRECTORY + dir + " ! " + e.getMessage());
                }
                System.out.println(TRYING_TO_DOWNLOAD_FILE);
                Drifty_CLI.logger.log(LOGGER_ERROR, TRYING_TO_DOWNLOAD_FILE);
                downloadFile();
            }
        } catch (MalformedURLException e) {
            System.out.println(INVALID_LINK);
            Drifty_CLI.logger.log(LOGGER_ERROR, INVALID_LINK + e.getMessage());
        } catch (IOException e) {
            System.out.println(FAILED_TO_CONNECT_TO_URL + url + " !");
            Drifty_CLI.logger.log(LOGGER_ERROR, FAILED_TO_CONNECT_TO_URL + url + " ! " + e.getMessage());
        }
    }
}
