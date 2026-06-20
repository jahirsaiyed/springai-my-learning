"use client";

import ReactMarkdown from "react-markdown";
import type { ChatMessage as ChatMessageType } from "@/stores/chat-store";

interface Props {
  message: ChatMessageType;
}

export function ChatMessage({ message }: Props) {
  const isUser = message.role === "user";

  return (
    <div className={`flex gap-3 ${isUser ? "flex-row-reverse" : ""}`}>
      {/* Avatar */}
      <div
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-medium text-white"
        style={{
          background: isUser ? "var(--color-primary)" : "#6b7280",
        }}
      >
        {isUser ? "U" : "AI"}
      </div>

      {/* Bubble */}
      <div className="max-w-[75%]">
        {message.agent && !isUser && (
          <div
            className="mb-1 text-[10px] font-medium uppercase tracking-wide"
            style={{ color: "var(--color-text-muted)" }}
          >
            {message.agent}
            {message.cached && " (cached)"}
          </div>
        )}
        <div
          className="chat-markdown rounded-2xl px-4 py-2.5 text-sm leading-relaxed"
          style={{
            background: isUser
              ? "var(--color-user-bubble)"
              : "var(--color-assistant-bubble)",
            color: isUser ? "#ffffff" : "var(--color-text)",
          }}
        >
          {isUser ? (
            <p>{message.content}</p>
          ) : (
            <ReactMarkdown>{message.content || "\u200B"}</ReactMarkdown>
          )}
          {message.streaming && (
            <span className="ml-0.5 inline-block h-4 w-1 animate-pulse rounded-full bg-current opacity-60" />
          )}
        </div>
      </div>
    </div>
  );
}
