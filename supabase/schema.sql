-- Vendora — Supabase schema (v4)
-- Run this once in your Supabase project's SQL Editor
-- (Dashboard → SQL Editor → New query → paste the WHOLE file → Run).
--
-- This script is self-cleaning: it drops any earlier version of Vendora's
-- tables/functions/policies before recreating them, so it's always safe to
-- re-run from scratch if something looks wrong — you never need a separate
-- cleanup step first.
--
-- There is deliberately ZERO Supabase Auth dashboard configuration needed
-- for this — no Email/Phone provider to enable, no confirmation toggles.
-- Registration, login, and all data access happen through the plain
-- Postgrest REST API using the functions below, called with the app's
-- anon key.
--
-- Security model: every real table (shops, shop_accounts, products, sales)
-- has Row Level Security turned on with ZERO policies attached, which
-- means the Postgrest REST endpoints for those tables always return
-- nothing / reject writes, for anyone, always. The only way in or out is
-- through the SECURITY DEFINER functions below, which validate a session
-- token before touching any data.

-- ─────────────────────────────────────────────────────────────
-- Clean slate — safe to run even if none of this exists yet
-- ─────────────────────────────────────────────────────────────
drop function if exists public.replace_sales(text, jsonb);
drop function if exists public.get_sales(text);
drop function if exists public.replace_products(text, jsonb);
drop function if exists public.get_products(text);
drop function if exists public.get_shop_details(text);
drop function if exists public.logout_session(text);
drop function if exists public.login_shop_account(text, text);
drop function if exists public.join_shop_with_code(text, text, text);
drop function if exists public.register_shop(text, text, text, text, text, text, text, text);
drop function if exists public.session_shop_id(text);
drop function if exists public.shop_id_for_join_code(text);

drop table if exists public.sales cascade;
drop table if exists public.products cascade;
drop table if exists public.shop_sessions cascade;
drop table if exists public.shop_accounts cascade;
drop table if exists public.shop_members cascade;
drop table if exists public.shops cascade;

drop policy if exists "Users can upload their own verification files" on storage.objects;
drop policy if exists "Users can view their own verification files" on storage.objects;
drop policy if exists "Anyone can upload a verification file" on storage.objects;
drop policy if exists "Anyone can view a verification file" on storage.objects;
drop policy if exists "Anyone can update a verification file" on storage.objects;

-- ─────────────────────────────────────────────────────────────
-- pgcrypto — needed for crypt()/gen_random_bytes(). Supabase projects
-- commonly install this into a schema called "extensions" rather than
-- "public", so every function below searches BOTH schemas rather than
-- assuming one or the other.
-- ─────────────────────────────────────────────────────────────
create extension if not exists pgcrypto;

-- ─────────────────────────────────────────────────────────────
-- Tables
-- ─────────────────────────────────────────────────────────────

create table public.shops (
    id uuid primary key default gen_random_uuid(),
    owner_account_id uuid,                    -- filled in right after the owner account row exists
    shop_name text not null,
    owner_name text not null,
    address text not null,
    gst_number text,                          -- optional, never required
    join_code text not null unique,           -- share this with employees to let them join
    verification_status text not null default 'pending', -- pending | verified | rejected
    verification_method text not null,        -- shopfront_photo | business_document | selfie_photo
    verification_file_path text not null,     -- path inside the shop-verification storage bucket
    created_at timestamptz not null default now()
);

-- Replaces auth.users entirely — one row per phone number.
create table public.shop_accounts (
    id uuid primary key default gen_random_uuid(),
    phone text not null unique,               -- "9198765432" style, digits only
    password_hash text not null,              -- bcrypt via pgcrypto, never plaintext
    shop_id uuid not null references public.shops(id) on delete cascade,
    role text not null default 'owner',       -- owner | employee
    created_at timestamptz not null default now()
);

alter table public.shops
    add constraint shops_owner_account_fk
    foreign key (owner_account_id) references public.shop_accounts(id);

-- Replaces Supabase Auth's JWT session — a long random token the app holds
-- on to (see SessionStore.kt) and sends with every request from here on.
create table public.shop_sessions (
    token text primary key,
    account_id uuid not null references public.shop_accounts(id) on delete cascade,
    shop_id uuid not null references public.shops(id) on delete cascade,
    role text not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '365 days')
);

create table public.products (
    id bigint generated always as identity primary key,
    shop_id uuid not null references public.shops(id) on delete cascade,
    local_id integer not null,
    name text not null,
    barcode text not null,
    selling_price double precision not null default 0,
    current_stock integer not null default 0,
    unit text not null default 'pcs',
    updated_at timestamptz not null default now(),
    unique (shop_id, local_id)
);

create table public.sales (
    id bigint generated always as identity primary key,
    shop_id uuid not null references public.shops(id) on delete cascade,
    local_id integer not null,
    "timestamp" bigint not null,
    total_amount double precision not null default 0,
    items_summary text not null default '',
    cash_amount double precision not null default 0,
    upi_amount double precision not null default 0,
    is_credit boolean not null default false,
    customer_name text not null default '',
    customer_phone text not null default '',
    updated_at timestamptz not null default now(),
    unique (shop_id, local_id)
);

-- Lock every table down completely at the REST layer — enabled, no
-- policies, so direct table access always returns/changes nothing.
-- All real access happens through the SECURITY DEFINER functions below.
alter table public.shops enable row level security;
alter table public.shop_accounts enable row level security;
alter table public.shop_sessions enable row level security;
alter table public.products enable row level security;
alter table public.sales enable row level security;

create index products_shop_id_idx on public.products (shop_id);
create index sales_shop_id_idx on public.sales (shop_id);
create index shop_sessions_token_idx on public.shop_sessions (token);
create index shop_accounts_phone_idx on public.shop_accounts (phone);
create index shops_join_code_idx on public.shops (join_code);

-- ─────────────────────────────────────────────────────────────
-- Internal helper — resolves a session token to a shop_id, or null if the
-- token doesn't exist / has expired. Not directly callable from the app.
-- ─────────────────────────────────────────────────────────────
create or replace function public.session_shop_id(p_token text)
returns uuid
language sql
security definer
set search_path = public, extensions
stable
as $$
    select shop_id from public.shop_sessions where token = p_token and expires_at > now();
$$;

-- ─────────────────────────────────────────────────────────────
-- Registration — owner. Called once, after the shop details form AND the
-- verification photo upload are both already done on the client, so the
-- account only comes into existence with everything attached.
-- ─────────────────────────────────────────────────────────────
create or replace function public.register_shop(
    p_phone text,
    p_password text,
    p_shop_name text,
    p_owner_name text,
    p_address text,
    p_gst_number text,
    p_verification_method text,
    p_verification_file_path text
) returns table(session_token text, shop_id uuid)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_shop_id uuid;
    v_account_id uuid;
    v_token text;
    v_join_code text;
begin
    if p_phone is null or length(p_phone) <> 10 then
        raise exception 'Enter a valid 10-digit mobile number';
    end if;
    if length(p_password) < 6 then
        raise exception 'Password must be at least 6 characters';
    end if;
    if exists (select 1 from public.shop_accounts where phone = p_phone) then
        raise exception 'An account with this phone number already exists';
    end if;

    v_join_code := upper(substr(md5(random()::text || clock_timestamp()::text), 1, 6));
    while exists (select 1 from public.shops where join_code = v_join_code) loop
        v_join_code := upper(substr(md5(random()::text || clock_timestamp()::text), 1, 6));
    end loop;

    insert into public.shops (shop_name, owner_name, address, gst_number, join_code, verification_method, verification_file_path)
    values (trim(p_shop_name), trim(p_owner_name), trim(p_address), nullif(trim(p_gst_number), ''), v_join_code, p_verification_method, p_verification_file_path)
    returning id into v_shop_id;

    insert into public.shop_accounts (phone, password_hash, shop_id, role)
    values (p_phone, crypt(p_password, gen_salt('bf')), v_shop_id, 'owner')
    returning id into v_account_id;

    update public.shops set owner_account_id = v_account_id where id = v_shop_id;

    v_token := encode(gen_random_bytes(32), 'hex');
    insert into public.shop_sessions (token, account_id, shop_id, role)
    values (v_token, v_account_id, v_shop_id, 'owner');

    return query select v_token, v_shop_id;
end;
$$;

grant execute on function public.register_shop(text, text, text, text, text, text, text, text) to anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Registration — employee, via the owner's shop code
-- ─────────────────────────────────────────────────────────────
create or replace function public.join_shop_with_code(
    p_phone text,
    p_password text,
    p_join_code text
) returns table(session_token text, shop_id uuid)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_shop_id uuid;
    v_account_id uuid;
    v_token text;
begin
    if p_phone is null or length(p_phone) <> 10 then
        raise exception 'Enter a valid 10-digit mobile number';
    end if;
    if length(p_password) < 6 then
        raise exception 'Password must be at least 6 characters';
    end if;
    if exists (select 1 from public.shop_accounts where phone = p_phone) then
        raise exception 'An account with this phone number already exists';
    end if;

    select id into v_shop_id from public.shops where join_code = upper(trim(p_join_code));
    if v_shop_id is null then
        raise exception 'That shop code doesn''t match any shop';
    end if;

    insert into public.shop_accounts (phone, password_hash, shop_id, role)
    values (p_phone, crypt(p_password, gen_salt('bf')), v_shop_id, 'employee')
    returning id into v_account_id;

    v_token := encode(gen_random_bytes(32), 'hex');
    insert into public.shop_sessions (token, account_id, shop_id, role)
    values (v_token, v_account_id, v_shop_id, 'employee');

    return query select v_token, v_shop_id;
end;
$$;

grant execute on function public.join_shop_with_code(text, text, text) to anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Login — works for both owners and employees
-- ─────────────────────────────────────────────────────────────
create or replace function public.login_shop_account(
    p_phone text,
    p_password text
) returns table(session_token text, shop_id uuid, role text)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_account public.shop_accounts%rowtype;
    v_token text;
begin
    select * into v_account from public.shop_accounts where phone = p_phone;
    if not found or v_account.password_hash <> crypt(p_password, v_account.password_hash) then
        raise exception 'Incorrect phone number or password';
    end if;

    v_token := encode(gen_random_bytes(32), 'hex');
    insert into public.shop_sessions (token, account_id, shop_id, role)
    values (v_token, v_account.id, v_account.shop_id, v_account.role);

    return query select v_token, v_account.shop_id, v_account.role;
end;
$$;

grant execute on function public.login_shop_account(text, text) to anon, authenticated;

create or replace function public.logout_session(p_token text)
returns void
language sql
security definer
set search_path = public, extensions
as $$
    delete from public.shop_sessions where token = p_token;
$$;

grant execute on function public.logout_session(text) to anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Shop details (for the Settings screen: name, join code, verification status)
-- ─────────────────────────────────────────────────────────────
create or replace function public.get_shop_details(p_token text)
returns table(
    shop_id uuid,
    shop_name text,
    address text,
    join_code text,
    verification_status text,
    role text
)
language sql
security definer
set search_path = public, extensions
stable
as $$
    select s.id, s.shop_name, s.address, s.join_code, s.verification_status, sess.role
    from public.shop_sessions sess
    join public.shops s on s.id = sess.shop_id
    where sess.token = p_token and sess.expires_at > now();
$$;

grant execute on function public.get_shop_details(text) to anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Products — read the whole shop's catalog, or replace it wholesale
-- (mirrors the app's existing "replace what's on the server" sync style)
-- ─────────────────────────────────────────────────────────────
create or replace function public.get_products(p_token text)
returns setof public.products
language sql
security definer
set search_path = public, extensions
stable
as $$
    select * from public.products where shop_id = public.session_shop_id(p_token);
$$;

grant execute on function public.get_products(text) to anon, authenticated;

create or replace function public.replace_products(p_token text, p_products jsonb)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_shop_id uuid := public.session_shop_id(p_token);
begin
    if v_shop_id is null then
        raise exception 'Session expired, please sign in again';
    end if;

    delete from public.products where shop_id = v_shop_id;

    insert into public.products (shop_id, local_id, name, barcode, selling_price, current_stock, unit)
    select
        v_shop_id,
        (p->>'localId')::integer,
        p->>'name',
        p->>'barcode',
        (p->>'sellingPrice')::double precision,
        (p->>'currentStock')::integer,
        p->>'unit'
    from jsonb_array_elements(coalesce(p_products, '[]'::jsonb)) as p;
end;
$$;

grant execute on function public.replace_products(text, jsonb) to anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Sales — same pattern as products
-- ─────────────────────────────────────────────────────────────
create or replace function public.get_sales(p_token text)
returns setof public.sales
language sql
security definer
set search_path = public, extensions
stable
as $$
    select * from public.sales where shop_id = public.session_shop_id(p_token);
$$;

grant execute on function public.get_sales(text) to anon, authenticated;

create or replace function public.replace_sales(p_token text, p_sales jsonb)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_shop_id uuid := public.session_shop_id(p_token);
begin
    if v_shop_id is null then
        raise exception 'Session expired, please sign in again';
    end if;

    delete from public.sales where shop_id = v_shop_id;

    insert into public.sales (
        shop_id, local_id, "timestamp", total_amount, items_summary,
        cash_amount, upi_amount, is_credit, customer_name, customer_phone
    )
    select
        v_shop_id,
        (s->>'localId')::integer,
        (s->>'timestamp')::bigint,
        (s->>'totalAmount')::double precision,
        s->>'itemsSummary',
        coalesce((s->>'cashAmount')::double precision, 0),
        coalesce((s->>'upiAmount')::double precision, 0),
        coalesce((s->>'isCredit')::boolean, false),
        coalesce(s->>'customerName', ''),
        coalesce(s->>'customerPhone', '')
    from jsonb_array_elements(coalesce(p_sales, '[]'::jsonb)) as s;
end;
$$;

grant execute on function public.replace_sales(text, jsonb) to anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Storage — shop verification photo/document uploads.
-- Uploaded BEFORE the account exists (registration needs the file path
-- up front), so this can't be gated by a session token yet — instead each
-- upload path starts with a random UUID that only that client knows.
-- Nobody can enumerate or guess these paths, but be aware this bucket
-- accepts anonymous uploads by design; don't put anything more sensitive
-- than these verification files in it.
-- ─────────────────────────────────────────────────────────────
insert into storage.buckets (id, name, public)
values ('shop-verification', 'shop-verification', false)
on conflict (id) do nothing;

create policy "Anyone can upload a verification file"
    on storage.objects for insert
    with check (bucket_id = 'shop-verification');

create policy "Anyone can view a verification file"
    on storage.objects for select
    using (bucket_id = 'shop-verification');

create policy "Anyone can update a verification file"
    on storage.objects for update
    using (bucket_id = 'shop-verification')
    with check (bucket_id = 'shop-verification');
