import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Phone, MessageSquare, Search, X, Wallet } from 'lucide-react';
import { AI_ROSTER } from '../constants';
import { AIPersonality } from '../types';
import { formatCurrency } from '../lib/wallet';

interface AIListProps {
  balance: number;
  onCall: (aiId: string) => void;
  onChat: (aiId: string) => void;
  onOpenWallet: () => void;
}

export default function AIList({ balance, onCall, onChat, onOpenWallet }: AIListProps) {
  const [searchQuery, setSearchQuery] = useState('');

  const filteredAI = AI_ROSTER.filter((ai) => {
    const q = searchQuery.toLowerCase();
    return ai.name.toLowerCase().includes(q) || ai.role.toLowerCase().includes(q);
  });

  return (
    <div className="flex flex-col gap-5 max-w-lg mx-auto py-8 px-6">
      <div className="flex items-center justify-between mb-2">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Wixlo</h1>
          <p className="text-gray-400 mt-1 text-sm">Call and message AI contacts</p>
        </div>
        <button
          onClick={onOpenWallet}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gray-50 border border-gray-100 hover:bg-gray-100 transition-colors"
        >
          <Wallet size={18} className="text-wixlo-green" />
          <span className="text-sm font-semibold">{formatCurrency(balance)}</span>
        </button>
      </div>

      <div className="relative">
        <div className="absolute inset-y-0 left-3 flex items-center pointer-events-none text-gray-400">
          <Search size={16} />
        </div>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search contacts..."
          className="w-full bg-gray-50 border border-gray-100 rounded-xl py-2.5 pl-9 pr-8 focus:ring-1 focus:ring-wixlo-green/20 focus:bg-white transition-all outline-none text-sm"
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="absolute inset-y-0 right-3 flex items-center text-gray-400 hover:text-gray-600"
          >
            <X size={16} />
          </button>
        )}
      </div>

      <div className="space-y-0.5">
        <AnimatePresence mode="popLayout">
          {filteredAI.length > 0 ? (
            filteredAI.map((ai) => (
              <motion.div
                key={ai.id}
                layout
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                <AIRow ai={ai} onCall={() => onCall(ai.id)} onChat={() => onChat(ai.id)} />
              </motion.div>
            ))
          ) : (
            <p className="text-center py-12 text-gray-400 text-sm">No contacts found.</p>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}

interface AIRowProps {
  ai: AIPersonality;
  onCall: () => void;
  onChat: () => void;
}

function AIRow({ ai, onCall, onChat }: AIRowProps) {
  return (
    <div className="flex items-center justify-between py-3 border-b border-gray-50 last:border-0">
      <div className="flex items-center gap-3 flex-1 min-w-0">
        <img
          src={ai.avatarUrl}
          alt={ai.name}
          className="w-12 h-12 rounded-full object-cover border border-gray-100"
          referrerPolicy="no-referrer"
        />
        <div className="min-w-0">
          <h3 className="text-base font-semibold text-gray-900 truncate">{ai.name}</h3>
          <p className="text-xs text-gray-500 truncate">{ai.role}</p>
          <p className="text-xs text-gray-400 mt-0.5">
            {formatCurrency(ai.chatPrice)}/msg · {formatCurrency(ai.callPrice)}/min
          </p>
        </div>
      </div>

      <div className="flex gap-1.5 ml-3">
        <button
          onClick={onChat}
          className="p-2 rounded-full hover:bg-gray-100 text-gray-500 hover:text-gray-900"
          title="Message"
        >
          <MessageSquare size={18} />
        </button>
        <button
          onClick={onCall}
          className="p-2 bg-wixlo-green/10 text-wixlo-green rounded-full hover:bg-wixlo-green/20"
          title="Call"
        >
          <Phone size={18} fill="currentColor" strokeWidth={0} />
        </button>
      </div>
    </div>
  );
}
