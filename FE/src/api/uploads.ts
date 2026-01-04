import { api } from './instance';
import type {
  CommonResponse,
  PresignPutRequest,
  PresignPutResponse,
  UploadCommitRequest,
  UploadCommitResponse,
} from './types';

export async function presignUpload(
  body: PresignPutRequest,
): Promise<CommonResponse<PresignPutResponse>> {
  const { data } = await api.post('/uploads/presign', body);
  return data as CommonResponse<PresignPutResponse>;
}

export async function commitUpload(
  body: UploadCommitRequest,
): Promise<CommonResponse<UploadCommitResponse>> {
  const { data } = await api.post('/uploads/commit', body);
  return data as CommonResponse<UploadCommitResponse>;
}

export async function sha256Base64(file: File): Promise<string> {
  const buf = await file.arrayBuffer();
  const hash = await crypto.subtle.digest('SHA-256', buf);
  const bytes = new Uint8Array(hash);
  // base64
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}

function headersFromMap(map: Record<string, string[]>): Headers {
  const h = new Headers();
  for (const [k, arr] of Object.entries(map || {})) {
    if (!arr) continue;
    for (const v of arr) h.append(k, v);
  }
  return h;
}

export async function uploadUserAvatarAndGetCdnUrl(
  file: File,
  userId: string,
): Promise<string> {
  if (
    !file.type ||
    !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
  ) {
    throw new Error('Unsupported content type');
  }

  const checksum = await sha256Base64(file);
  const presign = await presignUpload({
    kind: 'USER',
    subjectId: userId,
    purpose: 'PROFILE',
    contentType: file.type as PresignPutRequest['contentType'],
    originalFileName: file.name,
    checksumSHA256Base64: checksum,
  });

  const p = presign.data;
  console.log('[avatar-upload] presign ok', {
    assetId: p.assetId,
    url: p.url,
    headers: p.headers,
  });
  const headers = headersFromMap(p.headers);
  // PUT to storage
  const putRes = await fetch(p.url, { method: 'PUT', body: file, headers });
  if (!putRes.ok) {
    throw new Error(`Upload failed: ${putRes.status}`);
  }
  const etag = putRes.headers.get('ETag') ?? '';

  const commit = await commitUpload({
    assetId: p.assetId,
    objectKey: p.key,
    purpose: 'PROFILE',
    originalFilename: file.name,
    etag,
  });

  return commit.data.cdnUrl;
}
