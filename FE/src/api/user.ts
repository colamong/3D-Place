import { api } from './instance';
import type {
  CommonResponse,
  UpdateProfileRequestBody,
  UserProfileResponse,
  PublicProfileResponse,
  
} from './types';

// BFF-aligned: GET /api/users/me
export async function getMyProfile(): Promise<CommonResponse<UserProfileResponse>> {
  const { data } = await api.get('/users/me');
  return data as CommonResponse<UserProfileResponse>;
}

// BFF-aligned: GET /api/users/profile/{handle}
export async function getPublicProfileByHandle(nicknameHandle: string): Promise<CommonResponse<PublicProfileResponse>> {
  const { data } = await api.get(`/users/profile/${encodeURIComponent(nicknameHandle)}`);
  return data as CommonResponse<PublicProfileResponse>;
}

// BFF-aligned: POST /api/users/profile
export async function updateProfile(body: UpdateProfileRequestBody): Promise<CommonResponse<UserProfileResponse>> {
  const { data } = await api.post('/users/profile', body);
  return data as CommonResponse<UserProfileResponse>;
}

// 계정 연동 링크 시작
// 백엔드: UserIdentityViewController
// @PostMapping("/link/start")
export async function startLink(provider: string): Promise<CommonResponse<Record<string, unknown>>> {
  const { data } = await api.post('/users/link/start', undefined, { params: { provider } });
  return data as CommonResponse<Record<string, unknown>>;
}

// OAuth 링크 콜백 처리
// 백엔드: UserIdentityViewController
// @GetMapping("/oauth/link/callback")
export async function oauthLinkCallback(code: string, state: string): Promise<CommonResponse<Record<string, unknown>>> {
  const { data } = await api.get('/users/oauth/link/callback', { params: { code, state } });
  return data as CommonResponse<Record<string, unknown>>;
}

// 계정 삭제: DELETE /api/users/me
export async function deleteAccount(): Promise<void> {
  await api.delete('/users/me');
}
