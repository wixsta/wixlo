import dotenv from "dotenv";
import express from "express";

dotenv.config({ path: ".env.local" });
dotenv.config();
import path from "path";
import { fileURLToPath } from "url";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI } from "@google/genai";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function getApiKey(): string | undefined {
  return process.env.GEMINI_API_KEY?.trim() || undefined;
}

async function startServer() {
  const app = express();
  const PORT = Number(process.env.PORT) || 3000;
  const isProduction = process.env.NODE_ENV === "production";

  app.use(express.json());

  app.get("/api/health", (_req, res) => {
    res.json({ status: "ok" });
  });

  app.get("/api/status", (_req, res) => {
    res.json({ configured: Boolean(getApiKey()) });
  });

  app.post("/api/chat", async (req, res) => {
    const apiKey = getApiKey();
    if (!apiKey) {
      res.status(503).json({
        error: "GEMINI_API_KEY is not configured. Add it to .env.local and restart the server.",
      });
      return;
    }

    const { prompt, systemInstruction } = req.body as {
      prompt?: string;
      systemInstruction?: string;
    };

    if (!prompt?.trim()) {
      res.status(400).json({ error: "prompt is required" });
      return;
    }

    try {
      const genAI = new GoogleGenAI({ apiKey });
      const response = await genAI.models.generateContent({
        model: "gemini-2.0-flash",
        contents: prompt,
        config: {
          systemInstruction: systemInstruction || undefined,
          maxOutputTokens: 200,
        },
      });

      res.json({ text: response.text || "I'm not sure how to respond to that." });
    } catch (error) {
      console.error("Gemini chat error:", error);
      res.status(500).json({ error: "Failed to generate a response. Check your API key and try again." });
    }
  });

  // Live voice calls use the browser SDK; key is fetched at runtime (not baked into the bundle).
  app.get("/api/gemini-key", (_req, res) => {
    const apiKey = getApiKey();
    if (!apiKey) {
      res.status(503).json({
        error: "GEMINI_API_KEY is not configured. Add it to .env.local and restart the server.",
      });
      return;
    }
    res.json({ apiKey });
  });

  if (!isProduction) {
    const vite = await createViteServer({
      root: __dirname,
      configFile: path.join(__dirname, "vite.config.ts"),
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(__dirname, "dist");
    app.use(express.static(distPath));
    app.get("*", (_req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
    if (!getApiKey()) {
      console.warn(
        "Warning: GEMINI_API_KEY is not set. Copy .env.example to .env.local and add your key."
      );
    }
  });
}

startServer().catch((err) => {
  console.error("Failed to start server:", err);
  process.exit(1);
});
