export interface CommonResponse<T> {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  traceId: string;
  data: T;
}

export interface AuthState {
  authenticated: boolean;
  next_action?: string;
  sub?: string;
  email?: string;
  email_verified?: boolean;
  reason?: string;
}

export interface VerifyEmailResendResult {
  ok: boolean;
  error?: string;
  message?: string;
  email?: string;
}

export interface VerifyEmailContext {
  kind: string;
  dbUser: boolean;
  email_verified?: boolean;
  created_at?: string;
}

export interface UpdateProfileRequestBody {
  nickname?: string | null;
  metadata?: Record<string, unknown> | null;
  updatedAtExpected?: string | null; // ISO string (Instant)
}

export interface UserProfileResponse {
  userId: string;
  email: string | null;
  emailVerified: boolean;
  nickname: string | null;
  nicknameSeq: number | null;
  nicknameHandle: string | null;
  isActive: boolean;
  loginCount: number;
  metadata: unknown;
  paintCountTotal: number;
  lastLoginAt: string | null;
  blockedAt: string | null;
  blockedReason: string | null;
  createdAt: string;
  updatedAt: string;
  roles: string[];
  identities: UserIdentityDto[];
}

export interface UserIdentityDto {
  id: string;
  userId: string;
  provider: string;
  providerTenant: string;
  providerSub: string;
  emailAtProvider: string | null;
  displayNameAtProvider: string | null;
  lastSyncAt: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
}

export interface AuthEventResponse {
  id: string;
  provider: string | null;
  kind: string | null;
  detail: string | null;
  ip: string | null;
  userAgent: string | null;
  createdAt: string;
}

// Admin API: user events query params
export interface AdminUserEventsParams {
  limit?: number;
  beforeCreatedExclusive?: string; // ISO
  beforeUuidExclusive?: string;
}

export interface PublicProfileResponse {
  userId: string;
  nickname: string | null;
  nicknameSeq: number | null;
  nicknameHandle: string | null;
  metadata: unknown;
  createdAt: string;
}

// ========== Clan domain types ==========
export type ClanJoinPolicy = 'OPEN' | 'APPROVAL' | 'INVITE_ONLY';
export type ClanMemberRole = 'MASTER' | 'OFFICER' | 'MEMBER';
export type ClanMemberStatus =
  | 'ACTIVE'
  | 'INVITED'
  | 'PENDING'
  | 'LEFT'
  | 'KICKED'
  | 'BANNED';
export type ClanJoinRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'EXPIRED';

export interface ClanSummary {
  id: string;
  handle: string;
  name: string;
  isPublic: boolean;
  memberCount: number;
  paintCountTotal: number;
}

export interface ClanDetail {
  id: string;
  handle: string;
  name: string;
  description?: string | null;
  ownerId: string;
  isPublic: boolean;
  maxMembers: number;
  memberCount: number;
  paintCountTotal: number;
}

export interface ClanSummaryResponse {
  id: string;
  name: string;
  description: string | null;
  joinPolicy: ClanJoinPolicy;
  isPublic: boolean;
  memberCount: number;
  paintCountTotal: number;
}

export interface PublicClanSummaryResponse {
  clan: ClanSummaryResponse;
  myJoinRequestStatus?: ClanJoinRequestStatus | null;
}

export interface MyClanResponse {
  clan: ClanSummaryResponse;
  role: ClanMemberRole;
}

export interface ClanMemberView {
  id: string;
  userId: string;
  nickname: string;
  nicknameHandle?: string | null;
  role: ClanMemberRole;
  status: ClanMemberStatus;
  paintCountTotal: number;
  joinedAt: string;
  leftAt?: string | null;
}

export interface ClanDetailResponse {
  id: string;
  name: string;
  description: string | null;
  ownerId: string;
  joinPolicy: ClanJoinPolicy; // ClanJoinPolicyCode
  isPublic: boolean;
  memberCount: number;
  paintCountTotal: number;
  metadata: unknown;
  createdAt: string;
  updatedAt: string;
}

export interface ClanDetailView {
  id: string;
  name: string;
  description: string | null;
  ownerNickname: string;
  ownerNicknameHandle?: string | null;
  joinPolicy: ClanJoinPolicy;
  isPublic: boolean;
  memberCount: number;
  paintCountTotal: number;
  metadata: unknown;
  createdAt: string;
  updatedAt: string;
  members: ClanMemberView[];
}
export interface ClanJoinRequestBody {
  message?: string | null;
}

export interface ClanJoinRequestDTO {
  id: string;
  clanId: string;
  userId: string;
  status: ClanJoinRequestStatus;
  message?: string | null;
  reviewerId?: string | null;
  decidedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ClanMemberDTO {
  id: string;
  clanId: string;
  userId: string;
  role: ClanMemberRole;
  status: ClanMemberStatus;
  paintCountTotal: number;
  joinedAt: string;
  leftAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InvitePreferenceDTO {
  acceptInvites: boolean;
  updatedAt: string;
}

export interface InviteLinkResolveResponse {
  token: string;
  clan: Pick<ClanSummary, 'id' | 'handle' | 'name' | 'memberCount'>;
  joinableHint?: 'OK' | 'REQUIRES_LOGIN' | 'ALREADY_IN_CLAN' | 'CLOSED';
}

export interface InviteLinkCreateResponse {
  token: string;
  url: string;
}

export interface ClanInviteTicketResponse {
  code: string;
  url: string;
  expiresAt: string;
}

// ========== BFF Leaderboard (subject-based) ==========
export type LeaderboardSubject = 'USER' | 'CLAN';
export type LeaderboardPeriodKey = 'DAY' | 'WEEK' | 'MONTH';

export interface LeaderboardEntryView {
  id: string; // UUID
  name: string;
  paints: number;
  rank: number;
}

export interface LeaderboardView {
  subject: LeaderboardSubject;
  key: LeaderboardPeriodKey;
  entries: LeaderboardEntryView[];
}

export type LeaderboardViewResponse = CommonResponse<LeaderboardView>;

// ========== Legacy clan member leaderboard ==========
export type LeaderboardPeriod = 'today' | 'week' | 'month' | 'all';

// ==== BFF-aligned Create/Update Clan DTOs (strict to backend)
export interface CreateClanRequest {
  name: string;
  description: string;
  policy: ClanJoinPolicy;
}

export interface UpdateClanProfileRequest {
  name?: string;
  description?: string | null;
  metadataPatch?: unknown;
  joinPolicy?: ClanJoinPolicy;
}

// ========== Media Uploads ==========
export type SubjectKind = 'USER' | 'CLAN';
export type AssetPurpose = 'PROFILE' | 'BANNER';

export interface PresignPutRequest {
  kind: SubjectKind;
  subjectId: string; // UUID
  purpose: AssetPurpose;
  contentType: 'image/jpeg' | 'image/png' | 'image/webp';
  originalFileName?: string;
  checksumSHA256Base64?: string;
}

export interface PresignPutResponse {
  assetId: string;
  method: string; // PUT
  url: string;
  headers: Record<string, string[]>;
  key: string;
  expiresAt: string; // ISO
}

export interface UploadCommitRequest {
  assetId: string;
  objectKey: string;
  purpose: AssetPurpose;
  originalFilename: string;
  etag: string;
}

export interface UploadCommitResponse {
  assetId: string;
  assetKey: string;
  size: number;
  width: number;
  height: number;
  mime: string;
  sha256: string;
  pixelSha256: string;
  cdnUrl: string;
}
