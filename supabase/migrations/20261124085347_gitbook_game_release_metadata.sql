alter table public.luminabook_catalog_items
  add column if not exists game_version text not null default '1.0.0',
  add column if not exists update_manifest_path text,
  add column if not exists package_entry text not null default 'index.html',
  add column if not exists sha256 text,
  add column if not exists release_notes text not null default '',
  add column if not exists minimum_app_version text not null default '1.0.0';

create index if not exists luminabook_catalog_game_updates_idx
  on public.luminabook_catalog_items (id, updated_at desc)
  where item_type = 'game' and is_published = true;

comment on column public.luminabook_catalog_items.game_version is 'Semantic version installed by the GITBOOK game manager';
comment on column public.luminabook_catalog_items.update_manifest_path is 'Optional public JSON manifest path for game updates';
comment on column public.luminabook_catalog_items.package_entry is 'Entry HTML file inside a game package, relative to package root';
comment on column public.luminabook_catalog_items.sha256 is 'Optional SHA-256 of the downloaded game package';
comment on column public.luminabook_catalog_items.release_notes is 'Human-readable release notes shown before updating';
comment on column public.luminabook_catalog_items.minimum_app_version is 'Minimum compatible GITBOOK app version';
