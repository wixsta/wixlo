import { motion, AnimatePresence } from 'motion/react';
import { ArrowLeft, Send, Phone } from 'lucide-react';
import { useState, useRef, useEffect } from 'react';
import { AIPersonality } from '../types';
import { getAIResponse } from '../lib/gemini';
import { canAfford, formatCurrency } from '../lib/wallet';

interface ChatScreenProps {
  ai: AIPersonality;
  balance: number;
  onBack: () => void;
  onCall: () => void;
  onMessageSent: () => boolean;
}

interface Message {
  id: string;
  role: 'user' | 'ai';
  text: string;
}

export default function ChatScreen({ ai, balance, onBack, onCall, onMessageSent }: ChatScreenProps) {
  const [messages, setMessages] = useState<Message[]>([
    { id: '1', role: 'ai', text: `Hi, I'm ${ai.name}. ${ai.description}` },
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isTyping]);

  const buildPrompt = (userText: string) => {
    const history = messages
      .slice(-10)
      .map((m) => `${m.role === 'user' ? 'User' : 'Assistant'}: ${m.text}`)
      .join('\n');
    return history ? `${history}\nUser: ${userText}` : userText;
  };

  const handleSend = async () => {
    if (!input.trim() || isTyping) return;

    if (!canAfford({ balance, transactions: [] }, ai.chatPrice)) {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now().toString(),
          role: 'ai',
          text: `Insufficient balance. Each message costs ${formatCurrency(ai.chatPrice)}.`,
        },
      ]);
      return;
    }

    if (!onMessageSent()) return;

    const userMsg: Message = { id: Date.now().toString(), role: 'user', text: input };
    const userText = input;
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setIsTyping(true);

    try {
      const response = await getAIResponse(buildPrompt(userText), ai.systemPrompt);
      const aiMsg: Message = {
        id: (Date.now() + 1).toString(),
        role: 'ai',
        text: response,
      };
      setMessages((prev) => [...prev, aiMsg]);
    } catch {
      setMessages((prev) => [
        ...prev,
        { id: (Date.now() + 1).toString(), role: 'ai', text: 'Something went wrong. Please try again.' },
      ]);
    } finally {
      setIsTyping(false);
    }
  };

  return (
    <motion.div
      initial={{ x: '100%' }}
      animate={{ x: 0 }}
      exit={{ x: '100%' }}
      className="fixed inset-0 z-50 bg-white flex flex-col"
    >
      <div className="flex items-center justify-between px-6 py-6 border-b border-gray-100 bg-white sticky top-0 z-10">
        <div className="flex items-center gap-4">
          <button onClick={onBack} className="p-2 -ml-2 text-gray-500 hover:text-black">
            <ArrowLeft size={24} />
          </button>
          <img
            src={ai.avatarUrl}
            alt={ai.name}
            className="w-10 h-10 rounded-full object-cover"
            referrerPolicy="no-referrer"
          />
          <div>
            <h2 className="font-semibold">{ai.name}</h2>
            <p className="text-xs text-gray-400">{formatCurrency(ai.chatPrice)} per message</p>
          </div>
        </div>

        <button
          onClick={onCall}
          className="p-3 bg-wixlo-green/10 text-wixlo-green rounded-full hover:bg-wixlo-green/20 transition-all"
          title="Call"
        >
          <Phone size={24} fill="currentColor" strokeWidth={0} />
        </button>
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-6 py-4 space-y-4">
        <AnimatePresence initial={false}>
          {messages.map((msg) => (
            <motion.div
              key={msg.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              <div
                className={`max-w-[80%] px-4 py-3 rounded-2xl ${
                  msg.role === 'user'
                    ? 'bg-black text-white rounded-tr-sm'
                    : 'bg-gray-100 text-gray-800 rounded-tl-sm'
                }`}
              >
                {msg.text}
              </div>
            </motion.div>
          ))}
          {isTyping && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex justify-start">
              <div className="bg-gray-100 px-4 py-3 rounded-2xl rounded-tl-sm">
                <div className="flex gap-1">
                  {[0, 0.2, 0.4].map((delay) => (
                    <motion.div
                      key={delay}
                      className="w-1.5 h-1.5 bg-gray-400 rounded-full"
                      animate={{ opacity: [0.4, 1, 0.4] }}
                      transition={{ duration: 1, repeat: Infinity, delay }}
                    />
                  ))}
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <div className="p-6 bg-white border-t">
        <div className="relative flex items-center">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSend()}
            placeholder="Type a message..."
            className="w-full bg-gray-100 rounded-full py-4 px-6 pr-14 focus:outline-none focus:ring-1 focus:ring-black"
          />
          <button
            onClick={handleSend}
            className="absolute right-2 p-3 bg-black text-white rounded-full hover:scale-105 active:scale-95 transition-all"
          >
            <Send size={20} />
          </button>
        </div>
      </div>
    </motion.div>
  );
}
