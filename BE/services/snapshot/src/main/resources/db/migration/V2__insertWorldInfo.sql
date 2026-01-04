WITH inserted_world AS (
INSERT INTO world (name)
VALUES ('world')
    RETURNING uuid
    )
INSERT INTO world_lod (world_id, lod, chunk_edge_cells, voxel_size_m)
SELECT uuid, 0, 256, 1 FROM inserted_world
UNION ALL SELECT uuid, 1, 256, 1 FROM inserted_world
UNION ALL SELECT uuid, 2, 512, 1 FROM inserted_world
UNION ALL SELECT uuid, 3, 1024, 1 FROM inserted_world