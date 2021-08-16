package com.mail.springbootimaplistener.service;

import javax.mail.internet.MimeMessage;

public interface ReceiveMailService {

    void handleReceiveMail(MimeMessage mimeMessage);


}
