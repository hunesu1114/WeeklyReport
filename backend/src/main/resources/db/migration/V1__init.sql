create table report (
    id              bigserial primary key,
    report_date     date         not null,
    author_name     varchar(50)  not null,
    title_override  varchar(200),
    this_week_start date         not null,
    this_week_end   date         not null,
    next_week_start date         not null,
    next_week_end   date         not null,
    base_hours      numeric(6,2) not null default 40,
    note            text,
    template_key    varchar(50)  not null default 'DEFAULT_V1',
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null
);

create index idx_report_date on report (report_date desc);
create index idx_report_author on report (author_name);

create table report_item (
    id         bigserial primary key,
    report_id  bigint       not null references report (id) on delete cascade,
    section    varchar(20)  not null,
    sort_order integer      not null,
    task_name  varchar(200),
    detail     text,
    status     varchar(30),
    hours      numeric(6,2)
);

create index idx_report_item_report on report_item (report_id, section, sort_order);

create table report_template (
    id               bigserial primary key,
    template_key     varchar(50)  not null unique,
    name             varchar(100) not null,
    description      varchar(500),
    renderer_bean    varchar(100) not null,
    filename_pattern varchar(200) not null,
    active           boolean      not null default true,
    sort_order       integer      not null default 0
);
