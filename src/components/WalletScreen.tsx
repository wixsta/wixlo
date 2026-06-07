import { motion } from 'motion/react';
import { ArrowLeft, Plus } from 'lucide-react';
import { useState } from 'react';
import { WalletState } from '../types';
import { addFunds, formatCurrency } from '../lib/wallet';

interface WalletScreenProps {
  wallet: WalletState;
  onBack: () => void;
  onWalletChange: (wallet: WalletState) => void;
}

const TOP_UP_AMOUNTS = [500, 1000, 2000, 5000];

export default function WalletScreen({ wallet, onBack, onWalletChange }: WalletScreenProps) {
  const [customAmount, setCustomAmount] = useState('');

  const handleTopUp = (amount: number) => {
    if (amount <= 0) return;
    onWalletChange(addFunds(wallet, amount));
  };

  return (
    <motion.div
      initial={{ x: '100%' }}
      animate={{ x: 0 }}
      exit={{ x: '100%' }}
      className="fixed inset-0 z-50 bg-white flex flex-col"
    >
      <div className="flex items-center gap-4 px-6 py-8 border-b border-gray-100">
        <button onClick={onBack} className="p-2 -ml-2 text-gray-500 hover:text-black">
          <ArrowLeft size={24} />
        </button>
        <h2 className="text-xl font-semibold">Wallet</h2>
      </div>

      <div className="px-6 py-8">
        <p className="text-sm text-gray-500 uppercase tracking-wider">Balance</p>
        <p className="text-4xl font-bold mt-1">{formatCurrency(wallet.balance)}</p>
      </div>

      <div className="px-6 pb-6">
        <p className="text-sm font-medium text-gray-700 mb-3">Add funds</p>
        <div className="grid grid-cols-2 gap-2 mb-4">
          {TOP_UP_AMOUNTS.map((amount) => (
            <button
              key={amount}
              onClick={() => handleTopUp(amount)}
              className="flex items-center justify-center gap-2 py-3 rounded-xl border border-gray-200 hover:border-wixlo-green hover:bg-wixlo-green/5 transition-colors text-sm font-medium"
            >
              <Plus size={16} />
              {formatCurrency(amount)}
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          <input
            type="number"
            min="1"
            value={customAmount}
            onChange={(e) => setCustomAmount(e.target.value)}
            placeholder="Custom amount"
            className="flex-1 bg-gray-100 rounded-xl py-3 px-4 focus:outline-none focus:ring-1 focus:ring-black"
          />
          <button
            onClick={() => {
              const amount = parseInt(customAmount, 10);
              if (!isNaN(amount)) {
                handleTopUp(amount);
                setCustomAmount('');
              }
            }}
            className="px-5 py-3 bg-black text-white rounded-xl text-sm font-medium"
          >
            Add
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-6 border-t border-gray-100 pt-6">
        <p className="text-sm font-medium text-gray-700 mb-4">Transactions</p>
        {wallet.transactions.length === 0 ? (
          <p className="text-gray-400 text-sm">No transactions yet.</p>
        ) : (
          <div className="space-y-3">
            {wallet.transactions.map((tx) => (
              <div key={tx.id} className="flex items-center justify-between py-2 border-b border-gray-50">
                <div>
                  <p className="text-sm font-medium">{tx.description}</p>
                  <p className="text-xs text-gray-400">
                    {new Date(tx.timestamp).toLocaleString()}
                  </p>
                </div>
                <span className={`text-sm font-semibold ${tx.amount >= 0 ? 'text-wixlo-green' : 'text-gray-900'}`}>
                  {tx.amount >= 0 ? '+' : ''}{formatCurrency(Math.abs(tx.amount))}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </motion.div>
  );
}
