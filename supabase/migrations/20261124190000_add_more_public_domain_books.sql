-- Add more verified Spanish public-domain EPUB records to LuminaBook.
-- Project Gutenberg hosts the EPUB files; the app downloads them through the existing allowlisted host.
insert into public.luminabook_catalog_items
  (slug, title, author, description, item_type, file_format, storage_path, cover_path, language_code, subjects, is_published)
values
  (
    'platero-y-yo',
    'Platero y yo',
    'Juan Ramón Jiménez',
    'Prosa poética en breves estampas sobre Platero, un pequeño burro y la vida en Moguer.',
    'book',
    'epub',
    'https://www.gutenberg.org/ebooks/39209.epub3.images',
    null,
    'es',
    array['poesía','clásicos','literatura española'],
    true
  ),
  (
    'niebla-unamuno',
    'Niebla',
    'Miguel de Unamuno',
    'Novela filosófica y experimental que juega con los límites entre el autor, el personaje y la realidad.',
    'book',
    'epub',
    'https://www.gutenberg.org/ebooks/49836.epub3.images',
    null,
    'es',
    array['novela','filosofía','clásicos'],
    true
  ),
  (
    'azul-ruben-dario',
    'Azul...',
    'Rubén Darío',
    'Colección modernista de relatos y poemas que marcó un hito en la literatura hispanoamericana.',
    'book',
    'epub',
    'https://www.gutenberg.org/ebooks/52894.epub3.images',
    null,
    'es',
    array['poesía','cuentos','modernismo'],
    true
  ),
  (
    'lazarillo-de-tormes',
    'La vida de Lazarillo de Tormes',
    'Anónimo',
    'Novela picaresca del siglo XVI narrada por Lázaro, que observa con ironía las desigualdades y contradicciones de su época.',
    'book',
    'epub',
    'https://www.gutenberg.org/ebooks/320.epub3.images',
    null,
    'es',
    array['novela picaresca','clásicos','literatura española'],
    true
  )
on conflict (slug) do update set
  title = excluded.title,
  author = excluded.author,
  description = excluded.description,
  item_type = excluded.item_type,
  file_format = excluded.file_format,
  storage_path = excluded.storage_path,
  language_code = excluded.language_code,
  subjects = excluded.subjects,
  is_published = excluded.is_published,
  updated_at = now();
