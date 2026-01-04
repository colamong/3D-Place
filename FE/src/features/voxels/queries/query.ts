import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { paintKeys } from './key';
import { getPaintsByChunk, postPaint, postErase } from '@/api/paint';

export function usePaintChunkQuery(chunkId: string) {
  return useQuery({
    queryKey: paintKeys.chunk(chunkId),
    queryFn: () => getPaintsByChunk(chunkId),
  });
}

export function usePaintMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: postPaint,
    onSuccess: () => qc.invalidateQueries({ queryKey: paintKeys.all }),
  });
}

export function useEraseMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: postErase,
    onSuccess: () => qc.invalidateQueries({ queryKey: paintKeys.all }),
  });
}
