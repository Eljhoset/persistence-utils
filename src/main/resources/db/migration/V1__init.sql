create table accounts (
    id int generated always as identity,
    state text,
    balance numeric not null ,
    currency text not null
);

create table deposits (
    id int generated always as identity,
    account_id int not null,
    state text not null,
    amount numeric not null,
    currency text,
    registered_at timestamp not null,
    registered_by text not null,
    approved_at timestamp,
    approved_by text,
    rejected_at timestamp,
    rejected_by text,
    rejection_reason_id int,
    rejection_reason_other text,
    cancelled_at timestamp,
    cancelled_by text,
    cancellation_reason_id int,
    cancellation_reason_other text,
    confirmed_at timestamp,
    confirmed_by text,
    is_confirmed boolean default false
);
