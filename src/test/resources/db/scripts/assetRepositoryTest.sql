insert into asset (id, municipality_id, source, source_id, version, authority, asset_id, origin, `type`, title, description, status, status_reason, issued, valid_to)
values
    ('e84b72ee-1a34-44b5-b8f6-2e0e42e99010', '2281', 'PARTY_ASSETS', '1c8f38a6-b492-4037-b7dc-de5bc6c629f0', 3, 'MILJONAMNDEN', 'PRH-0000000001', 'CASEDATA',
     'PERMIT', 'Serveringstillstånd', 'description', 'ACTIVE', null, '2026-01-01', '2027-01-01'),
    ('cba6f0e5-e826-4690-8776-37c69d981a2a', '2281', 'PARTY_ASSETS', '5f0b5b0a-7b1c-4cf2-9a8e-2d6f6f0f1a01', 1, null, 'PRH-0000000002', 'CASEDATA',
     'PERMIT', 'Parkeringstillstånd', null, 'ACTIVE', null, '2026-02-01', null),
    ('0d64beb2-3aee-4e0f-9b1f-6a1e1c2a3b4c', '2260', 'PARTY_ASSETS', 'a3b7c1d2-0e4f-4a5b-8c6d-7e8f9a0b1c2d', 1, null, 'PRH-0000000003', 'CASEDATA',
     'PERMIT', 'Annan kommun', null, 'ACTIVE', null, '2026-03-01', null);

insert into asset_party (asset_id, party_id, role)
values
    ('e84b72ee-1a34-44b5-b8f6-2e0e42e99010', '81471222-5798-11e9-ae24-57fa13b361e1', 'OWNER'),
    ('e84b72ee-1a34-44b5-b8f6-2e0e42e99010', '81471222-5798-11e9-ae24-57fa13b361e1', 'APPLICANT'),
    ('cba6f0e5-e826-4690-8776-37c69d981a2a', 'c5d21b57-c785-4d3c-8361-940cae999ff7', 'OWNER'),
    ('0d64beb2-3aee-4e0f-9b1f-6a1e1c2a3b4c', '81471222-5798-11e9-ae24-57fa13b361e1', 'OWNER');
