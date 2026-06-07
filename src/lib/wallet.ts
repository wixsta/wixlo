import { Transaction, TransactionType, WalletState } from '../types';

const STORAGE_KEY = 'wixlo-wallet';
const INITIAL_BALANCE = 1000;

function defaultWallet(): WalletState {
  return {
    balance: INITIAL_BALANCE,
    transactions: [{
      id: 'seed',
      type: 'deposit',
      amount: INITIAL_BALANCE,
      description: 'Welcome credit',
      timestamp: Date.now(),
    }],
  };
}

export function loadWallet(): WalletState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaultWallet();
    const parsed = JSON.parse(raw) as WalletState;
    if (typeof parsed.balance !== 'number' || !Array.isArray(parsed.transactions)) {
      return defaultWallet();
    }
    return parsed;
  } catch {
    return defaultWallet();
  }
}

export function saveWallet(state: WalletState): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function canAfford(wallet: WalletState, amount: number): boolean {
  return wallet.balance >= amount;
}

export function addFunds(wallet: WalletState, amount: number): WalletState {
  const next: WalletState = {
    balance: wallet.balance + amount,
    transactions: [{
      id: crypto.randomUUID(),
      type: 'deposit',
      amount,
      description: 'Wallet top-up',
      timestamp: Date.now(),
    }, ...wallet.transactions],
  };
  saveWallet(next);
  return next;
}

export function charge(
  wallet: WalletState,
  amount: number,
  type: TransactionType,
  description: string,
): { success: boolean; wallet: WalletState } {
  if (amount <= 0) return { success: true, wallet };
  if (!canAfford(wallet, amount)) return { success: false, wallet };

  const next: WalletState = {
    balance: wallet.balance - amount,
    transactions: [{
      id: crypto.randomUUID(),
      type,
      amount: -amount,
      description,
      timestamp: Date.now(),
    }, ...wallet.transactions],
  };
  saveWallet(next);
  return { success: true, wallet: next };
}

export function formatCurrency(amount: number): string {
  return `₦${amount.toLocaleString()}`;
}

export function callCost(durationSeconds: number, pricePerMinute: number): number {
  const minutes = Math.max(1, Math.ceil(durationSeconds / 60));
  return minutes * pricePerMinute;
}
