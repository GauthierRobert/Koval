import { type APIRequestContext } from '@playwright/test';

const API_URL = 'http://localhost:8080';

function headers(token: string) {
  return { Authorization: `Bearer ${token}` };
}

// ---------- Trainings ----------

export async function createTraining(
  api: APIRequestContext,
  token: string,
  data: {
    title: string;
    description?: string;
    sportType?: string;
    trainingType?: string;
    workoutBlocks?: any[];
  }
): Promise<{ id: string; title: string }> {
  const response = await api.post(`${API_URL}/api/trainings`, {
    headers: headers(token),
    data: {
      title: data.title,
      description: data.description ?? 'E2E test training',
      sportType: data.sportType ?? 'CYCLING',
      trainingType: data.trainingType ?? 'ENDURANCE',
      workoutBlocks: data.workoutBlocks ?? [
        {
          type: 'WARMUP',
          durationSeconds: 300,
          powerTargetPercent: 50,
          label: 'Warm Up',
        },
        {
          type: 'STEADY',
          durationSeconds: 1200,
          powerTargetPercent: 75,
          label: 'Main Set',
        },
        {
          type: 'COOLDOWN',
          durationSeconds: 300,
          powerTargetPercent: 40,
          label: 'Cool Down',
        },
      ],
    },
  });
  if (!response.ok()) {
    throw new Error(`createTraining failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function deleteTraining(
  api: APIRequestContext,
  token: string,
  trainingId: string
): Promise<void> {
  await api.delete(`${API_URL}/api/trainings/${trainingId}`, {
    headers: headers(token),
  });
}

// ---------- Clubs ----------

export async function createClub(
  api: APIRequestContext,
  token: string,
  data: {
    name: string;
    description?: string;
    visibility?: 'PUBLIC' | 'PRIVATE';
    location?: string;
  }
): Promise<{ id: string; name: string }> {
  const response = await api.post(`${API_URL}/api/clubs`, {
    headers: headers(token),
    data: {
      name: data.name,
      description: data.description ?? 'E2E test club',
      visibility: data.visibility ?? 'PUBLIC',
      location: data.location ?? 'Test Location',
    },
  });
  if (!response.ok()) {
    throw new Error(`createClub failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function deleteClub(
  api: APIRequestContext,
  token: string,
  clubId: string
): Promise<void> {
  await api.delete(`${API_URL}/api/clubs/${clubId}`, {
    headers: headers(token),
  });
}

export async function joinClub(
  api: APIRequestContext,
  token: string,
  clubId: string
): Promise<{ id: string; role: string; status: string }> {
  const response = await api.post(`${API_URL}/api/clubs/${clubId}/join`, {
    headers: headers(token),
  });
  if (!response.ok()) {
    throw new Error(`joinClub failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function leaveClub(
  api: APIRequestContext,
  token: string,
  clubId: string
): Promise<void> {
  await api.delete(`${API_URL}/api/clubs/${clubId}/leave`, {
    headers: headers(token),
  });
}

// ---------- Club Members ----------

export async function getClubMembers(
  api: APIRequestContext,
  token: string,
  clubId: string
): Promise<any[]> {
  const response = await api.get(`${API_URL}/api/clubs/${clubId}/members`, {
    headers: headers(token),
  });
  if (!response.ok()) {
    throw new Error(`getClubMembers failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function approveMember(
  api: APIRequestContext,
  token: string,
  clubId: string,
  membershipId: string
): Promise<void> {
  const response = await api.post(
    `${API_URL}/api/clubs/${clubId}/members/${membershipId}/approve`,
    { headers: headers(token) }
  );
  if (!response.ok()) {
    throw new Error(`approveMember failed: ${response.status()} ${await response.text()}`);
  }
}

export async function updateMemberRole(
  api: APIRequestContext,
  token: string,
  clubId: string,
  membershipId: string,
  role: string
): Promise<void> {
  const response = await api.put(
    `${API_URL}/api/clubs/${clubId}/members/${membershipId}/role`,
    { headers: headers(token), data: { role } }
  );
  if (!response.ok()) {
    throw new Error(`updateMemberRole failed: ${response.status()} ${await response.text()}`);
  }
}

// ---------- Club Groups ----------

export async function createClubGroup(
  api: APIRequestContext,
  token: string,
  clubId: string,
  name: string
): Promise<{ id: string; name: string }> {
  const response = await api.post(`${API_URL}/api/clubs/${clubId}/groups`, {
    headers: headers(token),
    data: { name },
  });
  if (!response.ok()) {
    throw new Error(`createClubGroup failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function addMemberToClubGroup(
  api: APIRequestContext,
  token: string,
  clubId: string,
  groupId: string,
  userId: string
): Promise<void> {
  const response = await api.post(
    `${API_URL}/api/clubs/${clubId}/groups/${groupId}/members/${userId}`,
    { headers: headers(token) }
  );
  if (!response.ok()) {
    throw new Error(`addMemberToClubGroup failed: ${response.status()} ${await response.text()}`);
  }
}

// ---------- Club Sessions ----------

export async function createClubSession(
  api: APIRequestContext,
  token: string,
  clubId: string,
  data: {
    title: string;
    sport?: string;
    scheduledAt: string;
    location?: string;
    description?: string;
    maxParticipants?: number;
    durationMinutes?: number;
    category?: 'SCHEDULED' | 'OPEN';
    clubGroupId?: string;
  }
): Promise<{ id: string; title: string }> {
  const response = await api.post(`${API_URL}/api/clubs/${clubId}/sessions`, {
    headers: headers(token),
    data: {
      title: data.title,
      sport: data.sport ?? 'CYCLING',
      scheduledAt: data.scheduledAt,
      location: data.location ?? 'Test Location',
      description: data.description ?? 'E2E test session',
      maxParticipants: data.maxParticipants,
      durationMinutes: data.durationMinutes ?? 60,
      category: data.category ?? 'SCHEDULED',
      clubGroupId: data.clubGroupId,
    },
  });
  if (!response.ok()) {
    throw new Error(`createClubSession failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function joinClubSession(
  api: APIRequestContext,
  token: string,
  clubId: string,
  sessionId: string
): Promise<void> {
  const response = await api.post(
    `${API_URL}/api/clubs/${clubId}/sessions/${sessionId}/join`,
    { headers: headers(token) }
  );
  if (!response.ok()) {
    throw new Error(`joinClubSession failed: ${response.status()} ${await response.text()}`);
  }
}

export async function linkTrainingToSession(
  api: APIRequestContext,
  token: string,
  clubId: string,
  sessionId: string,
  trainingId: string,
  clubGroupId?: string
): Promise<void> {
  const response = await api.put(
    `${API_URL}/api/clubs/${clubId}/sessions/${sessionId}/link-training`,
    {
      headers: headers(token),
      data: { trainingId, clubGroupId: clubGroupId ?? null },
    }
  );
  if (!response.ok()) {
    throw new Error(
      `linkTrainingToSession failed: ${response.status()} ${await response.text()}`
    );
  }
}

// ---------- Club Invite Codes ----------

export async function createClubInviteCode(
  api: APIRequestContext,
  token: string,
  clubId: string,
  options?: { maxUses?: number; clubGroupId?: string }
): Promise<{ id: string; code: string }> {
  const response = await api.post(`${API_URL}/api/clubs/${clubId}/invite-codes`, {
    headers: headers(token),
    data: {
      maxUses: options?.maxUses ?? 0,
      clubGroupId: options?.clubGroupId ?? null,
    },
  });
  if (!response.ok()) {
    throw new Error(`createClubInviteCode failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function redeemInviteCode(
  api: APIRequestContext,
  token: string,
  code: string
): Promise<void> {
  const response = await api.post(`${API_URL}/api/coach/redeem-invite`, {
    headers: headers(token),
    data: { code },
  });
  if (!response.ok()) {
    throw new Error(`redeemInviteCode failed: ${response.status()} ${await response.text()}`);
  }
}

// ---------- Coach Operations ----------

export async function addAthleteToCoachGroup(
  api: APIRequestContext,
  token: string,
  athleteId: string,
  groupName: string
): Promise<void> {
  const response = await api.post(
    `${API_URL}/api/coach/athletes/${athleteId}/groups`,
    { headers: headers(token), data: { group: groupName } }
  );
  if (!response.ok()) {
    throw new Error(
      `addAthleteToCoachGroup failed: ${response.status()} ${await response.text()}`
    );
  }
}

export async function assignTraining(
  api: APIRequestContext,
  token: string,
  trainingId: string,
  athleteIds: string[],
  scheduledDate: string,
  options?: { notes?: string; clubId?: string }
): Promise<any[]> {
  const response = await api.post(`${API_URL}/api/coach/assign`, {
    headers: headers(token),
    data: {
      trainingId,
      athleteIds,
      scheduledDate,
      notes: options?.notes ?? '',
      clubId: options?.clubId ?? null,
    },
  });
  if (!response.ok()) {
    throw new Error(`assignTraining failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function getCoachInviteCodes(
  api: APIRequestContext,
  token: string
): Promise<any[]> {
  const response = await api.get(`${API_URL}/api/coach/invite-codes`, {
    headers: headers(token),
  });
  if (!response.ok()) {
    throw new Error(`getCoachInviteCodes failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function generateCoachInviteCode(
  api: APIRequestContext,
  token: string,
  groupIds: string[],
  maxUses?: number
): Promise<{ id: string; code: string }> {
  const response = await api.post(`${API_URL}/api/coach/invite-codes`, {
    headers: headers(token),
    data: { groupIds, maxUses: maxUses ?? 0 },
  });
  if (!response.ok()) {
    throw new Error(
      `generateCoachInviteCode failed: ${response.status()} ${await response.text()}`
    );
  }
  return response.json();
}

// ---------- Schedule ----------

export async function getSchedule(
  api: APIRequestContext,
  token: string,
  start: string,
  end: string
): Promise<any[]> {
  const response = await api.get(
    `${API_URL}/api/schedule?start=${start}&end=${end}&includeClubSessions=true`,
    { headers: headers(token) }
  );
  if (!response.ok()) {
    throw new Error(`getSchedule failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function deleteScheduledWorkout(
  api: APIRequestContext,
  token: string,
  workoutId: string
): Promise<void> {
  await api.delete(`${API_URL}/api/schedule/${workoutId}`, {
    headers: headers(token),
  });
}

// ---------- Recurring Sessions ----------

// ---------- Sessions / History ----------

export async function createSession(
  api: APIRequestContext,
  token: string,
  data: {
    title: string;
    sportType?: string;
    totalDurationSeconds?: number;
    avgPower?: number;
    avgHR?: number;
    avgCadence?: number;
    avgSpeed?: number;
    completedAt?: string;
    tss?: number;
    intensityFactor?: number;
    rpe?: number;
    blockSummaries?: any[];
  }
): Promise<{ id: string; title: string }> {
  const response = await api.post(`${API_URL}/api/sessions`, {
    headers: headers(token),
    data: {
      title: data.title,
      sportType: data.sportType ?? 'CYCLING',
      totalDurationSeconds: data.totalDurationSeconds ?? 3600,
      avgPower: data.avgPower ?? 200,
      avgHR: data.avgHR ?? 145,
      avgCadence: data.avgCadence ?? 85,
      avgSpeed: data.avgSpeed ?? 8.5,
      completedAt: data.completedAt ?? new Date().toISOString(),
      tss: data.tss,
      intensityFactor: data.intensityFactor,
      rpe: data.rpe,
      blockSummaries: data.blockSummaries ?? [],
    },
  });
  if (!response.ok()) {
    throw new Error(`createSession failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function deleteSession(
  api: APIRequestContext,
  token: string,
  sessionId: string
): Promise<void> {
  await api.delete(`${API_URL}/api/sessions/${sessionId}`, {
    headers: headers(token),
  });
}

export async function getSessions(
  api: APIRequestContext,
  token: string
): Promise<any[]> {
  const response = await api.get(`${API_URL}/api/sessions`, {
    headers: headers(token),
  });
  if (!response.ok()) {
    throw new Error(`getSessions failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function updateSessionRpe(
  api: APIRequestContext,
  token: string,
  sessionId: string,
  rpe: number
): Promise<any> {
  const response = await api.patch(`${API_URL}/api/sessions/${sessionId}`, {
    headers: headers(token),
    data: { rpe },
  });
  if (!response.ok()) {
    throw new Error(`updateSessionRpe failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function getSessionPmc(
  api: APIRequestContext,
  token: string,
  from: string,
  to: string
): Promise<any[]> {
  const response = await api.get(
    `${API_URL}/api/sessions/pmc?from=${from}&to=${to}`,
    { headers: headers(token) }
  );
  if (!response.ok()) {
    throw new Error(`getSessionPmc failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function getPowerCurve(
  api: APIRequestContext,
  token: string,
  from: string,
  to: string
): Promise<Record<string, number>> {
  const response = await api.get(
    `${API_URL}/api/sessions/power-curve?from=${from}&to=${to}`,
    { headers: headers(token) }
  );
  if (!response.ok()) {
    throw new Error(`getPowerCurve failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function getVolume(
  api: APIRequestContext,
  token: string,
  from: string,
  to: string,
  groupBy: 'week' | 'month' = 'week'
): Promise<any[]> {
  const response = await api.get(
    `${API_URL}/api/sessions/volume?from=${from}&to=${to}&groupBy=${groupBy}`,
    { headers: headers(token) }
  );
  if (!response.ok()) {
    throw new Error(`getVolume failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function getPersonalRecords(
  api: APIRequestContext,
  token: string
): Promise<Record<string, number>> {
  const response = await api.get(`${API_URL}/api/sessions/personal-records`, {
    headers: headers(token),
  });
  if (!response.ok()) {
    throw new Error(`getPersonalRecords failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

// ---------- Club Feed ----------

export async function createAnnouncement(
  api: APIRequestContext,
  token: string,
  clubId: string,
  content: string
): Promise<any> {
  const response = await api.post(
    `${API_URL}/api/clubs/${clubId}/feed/announcements`,
    { headers: headers(token), data: { content } }
  );
  if (!response.ok()) {
    throw new Error(`createAnnouncement failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

export async function getFeedEvents(
  api: APIRequestContext,
  token: string,
  clubId: string,
  page: number = 0
): Promise<{ pinned: any[]; items: any[]; page: number; hasMore: boolean }> {
  const response = await api.get(
    `${API_URL}/api/clubs/${clubId}/feed?page=${page}`,
    { headers: headers(token) }
  );
  if (!response.ok()) {
    throw new Error(`getFeedEvents failed: ${response.status()} ${await response.text()}`);
  }
  return response.json();
}

// ---------- Recurring Sessions ----------

export async function createRecurringTemplate(
  api: APIRequestContext,
  token: string,
  clubId: string,
  data: {
    title: string;
    sport?: string;
    dayOfWeek: string;
    timeOfDay: string;
    location?: string;
    description?: string;
    durationMinutes?: number;
    category?: string;
  }
): Promise<{ id: string; title: string }> {
  const response = await api.post(`${API_URL}/api/clubs/${clubId}/recurring-sessions`, {
    headers: headers(token),
    data: {
      title: data.title,
      sport: data.sport ?? 'CYCLING',
      dayOfWeek: data.dayOfWeek,
      timeOfDay: data.timeOfDay,
      location: data.location ?? 'Test Location',
      description: data.description ?? 'E2E recurring session',
      durationMinutes: data.durationMinutes ?? 60,
      category: data.category ?? 'SCHEDULED',
    },
  });
  if (!response.ok()) {
    throw new Error(
      `createRecurringTemplate failed: ${response.status()} ${await response.text()}`
    );
  }
  return response.json();
}
