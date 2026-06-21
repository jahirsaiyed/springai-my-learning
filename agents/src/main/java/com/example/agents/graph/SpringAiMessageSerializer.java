package com.example.agents.graph;

import org.bsc.langgraph4j.serializer.Serializer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Custom LangGraph4j serializer for Spring AI Message types,
 * which do not implement java.io.Serializable.
 */
public class SpringAiMessageSerializer implements Serializer<Message> {

    @Override
    public void write(Message message, ObjectOutput out) throws IOException {
        Serializer.writeUTF(message.getMessageType().name(), out);
        Serializer.writeUTF(message.getText(), out);
    }

    @Override
    public Message read(ObjectInput in) throws IOException, ClassNotFoundException {
        String typeName = Serializer.readUTF(in);
        String text = Serializer.readUTF(in);

        MessageType type = MessageType.valueOf(typeName);
        return switch (type) {
            case USER -> new UserMessage(text);
            case ASSISTANT -> new AssistantMessage(text);
            case SYSTEM -> new SystemMessage(text);
            default -> new UserMessage(text);
        };
    }
}
