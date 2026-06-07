import { motion, AnimatePresence } from 'motion/react';
import { Mic, MicOff, PhoneOff } from 'lucide-react';
import { useState, useEffect, useRef, useCallback } from 'react';
import { AIPersonality } from '../types';
import { fetchGeminiApiKey } from '../lib/gemini';
import { GeminiLiveStreamer } from '../lib/gemini-live';
import { callCost, canAfford, formatCurrency } from '../lib/wallet';

interface CallScreenProps {
  ai: AIPersonality;
  balance: number;
  onEndCall: (durationSeconds: number) => void;
}

type CallStatus = 'connecting' | 'ringing' | 'connected' | 'error';

export default function CallScreen({ ai, balance, onEndCall }: CallScreenProps) {
  const [status, setStatus] = useState<CallStatus>('connecting');
  const [isMuted, setIsMuted] = useState(false);
  const [isTalking, setIsTalking] = useState(false);
  const [isAiListening, setIsAiListening] = useState(false);
  const [duration, setDuration] = useState(0);
  const [transcriptChunks, setTranscriptChunks] = useState<string[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const streamerRef = useRef<GeminiLiveStreamer | null>(null);
  const micProcessorRef = useRef<ScriptProcessorNode | null>(null);
  const micStreamRef = useRef<MediaStream | null>(null);
  const ringingInterval = useRef<number | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const isMutedRef = useRef(isMuted);
  const statusRef = useRef(status);
  const durationRef = useRef(0);
  const balanceRef = useRef(balance);
  const onEndCallRef = useRef(onEndCall);

  isMutedRef.current = isMuted;
  statusRef.current = status;
  durationRef.current = duration;
  balanceRef.current = balance;
  onEndCallRef.current = onEndCall;

  const cleanup = useCallback(() => {
    if (streamerRef.current) {
      streamerRef.current.disconnect();
      streamerRef.current = null;
    }
    if (micProcessorRef.current) {
      micProcessorRef.current.disconnect();
      micProcessorRef.current = null;
    }
    if (micStreamRef.current) {
      micStreamRef.current.getTracks().forEach((track) => track.stop());
      micStreamRef.current = null;
    }
    if (audioContextRef.current) {
      audioContextRef.current.close().catch(() => {});
      audioContextRef.current = null;
    }
  }, []);

  const stopRinging = useCallback(() => {
    if (ringingInterval.current) {
      clearInterval(ringingInterval.current);
      ringingInterval.current = null;
    }
    if (audioContextRef.current) {
      audioContextRef.current.close().catch(() => {});
      audioContextRef.current = null;
    }
  }, []);

  const startMicrophone = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });
      micStreamRef.current = stream;

      const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 16000 });
      const source = audioCtx.createMediaStreamSource(stream);
      const processor = audioCtx.createScriptProcessor(2048, 1, 1);
      micProcessorRef.current = processor;

      processor.onaudioprocess = (e) => {
        if (isMutedRef.current || !streamerRef.current) {
          setIsAiListening(false);
          return;
        }

        const inputData = e.inputBuffer.getChannelData(0);
        let sum = 0;
        for (let i = 0; i < inputData.length; i++) sum += inputData[i] * inputData[i];
        const volume = Math.sqrt(sum / inputData.length);
        setIsAiListening(volume > 0.012);

        streamerRef.current.sendAudioChunk(inputData);
      };

      source.connect(processor);
      processor.connect(audioCtx.destination);

      if (audioCtx.state === 'suspended') {
        await audioCtx.resume();
      }
      audioContextRef.current = audioCtx;
    } catch (err) {
      console.error('Microphone error:', err);
      setStatus('error');
      setErrorMessage('Microphone access denied.');
    }
  }, []);

  const startLiveSession = useCallback(async () => {
    const apiKey = await fetchGeminiApiKey();
    if (!apiKey) {
      setStatus('error');
      setErrorMessage('Add GEMINI_API_KEY to .env.local and restart the server.');
      return;
    }

    const streamer = new GeminiLiveStreamer(
      {
        apiKey,
        model: 'gemini-2.5-flash-native-audio-preview-12-2025',
        systemInstruction: ai.systemPrompt,
        voiceName: ai.liveVoiceName,
      },
      {
        onStatusChange: (newStatus) => {
          if (newStatus === 'error') {
            setStatus('error');
            setErrorMessage('Connection error.');
          }
          if (newStatus === 'open') {
            startMicrophone();
            const greeting = ai.gender === 'male'
              ? `Hello, I am ${ai.name}. How can I help you?`
              : `Hello, I am ${ai.name}. How can I assist you today?`;
            setTimeout(() => streamer.sendText(greeting), 500);
          }
        },
        onTalkingChange: setIsTalking,
        onMessage: (text) => {
          setTranscriptChunks((prev) => {
            const last = prev[prev.length - 1] || '';
            if (text.length < 25 && last.length < 80) {
              const updated = [...prev];
              if (updated.length === 0) return [text];
              updated[updated.length - 1] = last + (text.startsWith(' ') || last.endsWith(' ') ? '' : ' ') + text;
              return updated.slice(-2);
            }
            return [...prev, text].slice(-2);
          });
        },
      },
    );

    streamerRef.current = streamer;
    await streamer.connect();
  }, [ai, startMicrophone]);

  const startRinging = useCallback(() => {
    try {
      const ctx = new (window.AudioContext || (window as any).webkitAudioContext)();
      const playBeep = () => {
        if (statusRef.current === 'connected') return;
        const createTone = (startTime: number) => {
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.type = 'sine';
          osc.frequency.setValueAtTime(400, startTime);
          gain.gain.setValueAtTime(0, startTime);
          gain.gain.linearRampToValueAtTime(0.1, startTime + 0.05);
          gain.gain.linearRampToValueAtTime(0, startTime + 0.4);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(startTime);
          osc.stop(startTime + 0.5);
        };
        createTone(ctx.currentTime);
        createTone(ctx.currentTime + 0.6);
      };
      ringingInterval.current = window.setInterval(playBeep, 3000);
      playBeep();
      audioContextRef.current = ctx;
    } catch (e) {
      console.error('Audio error:', e);
    }
  }, []);

  const handleEnd = useCallback(() => {
    stopRinging();
    cleanup();
    onEndCallRef.current(durationRef.current);
  }, [cleanup, stopRinging]);

  useEffect(() => {
    let timer: number;
    if (status === 'connected') {
      timer = window.setInterval(() => {
        setDuration((prev) => {
          const next = prev + 1;
          const projected = callCost(next, ai.callPrice);
          if (projected > balanceRef.current) {
            window.setTimeout(() => handleEnd(), 0);
          }
          return next;
        });
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [status, ai.callPrice, handleEnd]);

  useEffect(() => {
    if (!canAfford({ balance, transactions: [] }, ai.callPrice)) {
      setStatus('error');
      setErrorMessage(`Insufficient balance. Calls start at ${formatCurrency(ai.callPrice)}/min.`);
      return;
    }

    const connectTimer = setTimeout(() => {
      setStatus('ringing');
      startRinging();
    }, 800);

    const ringTimer = setTimeout(() => {
      setStatus('connected');
      stopRinging();
      startLiveSession().catch((err) => {
        console.error('Live session failed:', err);
        setStatus('error');
      });
    }, 2500);

    return () => {
      clearTimeout(connectTimer);
      clearTimeout(ringTimer);
      stopRinging();
      cleanup();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const projectedCost = callCost(duration, ai.callPrice);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 bg-black flex flex-col items-center justify-center overflow-hidden"
    >
      <div className="absolute inset-0 flex items-center justify-center">
        <motion.img
          src={ai.avatarUrl}
          alt={ai.name}
          animate={{
            scale: isTalking ? [1.05, 1.08, 1.05] : 1.05,
          }}
          transition={{ duration: isTalking ? 1.5 : 8, repeat: Infinity, ease: 'easeInOut' }}
          className="w-full h-full object-cover opacity-60"
          referrerPolicy="no-referrer"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black via-black/40 to-black/60" />
      </div>

      <div className="absolute top-16 left-0 right-0 flex flex-col items-center gap-2 pointer-events-none z-10">
        <h2 className="text-white text-3xl font-semibold tracking-tight">{ai.name}</h2>
        <div className="flex flex-col items-center gap-1">
          {status === 'connected' ? (
            <>
              <span className="text-white/80 font-mono text-lg">{formatDuration(duration)}</span>
              <span className="text-white/50 text-xs">{formatCurrency(projectedCost)}</span>
            </>
          ) : status === 'error' ? (
            <span className="text-wixlo-red font-medium text-sm">{errorMessage}</span>
          ) : (
            <span className="text-wixlo-green text-sm">
              {status === 'connecting' ? 'Connecting...' : 'Ringing...'}
            </span>
          )}
          {status === 'connected' && (
            <span className="text-white/60 text-xs mt-1">
              {isTalking ? 'Speaking' : isAiListening ? 'Listening' : 'Ready'}
            </span>
          )}
        </div>
      </div>

      <AnimatePresence>
        {status === 'connected' && transcriptChunks.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="absolute inset-x-8 bottom-44 text-center z-30"
          >
            <div className="bg-black/50 backdrop-blur-md rounded-2xl p-4 max-w-lg mx-auto">
              <p className="text-white/90 text-base leading-relaxed">
                {transcriptChunks[transcriptChunks.length - 1]}
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="absolute bottom-14 px-10 py-5 glass rounded-[40px] flex items-center gap-10 shadow-2xl z-20">
        {status === 'connected' && (
          <button
            onClick={() => setIsMuted(!isMuted)}
            className={`p-4 rounded-full transition-all ${isMuted ? 'bg-white/20 text-white' : 'text-white hover:bg-white/10'}`}
          >
            {isMuted ? <MicOff size={24} /> : <Mic size={24} />}
          </button>
        )}

        <button
          onClick={handleEnd}
          className="p-7 bg-wixlo-red rounded-full text-white shadow-lg hover:bg-red-600 transition-all"
        >
          <PhoneOff size={32} />
        </button>
      </div>
    </motion.div>
  );
}
