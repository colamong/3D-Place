import { useEffect, useState } from 'react';

/**
 * 파일 선택 시 Object URL을 만들어 미리보기 URL을 제공합니다.
 * 파일이 없으면 initialUrl을 그대로 사용합니다.
 */
export function useAvatarPreview(
  file: File | null,
  initialUrl: string | null = null,
) {
  const [url, setUrl] = useState<string | null>(initialUrl);

  useEffect(() => {
    if (!file) {
      setUrl(initialUrl ?? null);
      return;
    }

    const objectUrl = URL.createObjectURL(file);
    setUrl(objectUrl);

    return () => {
      URL.revokeObjectURL(objectUrl);
    };
  }, [file, initialUrl]);

  return url;
}

