package com.craftion.farmer.message;

import com.craftion.farmer.config.MessageManager;
import com.craftion.farmer.util.TextUtil;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;

public final class MessageService {

    private final MessageManager messageManager;

    public MessageService(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    public void send(CommandSender receiver, String path) {
        String message = this.messageManager.string(path);
        if (message == null || message.isBlank()) {
            return;
        }

        receiver.sendMessage(TextUtil.parse(message));
    }

    public void send(CommandSender receiver, String path, Map<String, String> placeholders) {
        String message = this.messageManager.string(path);
        if (message == null || message.isBlank()) {
            return;
        }

        receiver.sendMessage(TextUtil.parse(applyPlaceholders(message, placeholders)));
    }

    public void sendList(CommandSender receiver, String path) {
        List<String> messages = this.messageManager.stringList(path);
        for (String message : messages) {
            if (!message.isBlank()) {
                receiver.sendMessage(TextUtil.parse(message));
            }
        }
    }

    public void sendList(CommandSender receiver, String path, Map<String, String> placeholders) {
        List<String> messages = this.messageManager.stringList(path);
        for (String message : messages) {
            if (!message.isBlank()) {
                receiver.sendMessage(TextUtil.parse(applyPlaceholders(message, placeholders)));
            }
        }
    }

    public String messageString(String path, String fallback) {
        return this.messageManager.messageString(path, fallback);
    }

    public List<String> messageList(String path, List<String> fallbackList) {
        return this.messageManager.messageList(path, fallbackList);
    }

    public String applyPlaceholders(String message, Map<String, String> placeholders) {
        if (message == null) {
            return null;
        }
        String result = message;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return result;
    }

    public List<String> applyPlaceholders(List<String> messages, Map<String, String> placeholders) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream().map(message -> applyPlaceholders(message, placeholders)).toList();
    }
}
