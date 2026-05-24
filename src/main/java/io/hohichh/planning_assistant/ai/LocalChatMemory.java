package io.hohichh.planning_assistant.ai;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalChatMemory implements ChatMemory {

    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();
    private final int maxMessagesPerConversation;

    public LocalChatMemory(int maxMessagesPerConversation) {
        this.maxMessagesPerConversation = maxMessagesPerConversation;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> stored = conversations.computeIfAbsent(conversationId, ignored -> new ArrayList<>());
        synchronized (stored) {
            stored.addAll(messages);
            int overflow = stored.size() - maxMessagesPerConversation;
            if (overflow > 0) {
                stored.subList(0, overflow).clear();
            }
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> stored = conversations.get(conversationId);
        if (stored == null) {
            return List.of();
        }
        synchronized (stored) {
            return List.copyOf(stored);
        }
    }

    @Override
    public void clear(String conversationId) {
        conversations.remove(conversationId);
    }
}
