package com.craftion.farmer.message;

import com.craftion.farmer.config.MessageManager;
import com.craftion.farmer.util.TextUtil;
import java.util.List;
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

    public void sendList(CommandSender receiver, String path) {
        List<String> messages = this.messageManager.stringList(path);
        for (String message : messages) {
            if (!message.isBlank()) {
                receiver.sendMessage(TextUtil.parse(message));
            }
        }
    }
}
