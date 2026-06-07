export type AIId = 'kai' | 'sia' | 'arlo' | 'drvane' | 'klaus' | 'sanjay' | 'emir' | 'musa' | 'fatima' | 'umar';

export interface AIPersonality {
  id: AIId;
  name: string;
  role: string;
  description: string;
  avatarUrl: string;
  systemPrompt: string;
  gender: 'male' | 'female';
  liveVoiceName: 'Puck' | 'Charon' | 'Kore' | 'Fenrir' | 'Zephyr';
  chatPrice: number;
  callPrice: number;
}

export type AppState = 'hub' | 'call' | 'chat' | 'wallet';

export type TransactionType = 'deposit' | 'chat' | 'call';

export interface Transaction {
  id: string;
  type: TransactionType;
  amount: number;
  description: string;
  timestamp: number;
}

export interface WalletState {
  balance: number;
  transactions: Transaction[];
}
