insert into public.luminabook_catalog_items
  (slug, title, author, description, item_type, file_format, storage_path, cover_path, language_code, subjects, is_published)
values
  ('fuente-ovejuna','Fuente Ovejuna','Lope de Vega','Obra teatral del Siglo de Oro sobre una comunidad que se enfrenta al abuso de poder.','book','epub','https://www.gutenberg.org/ebooks/60198.epub3.images',null,'es',array['teatro','clásicos','siglo de oro'],true),
  ('martin-fierro','El Gaucho Martín Fierro','José Hernández','Poema narrativo fundamental de la literatura argentina, centrado en la vida del gaucho Martín Fierro.','book','epub','https://www.gutenberg.org/ebooks/14765.epub3.images',null,'es',array['poesía narrativa','clásicos','literatura argentina'],true)
on conflict (slug) do update set
  title=excluded.title, author=excluded.author, description=excluded.description,
  item_type=excluded.item_type, file_format=excluded.file_format, storage_path=excluded.storage_path,
  language_code=excluded.language_code, subjects=excluded.subjects, is_published=excluded.is_published,
  updated_at=now();