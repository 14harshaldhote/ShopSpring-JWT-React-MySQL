package com.shopeefy.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Records outgoing mail instead of sending it, so tests can read OTP codes and security alerts. */
public class CapturingMailSender extends JavaMailSenderImpl {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(SimpleMailMessage message) {
        sent.add(message);
    }

    @Override
    public void send(SimpleMailMessage... messages) {
        sent.addAll(List.of(messages));
    }

    public void clear() {
        sent.clear();
    }

    /** Waits for the newest code mailed to {@code to} (mail is sent asynchronously). */
    public String awaitCode(String to) {
        SimpleMailMessage message = await(to, "code");
        Matcher m = CODE.matcher(message.getText());
        if (!m.find()) {
            throw new AssertionError("no code in mail: " + message.getText());
        }
        return m.group(1);
    }

    public SimpleMailMessage await(String to, String subjectContains) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                SimpleMailMessage m = sent.get(i);
                if (m.getTo() != null && List.of(m.getTo()).contains(to) && m.getSubject().contains(subjectContains)) {
                    return m;
                }
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("no mail to " + to + " with subject containing '" + subjectContains + "'; sent: "
                + sent.stream().map(m -> List.of(m.getTo()) + " " + m.getSubject()).toList());
    }

    public long countTo(String to, String subjectContains) {
        return sent.stream().filter(m -> List.of(m.getTo()).contains(to) && m.getSubject().contains(subjectContains)).count();
    }

    public boolean anyTo(String to, String subjectContains) {
        return sent.stream().anyMatch(m -> List.of(m.getTo()).contains(to) && m.getSubject().contains(subjectContains));
    }
}
