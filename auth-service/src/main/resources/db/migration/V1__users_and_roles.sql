create table users (
    id            bigserial primary key,
    username      varchar(50)  not null unique,
    password_hash varchar(100) not null,
    enabled       boolean      not null default true,
    created_at    timestamptz  not null default now()
);

-- a user can hold more than one role, so it is its own table, not a column
create table user_roles (
    user_id bigint      not null references users (id) on delete cascade,
    role    varchar(40) not null,
    primary key (user_id, role)
);

create index idx_user_roles_user on user_roles (user_id);
