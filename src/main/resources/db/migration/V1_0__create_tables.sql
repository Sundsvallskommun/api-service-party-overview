    create table asset (
        issued date,
        valid_to date,
        version bigint not null,
        status varchar(32),
        source varchar(64) not null,
        asset_id varchar(255),
        authority varchar(255),
        id varchar(255) not null,
        municipality_id varchar(255) not null,
        origin varchar(255),
        source_id varchar(255) not null,
        status_reason varchar(255),
        title varchar(255),
        `type` varchar(255),
        description longtext,
        primary key (id)
    ) engine=InnoDB;

    create table asset_party (
        role varchar(64),
        asset_id varchar(255) not null,
        party_id varchar(255) not null
    ) engine=InnoDB;

    -- No check constraints on operation and object_type, unlike the schema Hibernate generates: a new object type
    -- should not need a migration of its own.
    create table event (
        occurred_at datetime(6) not null,
        received_at datetime(6) not null,
        version bigint not null,
        operation varchar(16) not null,
        object_type varchar(32) not null,
        source varchar(64) not null,
        authority varchar(255),
        event_id varchar(255) not null,
        id varchar(255) not null,
        municipality_id varchar(255) not null,
        source_id varchar(255) not null,
        payload longtext,
        primary key (id)
    ) engine=InnoDB;

    alter table if exists asset
       add constraint uq_asset_municipality_id_source_source_id unique (municipality_id, source, source_id);

    create index idx_asset_party_party_id
       on asset_party (party_id);

    create index idx_event_municipality_id_source_object_type_source_id
       on event (municipality_id, source, object_type, source_id);

    alter table if exists event
       add constraint uq_event_event_id unique (event_id);

    alter table if exists asset_party
       add constraint fk_asset_party_asset_id
       foreign key (asset_id)
       references asset (id);
