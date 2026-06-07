import { useState } from 'react';
import { AnimatePresence } from 'motion/react';
import ApiStatusBanner from './components/ApiStatusBanner';
import AIList from './components/AIList';
import CallScreen from './components/CallScreen';
import ChatScreen from './components/ChatScreen';
import WalletScreen from './components/WalletScreen';
import { AppState, AIId } from './types';
import { AI_ROSTER } from './constants';
import { loadWallet, charge, callCost } from './lib/wallet';

export default function App() {
  const [appState, setAppState] = useState<AppState>('hub');
  const [activeAI, setActiveAI] = useState<AIId | null>(null);
  const [wallet, setWallet] = useState(loadWallet);

  const currentAI = AI_ROSTER.find((ai) => ai.id === activeAI);

  const handleCall = (aiId: AIId) => {
    setActiveAI(aiId);
    setAppState('call');
  };

  const handleChat = (aiId: AIId) => {
    setActiveAI(aiId);
    setAppState('chat');
  };

  const handleEndCall = (durationSeconds: number) => {
    const ai = currentAI;
    if (ai && durationSeconds > 0) {
      const cost = callCost(durationSeconds, ai.callPrice);
      setWallet((prev) => {
        const result = charge(prev, cost, 'call', `Call with ${ai.name} (${Math.ceil(durationSeconds / 60)} min)`);
        return result.wallet;
      });
    }
    setAppState('hub');
    setActiveAI(null);
  };

  const handleMessageSent = (): boolean => {
    const ai = currentAI;
    if (!ai) return false;
    let charged = false;
    setWallet((prev) => {
      const result = charge(prev, ai.chatPrice, 'chat', `Message to ${ai.name}`);
      charged = result.success;
      return result.wallet;
    });
    return charged;
  };

  return (
    <div className="min-h-screen bg-white">
      <ApiStatusBanner />

      <main className={appState === 'hub' ? 'block' : 'hidden'}>
        <AIList
          balance={wallet.balance}
          onCall={(id) => handleCall(id as AIId)}
          onChat={(id) => handleChat(id as AIId)}
          onOpenWallet={() => setAppState('wallet')}
        />
      </main>

      <AnimatePresence>
        {appState === 'call' && currentAI && (
          <CallScreen
            ai={currentAI}
            balance={wallet.balance}
            onEndCall={handleEndCall}
          />
        )}

        {appState === 'chat' && currentAI && (
          <ChatScreen
            ai={currentAI}
            balance={wallet.balance}
            onBack={() => { setAppState('hub'); setActiveAI(null); }}
            onCall={() => handleCall(currentAI.id)}
            onMessageSent={handleMessageSent}
          />
        )}

        {appState === 'wallet' && (
          <WalletScreen
            wallet={wallet}
            onBack={() => setAppState('hub')}
            onWalletChange={setWallet}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
