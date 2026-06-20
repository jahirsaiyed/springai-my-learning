"use client";

import { useEffect, useRef, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/stores/auth-store";
import { useChatStore, generateMsgId } from "@/stores/chat-store";
import { streamChat, type StreamEvent } from "@/lib/api";
import { ChatMessage } from "@/components/ChatMessage";
import { ChatInput } from "@/components/ChatInput";
import { TenantSelector } from "@/components/TenantSelector";

export default function ChatPage() {
  const router = useRouter();
  const token = useAuthStore((s) => s.token);
  const email = useAuthStore((s) => s.email);
  const tenantSlug = useAuthStore((s) => s.tenantSlug);
  const logout = useAuthStore((s) => s.logout);

  const conversationId = useChatStore((s) => s.conversationId);
  const messages = useChatStore((s) => s.messages);
  const isStreaming = useChatStore((s) => s.isStreaming);
  const activeAgent = useChatStore((s) => s.activeAgent);

  const addMessage = useChatStore((s) => s.addMessage);
  const appendToLastMessage = useChatStore((s) => s.appendToLastMessage);
  const finalizeLastMessage = useChatStore((s) => s.finalizeLastMessage);
  const setConversationId = useChatStore((s) => s.setConversationId);
  const setStreaming = useChatStore((s) => s.setStreaming);
  const setActiveAgent = useChatStore((s) => s.setActiveAgent);
  const resetChat = useChatStore((s) => s.reset);

  const scrollRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!token) router.replace("/auth");
  }, [token, router]);

  useEffect(() => {
    scrollRef.current?.scrollTo({
      top: scrollRef.current.scrollHeight,
      behavior: "smooth",
    });
  }, [messages]);

  const handleSend = useCallback(
    (text: string) => {
      if (!token || !tenantSlug || isStreaming) return;

      // Add user message
      addMessage({
        id: generateMsgId(),
        role: "user",
        content: text,
        timestamp: Date.now(),
      });

      // Prepare assistant placeholder
      const assistantId = generateMsgId();
      addMessage({
        id: assistantId,
        role: "assistant",
        content: "",
        timestamp: Date.now(),
        streaming: true,
      });

      setStreaming(true);
      setActiveAgent(null);

      const isNewConversation = !conversationId;
      const path = isNewConversation
        ? "/api/chat/stream/start"
        : `/api/chat/stream/${conversationId}`;

      const body = isNewConversation
        ? { message: text, channel: "WEB" }
        : { message: text };

      abortRef.current = streamChat(
        path,
        body,
        token,
        tenantSlug,
        (event: StreamEvent) => {
          switch (event.type) {
            case "agent":
              setActiveAgent(event.data);
              // Update last message with agent info
              useChatStore.setState((state) => {
                const msgs = [...state.messages];
                const last = msgs[msgs.length - 1];
                if (last?.role === "assistant") {
                  msgs[msgs.length - 1] = { ...last, agent: event.data };
                }
                return { messages: msgs };
              });
              break;
            case "cached":
              useChatStore.setState((state) => {
                const msgs = [...state.messages];
                const last = msgs[msgs.length - 1];
                if (last?.role === "assistant") {
                  msgs[msgs.length - 1] = { ...last, cached: true };
                }
                return { messages: msgs };
              });
              break;
            case "token":
              appendToLastMessage(event.data);
              break;
            case "done":
              if (isNewConversation && event.data) {
                setConversationId(event.data);
              }
              break;
            case "error":
              appendToLastMessage(`\n\n*Error: ${event.data}*`);
              break;
          }
        },
        (error) => {
          appendToLastMessage(`\n\n*Connection error: ${error.message}*`);
          finalizeLastMessage();
          setStreaming(false);
        },
        () => {
          finalizeLastMessage();
          setStreaming(false);
        }
      );
    },
    [
      token, tenantSlug, conversationId, isStreaming,
      addMessage, appendToLastMessage, finalizeLastMessage,
      setConversationId, setStreaming, setActiveAgent,
    ]
  );

  function handleNewChat() {
    if (abortRef.current) abortRef.current.abort();
    resetChat();
  }

  function handleLogout() {
    if (abortRef.current) abortRef.current.abort();
    resetChat();
    logout();
    router.replace("/auth");
  }

  if (!token) return null;

  return (
    <div className="flex h-screen flex-col">
      {/* Header */}
      <header
        className="flex items-center justify-between border-b px-4 py-3"
        style={{
          background: "var(--color-surface)",
          borderColor: "var(--color-border)",
        }}
      >
        <div className="flex items-center gap-3">
          <h1 className="text-sm font-semibold">Support Chat</h1>
          <TenantSelector />
          {activeAgent && isStreaming && (
            <span
              className="rounded-full px-2 py-0.5 text-[10px] font-medium uppercase"
              style={{
                background: "var(--color-primary)",
                color: "#fff",
              }}
            >
              {activeAgent}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs" style={{ color: "var(--color-text-muted)" }}>
            {email}
          </span>
          <button
            onClick={handleNewChat}
            className="rounded-lg border px-3 py-1 text-xs transition-opacity hover:opacity-70"
            style={{ borderColor: "var(--color-border)", color: "var(--color-text)" }}
          >
            New Chat
          </button>
          <button
            onClick={handleLogout}
            className="rounded-lg px-3 py-1 text-xs text-white transition-opacity hover:opacity-70"
            style={{ background: "#ef4444" }}
          >
            Logout
          </button>
        </div>
      </header>

      {/* Messages */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-6">
        <div className="mx-auto flex max-w-2xl flex-col gap-4">
          {messages.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-20">
              <div
                className="flex h-14 w-14 items-center justify-center rounded-2xl text-2xl"
                style={{ background: "var(--color-assistant-bubble)" }}
              >
                ?
              </div>
              <p className="text-sm font-medium">How can we help you today?</p>
              <p
                className="max-w-sm text-center text-xs"
                style={{ color: "var(--color-text-muted)" }}
              >
                Ask about orders, refunds, shipping, or anything else.
                {!tenantSlug && " Select a tenant first."}
              </p>
            </div>
          )}
          {messages.map((msg) => (
            <ChatMessage key={msg.id} message={msg} />
          ))}
        </div>
      </div>

      {/* Input */}
      <div className="border-t px-4 py-3" style={{ borderColor: "var(--color-border)" }}>
        <div className="mx-auto max-w-2xl">
          <ChatInput
            onSend={handleSend}
            disabled={isStreaming || !tenantSlug}
          />
        </div>
      </div>
    </div>
  );
}
