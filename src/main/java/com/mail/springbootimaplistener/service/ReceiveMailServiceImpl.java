package com.mail.springbootimaplistener.service;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.util.MimeMessageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReceiveMailServiceImpl implements ReceiveMailService{

    static final Logger log= LoggerFactory.getLogger(ReceiveMailServiceImpl.class);

    static final String DOWNLOAD_FOLDER="data";

    static final String DOWNLOADED_MAIL_FOLDER="DOWNLOADED";


    @Override
    public void handleReceiveMail(MimeMessage mimeMessage) {
     try {
         Folder folder=mimeMessage.getFolder();
         folder.open(folder.READ_WRITE);

         Message[] messages=folder.getMessages();
         fetchMessageInFolder(folder,messages);

         Arrays.asList(messages).stream().filter(message -> {
             MimeMessage currentMessage=(MimeMessage) message;
             try {
                 return currentMessage.getMessageID().equalsIgnoreCase(mimeMessage.getMessageID());
             }catch (MessagingException e){
                 log.error("Error occured during proccess message",e);
                 return false;
             }
         }).forEach(this::extractMail);

         copyMailToDownloadedFolder(mimeMessage,folder);

         folder.close(true);
     } catch (Exception e) {
         log.error(e.getMessage(),e);
     }
    }

    private void extractMail(Message message) {
        try {
            final MimeMessage messageToExtract = (MimeMessage) message;
            final MimeMessageParser mimeMessageParser = new MimeMessageParser(messageToExtract).parse();

            showMailContent(mimeMessageParser);

            downloadAttachmentFiles(mimeMessageParser);

            // To delete downloaded email
            messageToExtract.setFlag(Flags.Flag.DELETED, true);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void downloadAttachmentFiles(MimeMessageParser mimeMessageParser) {
        log.debug("Email has {} attachment files", mimeMessageParser.getAttachmentList().size());
        mimeMessageParser.getAttachmentList().forEach(dataSource -> {
            if (StringUtils.isNotBlank(dataSource.getName())) {
                String rootDirectoryPath = new FileSystemResource("").getFile().getAbsolutePath();
                String dataFolderPath = rootDirectoryPath + File.separator + DOWNLOAD_FOLDER;
                createDirectoryIfNotExists(dataFolderPath);

                String downloadedAttachmentFilePath = rootDirectoryPath + File.separator + DOWNLOAD_FOLDER + File.separator + dataSource.getName();
                File downloadedAttachmentFile = new File(downloadedAttachmentFilePath);

                log.info("Save attachment file to: {}", downloadedAttachmentFilePath);

                try (
                        OutputStream out = new FileOutputStream(downloadedAttachmentFile)
                        // InputStream in = dataSource.getInputStream()
                ) {
                    InputStream in = dataSource.getInputStream();
                    IOUtils.copy(in, out);
                } catch (IOException e) {
                    log.error("Failed to save file.", e);
                }
            }
        });
    }

    private void createDirectoryIfNotExists(String directoryPath) {
        if (!Files.exists(Paths.get(directoryPath))) {
            try {
                Files.createDirectories(Paths.get(directoryPath));
            } catch (IOException e) {
                log.error("An error occurred during create folder: {}", directoryPath, e);
            }
        }
    }

    private void showMailContent(MimeMessageParser mimeMessageParser) throws Exception {
        log.debug("From:{} to: {} | Subject: {}",mimeMessageParser.getFrom(),
                mimeMessageParser.getTo(),
                mimeMessageParser.getSubject());
        log.debug("Mail Content:{}",mimeMessageParser.getPlainContent());
    }


    private void fetchMessageInFolder(Folder folder,Message[] messages) throws MessagingException{
        FetchProfile contentsProfile=new FetchProfile();
        contentsProfile.add(FetchProfile.Item.ENVELOPE);
        contentsProfile.add(FetchProfile.Item.CONTENT_INFO);
        contentsProfile.add(FetchProfile.Item.FLAGS);
        contentsProfile.add(FetchProfile.Item.SIZE);
        folder.fetch(messages,contentsProfile);
    }
    private void copyMailToDownloadedFolder(MimeMessage mimeMessage, Folder folder) throws MessagingException {
        Store store=folder.getStore();
        Folder downloadedMailFolder=store.getFolder(DOWNLOADED_MAIL_FOLDER);
        if (downloadedMailFolder.exists()){
            downloadedMailFolder.open(Folder.READ_WRITE);
            downloadedMailFolder.appendMessages(new MimeMessage[]{mimeMessage});
            downloadedMailFolder.close();
        }


    }


}
