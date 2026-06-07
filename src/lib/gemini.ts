export async function getAIResponse(prompt: string, aiPersonality: string) {
  try {
    const res = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt, systemInstruction: aiPersonality }),
    });

    const data = (await res.json()) as { text?: string; error?: string };

    if (!res.ok) {
      return data.error || "The service is temporarily unavailable. Please try again in a moment.";
    }

    return data.text || "I'm not sure how to respond to that.";
  } catch (error) {
    console.error("Chat API error:", error);
    return "Something went wrong. Let's try again later.";
  }
}

export async function fetchGeminiApiKey(): Promise<string | null> {
  try {
    const res = await fetch("/api/gemini-key");
    if (!res.ok) return null;
    const data = (await res.json()) as { apiKey?: string };
    return data.apiKey?.trim() || null;
  } catch {
    return null;
  }
}
