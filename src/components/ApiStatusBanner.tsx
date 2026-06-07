import { useEffect, useState } from 'react';

export default function ApiStatusBanner() {
  const [configured, setConfigured] = useState<boolean | null>(null);

  useEffect(() => {
    fetch('/api/status')
      .then((res) => res.json())
      .then((data: { configured?: boolean }) => setConfigured(Boolean(data.configured)))
      .catch(() => setConfigured(false));
  }, []);

  if (configured !== false) return null;

  return (
    <div className="bg-amber-50 border-b border-amber-200 px-4 py-3 text-center text-sm text-amber-900">
      Gemini API key missing. Copy <code className="font-mono text-xs">.env.example</code> to{' '}
      <code className="font-mono text-xs">.env.local</code>, set{' '}
      <code className="font-mono text-xs">GEMINI_API_KEY</code>, then restart{' '}
      <code className="font-mono text-xs">npm run dev</code>.
    </div>
  );
}
