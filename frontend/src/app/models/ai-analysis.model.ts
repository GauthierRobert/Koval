export interface Provenance {
    source: 'mcp' | 'web' | 'ai-chat' | string;
    mcpClientName?: string | null;
    model?: string | null;
    generatedAt: string;
}

export interface AiAnalysis {
    id: string;
    sessionId: string;
    athleteId: string;
    authorId: string;
    summary: string;
    body: string;
    highlights?: string[] | null;
    provenance: Provenance;
    createdAt: string;
    updatedAt: string;
}
