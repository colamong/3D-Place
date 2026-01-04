import { api } from './instance';
import type {
  AuthState,
  VerifyEmailContext,
  VerifyEmailResendResult,
} from './types';

export async function getAuthState(): Promise<AuthState> {
  const { data } = await api.get('/auth/state');
  return data as AuthState;
}

export async function resendVerifyEmail(
  email: string,
): Promise<VerifyEmailResendResult> {
  // BFF-aligned: POST /api/auth/verify-email/resend
  const { data } = await api.post('/auth/verify-email/resend', { email });
  return data as VerifyEmailResendResult;
}

export async function getVerifyEmailContext(
  email: string,
): Promise<VerifyEmailContext> {
  // BFF-aligned: GET /api/auth/verify-email/context?email=...
  const { data } = await api.get('/auth/verify-email/context', {
    params: { email },
  });
  return data as VerifyEmailContext;
}

export async function logoutAllDevices(): Promise<void> {
  await api.post('/logout-all');
}
 