package Backend;

import Utils.Constants;
import Utils.Utility;
import Utils.MessageBroker;

import javafx.scene.text.Text;

import java.io.IOException;
import java.io.PrintStream;

/**
 * This method is the Backend of Drifty which does the main part of Downloading the file from the website URL.
 * @version 2.0.0
 * @since 2.0.0
 */
public class Drifty {
    public static String projectWebsite = "https://saptarshisarkar12.github.io/Drifty/";
    public static MessageBroker messageBroker;
    private static String downloadsFolder = null;
    private static String url;
    private static String fileName;
    private static String applicationType;

    /**
     * This is the <b>constructor of Backend of Drifty</b> that configures it to be able to work with <b>Graphical User Interface (GUI) Functionalities</b>.
     * @param url Link to the website collected from the user through inputs.
     * @param downloadsDirectory The directory where the file will be downloaded ("./" for Default Downloads Folder).
     * @param fileNameOfTheDownloadedFile File Name to keep for the file to be downloaded.
     * @param linkOutputTextArea The Text area in the GUI, where the Backend will give its outputs regarding the link entered.
     * @param directoryOutputTextArea The Text area in the GUI, where the Backend will give its outputs regarding the directory entered.
     * @param downloadOutputTextArea The Text area in the GUI, where the Backend will give its outputs regarding the download of the file.
     * @param fileNameOutputTextArea The Text area in the GUI, where the Backend will give its outputs regarding the file name entered.
     * @since 2.0.0
     */
    public Drifty(String url, String downloadsDirectory, String fileNameOfTheDownloadedFile, Text linkOutputTextArea, Text directoryOutputTextArea, Text downloadOutputTextArea, Text fileNameOutputTextArea) {
        Drifty.url = url;
        downloadsFolder = downloadsDirectory;
        fileName = fileNameOfTheDownloadedFile;
        applicationType = "GUI";
        messageBroker = new MessageBroker(applicationType, linkOutputTextArea, directoryOutputTextArea, downloadOutputTextArea, fileNameOutputTextArea);
    }

    /**
     * This is the <b>constructor of Backend of Drifty</b> that configures it to be able to work with <b>Command Line Interface (CLI) Functionalities</b>.
     * @param url Link to the website collected from the user through inputs.
     * @param downloadsDirectory The directory where the file will be downloaded ("./" for Default Downloads Folder).
     * @param fileNameOfTheDownloadedFile File Name to keep for the file to be downloaded.
     * @param outputStream The Output Stream where the Backend will give its outputs (Usually, in this case - System.out [Standard Output Stream])
     * @since 2.0.0
     */
    public Drifty(String url, String downloadsDirectory, String fileNameOfTheDownloadedFile, PrintStream outputStream) {
        Drifty.url = url;
        downloadsFolder = downloadsDirectory;
        fileName = fileNameOfTheDownloadedFile;
        applicationType = "CLI";
        messageBroker = new MessageBroker(applicationType, outputStream);
    }

    /**
     * This is the main method where the Drifty Backend starts the backend process of Validating the inputs followed by downloading the file in the required folder.
     */
    public void start() {
        boolean errorFlag = false;
        messageBroker.sendMessage("Validating the link...", Constants.LOGGER_INFO, "link");
        if (url.contains(" ")) {
            messageBroker.sendMessage("Link should not contain whitespace characters!", Constants.LOGGER_ERROR, "link");
            errorFlag = true;
        } else if (url.length() == 0) {
            messageBroker.sendMessage("Link cannot be empty!", Constants.LOGGER_ERROR, "link");
            errorFlag = true;
        } else {
            try {
                Utility.isURLValid(url);
                messageBroker.sendMessage("Link is valid!", Constants.LOGGER_INFO, "link");
            } catch (Exception e) {
                messageBroker.sendMessage(e.getMessage(), Constants.LOGGER_ERROR, "link");
                errorFlag = true;
            }
        }
        if (downloadsFolder == null){
            downloadsFolder = Utility.saveToDefault();
        } else {
            downloadsFolder = downloadsFolder.replace('\\', '/');
            if (downloadsFolder.equals(".//") || downloadsFolder.equals("./")) {
                downloadsFolder = "";
            } else {
                try {
                    new CheckDirectory(downloadsFolder);
                } catch (IOException e) {
                    messageBroker.sendMessage(e.getMessage(), Constants.LOGGER_ERROR, "directory");
                    errorFlag = true;
                }
            }
        }

        if (((fileName == null) || (fileName.length() == 0)) && (!Utility.isYoutubeLink(url))) {
            fileName = Utility.findFilenameInLink(url);
            if (fileName == null || fileName.length() == 0) {
                messageBroker.sendMessage("Filename cannot be empty!", Constants.LOGGER_ERROR, "Filename");
                errorFlag = true;
            }
        }

        if (!errorFlag) {
            new FileDownloader(url, fileName, downloadsFolder).run();
        }
    }

    protected static String getAppType() {
        return applicationType;
    }
}
