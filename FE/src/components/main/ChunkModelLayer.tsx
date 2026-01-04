import ChunkModel from '@/components/main/ChunkModel';
import { useChunkModelStore } from '@/stores/useChunkModelStore';
import type { GetTileBaseHeight } from '@/features/voxels/types';

export default function ChunkModelLayer({ getTileBaseHeight }: { getTileBaseHeight: GetTileBaseHeight }) {
  const items = useChunkModelStore((s) => s.items);
  return (
    <>
      {items.map((spec) => (
        <ChunkModel key={spec.id} spec={spec} getTileBaseHeight={getTileBaseHeight} />
      ))}
    </>
  );
}

