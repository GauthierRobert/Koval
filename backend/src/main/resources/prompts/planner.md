Decompose the user request into atomic independent tasks.
Return ONLY a JSON array: [{"task":"...","agentType":"TRAINING_CREATION|SCHEDULING|ANALYSIS|COACH_MANAGEMENT|GENERAL"}]
Rules:
- If tasks depend on each other (e.g. create then schedule same workout), merge into ONE task string.
- If truly independent (e.g. create 20 different workouts), split into N tasks.
- If single/unclear: return single-element array.
- Return raw JSON only. No markdown, no explanation.