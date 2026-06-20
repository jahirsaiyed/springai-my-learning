import { create } from "zustand";

export interface ChatMessage {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  timestamp: number;
  streaming?: boolean;
  agent?: string;
  cached?: boolean;
}

interface ChatState {
  conversationId: string | null;
  messages: ChatMessage[];
  isStreaming: boolean;
  activeAgent: string | null;
  setConversationId: (id: string | null) => void;
  addMessage: (msg: ChatMessage) => void;
  appendToLastMessage: (text: string) => void;
  finalizeLastMessage: () => void;
  setStreaming: (streaming: boolean) => void;
  setActiveAgent: (agent: string | null) => void;
  reset: () => void;
}

let msgCounter = 0;

export function generateMsgId(): string {
  return `msg_${Date.now()}_${++msgCounter}`;
}

export const useChatStore = create<ChatState>()((set) => ({
  conversationId: null,
  messages: [],
  isStreaming: false,
  activeAgent: null,

  setConversationId: (id) => set({ conversationId: id }),

  addMessage: (msg) =>
    set((state) => ({ messages: [...state.messages, msg] })),

  appendToLastMessage: (text) =>
    set((state) => {
      const msgs = [...state.messages];
      const last = msgs[msgs.length - 1];
      if (last && last.role === "assistant") {
        msgs[msgs.length - 1] = { ...last, content: last.content + text };
      }
      return { messages: msgs };
    }),

  finalizeLastMessage: () =>
    set((state) => {
      const msgs = [...state.messages];
      const last = msgs[msgs.length - 1];
      if (last && last.streaming) {
        msgs[msgs.length - 1] = { ...last, streaming: false };
      }
      return { messages: msgs };
    }),

  setStreaming: (streaming) => set({ isStreaming: streaming }),
  setActiveAgent: (agent) => set({ activeAgent: agent }),
  reset: () =>
    set({
      conversationId: null,
      messages: [],
      isStreaming: false,
      activeAgent: null,
    }),
}));
