
import { GoogleGenAI, LiveServerMessage, Modality } from "@google/genai";
import { arrayBufferToBase64, base64ToArrayBuffer, floatTo16BitPCM, pcmToFloat32 } from './audio-utils';

export interface GeminiLiveConfig {
  model: string;
  systemInstruction: string;
  voiceName: string;
  apiKey: string;
}

export class GeminiLiveStreamer {
  private ai: GoogleGenAI;
  private session: any = null;
  private audioContext: AudioContext | null = null;
  private nextPlayTime: number = 0;
  private audioBuffers: AudioBufferSourceNode[] = [];
  private onStatusChange?: (status: 'connecting' | 'open' | 'closed' | 'error') => void;
  private onTalkingChange?: (isTalking: boolean) => void;
  private onMessage?: (text: string) => void;

  constructor(
    private config: GeminiLiveConfig,
    callbacks: {
      onStatusChange?: (status: 'connecting' | 'open' | 'closed' | 'error') => void;
      onTalkingChange?: (isTalking: boolean) => void;
      onMessage?: (text: string) => void;
    }
  ) {
    this.ai = new GoogleGenAI({ apiKey: config.apiKey, apiVersion: 'v1alpha' });
    this.onStatusChange = callbacks.onStatusChange;
    this.onTalkingChange = callbacks.onTalkingChange;
    this.onMessage = callbacks.onMessage;
  }

  public async connect() {
    this.onStatusChange?.('connecting');
    
    this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 24000 });
    if (this.audioContext.state === 'suspended') {
      await this.audioContext.resume().catch(e => console.error("Could not resume audio:", e));
    }
    this.nextPlayTime = this.audioContext.currentTime;

    try {
      this.session = await this.ai.live.connect({
        model: this.config.model,
        callbacks: {
          onopen: () => {
            console.log("Gemini Live: SDK Session Opened");
            this.onStatusChange?.('open');
          },
          onmessage: async (message: LiveServerMessage) => {
            this.handleServerMessage(message);
          },
          onclose: () => {
            console.log("Gemini Live: SDK Session Closed");
            this.onStatusChange?.('closed');
            this.stopAllAudio();
          },
          onerror: (error: any) => {
            console.error("Gemini Live: SDK Session Error:", error);
            this.onStatusChange?.('error');
          }
        },
        config: {
          responseModalities: [Modality.AUDIO],
          speechConfig: {
            voiceConfig: { prebuiltVoiceConfig: { voiceName: this.config.voiceName } }
          },
          systemInstruction: this.config.systemInstruction,
          // Add transcription support for the UI
          inputAudioTranscription: {},
          outputAudioTranscription: {},
        }
      });
    } catch (e) {
      console.error("Failed to connect to Live API:", e);
      this.onStatusChange?.('error');
    }
  }

  public sendAudioChunk(float32Data: Float32Array) {
    if (!this.session) return;

    const pcm16 = floatTo16BitPCM(float32Data);
    const base64 = arrayBufferToBase64(pcm16);

    this.session.sendRealtimeInput({
      audio: {
        data: base64,
        mimeType: "audio/pcm;rate=16000"
      }
    });
  }

  public sendText(text: string) {
    if (!this.session) return;
    this.session.sendRealtimeInput({ text });
  }

  private handleServerMessage(message: LiveServerMessage) {
    console.log("Gemini Live: Received Server Message", message);
    const serverContent = message.serverContent;
    
    // 1. Handle Audio Data & Model Turn Parts
    const modelTurn = serverContent?.modelTurn;
    const parts = modelTurn?.parts || [];
    
    for (const part of parts) {
      if (part.inlineData?.data) {
        this.queueAudio(part.inlineData.data);
      }
      if (part.text) {
        console.log("Gemini Live: Model Text Output:", part.text);
        this.onMessage?.(part.text);
      }
    }

    // 2. Handle Transcription (if specified in different fields)
    const msg = message as any;
    // Model's output transcription (text of what AI said)
    if (msg.serverContent?.modelTurn?.parts?.some((p: any) => p.text)) {
      // Already handled in loop, but double checking for non-standard formats
    }

    // 3. Handle Interruption
    if (serverContent?.interrupted) {
      console.log("Gemini Live: Session Interrupted");
      this.stopAllAudio();
    }
  }

  private async queueAudio(base64Data: string) {
    if (!this.audioContext) return;
    
    if (this.audioContext.state === 'suspended') {
      await this.audioContext.resume().catch(() => {});
    }

    const arrayBuffer = base64ToArrayBuffer(base64Data);
    const int16Array = new Int16Array(arrayBuffer);
    const float32Array = pcmToFloat32(int16Array);

    const buffer = this.audioContext.createBuffer(1, float32Array.length, 24000);
    buffer.getChannelData(0).set(float32Array);

    const source = this.audioContext.createBufferSource();
    source.buffer = buffer;
    source.connect(this.audioContext.destination);

    // Audio Scheduling logic
    const startTime = Math.max(this.nextPlayTime, this.audioContext.currentTime + 0.05); // Small buffer
    source.start(startTime);
    
    this.audioBuffers.push(source);
    this.onTalkingChange?.(true);

    source.onended = () => {
      this.audioBuffers = this.audioBuffers.filter(s => s !== source);
      if (this.audioBuffers.length === 0) {
        this.onTalkingChange?.(false);
      }
    };

    this.nextPlayTime = startTime + buffer.duration;
  }

  private stopAllAudio() {
    this.audioBuffers.forEach(source => {
      try { source.stop(); } catch (e) {}
    });
    this.audioBuffers = [];
    if (this.audioContext) {
      this.nextPlayTime = this.audioContext.currentTime;
    }
    this.onTalkingChange?.(false);
  }

  public disconnect() {
    this.stopAllAudio();
    if (this.session) {
      this.session.close();
      this.session = null;
    }
    if (this.audioContext) {
      this.audioContext.close();
      this.audioContext = null;
    }
  }
}
