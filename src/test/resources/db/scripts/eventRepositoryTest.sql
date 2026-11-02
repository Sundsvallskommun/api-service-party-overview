insert into event (id, event_id, municipality_id, source, object_type, source_id, operation, version, occurred_at, received_at, authority, payload)
values
    ('5c3f1f0e-1a2b-4c3d-8e4f-5a6b7c8d9e01', 'b82bd8ac-1507-4d9a-958d-369261eecc15', '2281', 'PARTY_ASSETS', 'ASSET', '1c8f38a6-b492-4037-b7dc-de5bc6c629f0',
     'UPSERT', 1, '2026-10-01 10:00:00', '2026-10-01 10:00:01', 'MILJONAMNDEN', '{"version":1}'),
    ('5c3f1f0e-1a2b-4c3d-8e4f-5a6b7c8d9e02', 'c0a4e9f2-6d3b-4f1a-9e8c-7b5a3d1f2e04', '2281', 'PARTY_ASSETS', 'ASSET', '1c8f38a6-b492-4037-b7dc-de5bc6c629f0',
     'UPSERT', 2, '2026-10-02 10:00:00', '2026-10-02 10:00:01', 'MILJONAMNDEN', '{"version":2}'),
    ('5c3f1f0e-1a2b-4c3d-8e4f-5a6b7c8d9e03', 'd1b5fa03-7e4c-4a2b-8f9d-8c6b4e2a3f05', '2281', 'PARTY_ASSETS', 'ASSET', '5f0b5b0a-7b1c-4cf2-9a8e-2d6f6f0f1a01',
     'DELETE', 4, '2026-10-03 10:00:00', '2026-10-03 10:00:01', null, null);
