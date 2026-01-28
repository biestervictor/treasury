package org.example.treasury.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.example.treasury.dto.MailRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "treasury.mail.enabled=true",
    "treasury.mail.from=no-reply@biester.vip",
    "spring.mail.host=localhost",
    "spring.mail.port=3025",
    "spring.mail.username=",
    "spring.mail.password=",
    "spring.mail.properties.mail.smtp.auth=false",
    "spring.mail.properties.mail.smtp.starttls.enable=false",
    "spring.mail.properties.mail.smtp.starttls.required=false"
})
class MailServiceTest {

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
      .withPerMethodLifecycle(true);

  @Autowired
  private MailService mailService;

  @Test
  void sendsPlainTextMail() throws Exception {
    // Arrange
    MailRequest request = new MailRequest(
        List.of("victor.biester@icloud.com"),
        "Test Subject",
        "Hallo Welt"
    );

    // Act
    mailService.send(request);

    // Assert
    assertThat(greenMail.getReceivedMessages()).hasSize(1);

    MimeMessage msg = greenMail.getReceivedMessages()[0];
    assertThat(msg.getSubject()).isEqualTo("Test Subject");
    assertThat(msg.getAllRecipients()).hasSize(1);
    assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("victor.biester@icloud.com");

    Object content = msg.getContent();
    if (content instanceof String s) {
      assertThat(s).contains("Hallo Welt");
    } else if (content instanceof Multipart multipart) {
      BodyPart part = multipart.getBodyPart(0);
      assertThat(part.getContent().toString()).contains("Hallo Welt");
    } else {
      throw new IllegalStateException("Unexpected mail content type: " + content.getClass());
    }
  }
}
