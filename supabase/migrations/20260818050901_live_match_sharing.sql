create schema if not exists racket_score_private;
revoke all on schema racket_score_private from public, anon, authenticated;

create table public.live_matches (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    public_code text not null unique check (public_code ~ '^[A-HJ-NP-Z2-9]{8}$'),
    sequence bigint not null default 0 check (sequence >= 0),
    snapshot jsonb not null,
    status text not null default 'live' check (status in ('live', 'complete', 'closed')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '8 hours'),
    check (expires_at > created_at)
);

create index live_matches_owner_updated_idx on public.live_matches(owner_id, updated_at desc);
create index live_matches_live_expiry_idx on public.live_matches(expires_at) where status = 'live';

alter table public.live_matches enable row level security;
revoke all on public.live_matches from public, anon;
grant select, insert, update, delete on public.live_matches to authenticated;

create policy "Owners can view live matches"
on public.live_matches for select to authenticated
using ((select auth.uid()) = owner_id);

create policy "Owners can create live matches"
on public.live_matches for insert to authenticated
with check ((select auth.uid()) = owner_id);

create policy "Owners can update live matches"
on public.live_matches for update to authenticated
using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

create policy "Owners can delete live matches"
on public.live_matches for delete to authenticated
using ((select auth.uid()) = owner_id);

create or replace function racket_score_private.set_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger live_matches_set_updated_at
before update on public.live_matches
for each row execute function racket_score_private.set_updated_at();

create or replace function public.get_live_match(code text)
returns table (
    public_code text,
    sequence bigint,
    snapshot jsonb,
    status text,
    updated_at timestamptz,
    expires_at timestamptz
)
language sql
stable
security definer
set search_path = ''
as $$
    select
        match.public_code,
        match.sequence,
        match.snapshot,
        match.status,
        match.updated_at,
        match.expires_at
    from public.live_matches as match
    where match.public_code = upper(trim(code))
      and match.status in ('live', 'complete')
      and match.expires_at > now()
    limit 1;
$$;

revoke all on function public.get_live_match(text) from public;
grant execute on function public.get_live_match(text) to anon, authenticated;
