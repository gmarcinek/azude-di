# Azure Document Intelligence - Parametry konfiguracji

## Aktualne ustawienia w aplikacji

### ChunkedDocumentAnalysisService - pełna konfiguracja:

```java
var poller = client.beginAnalyzeDocument(
    "prebuilt-layout",              // Model ID
    null,                           // pages: null = wszystkie strony (można "1-5,8,10-20")
    "pl-PL",                        // locale: język OCR dla polskich znaków
    null,                           // stringIndexType: null = default
    features,                       // Lista features (patrz niżej)
    null,                           // queryFields: custom fields
    ContentFormat.MARKDOWN,         // outputContentFormat: MARKDOWN lub TEXT
    request
);
```

### Features (włączone):

```java
List<DocumentAnalysisFeature> features = List.of(
    DocumentAnalysisFeature.STYLE_FONT,     // ⭐ Bold, italic, font sizes
    DocumentAnalysisFeature.KEY_VALUE_PAIRS // ⭐ Pary klucz-wartość
);
```

### Dostępne features:

- **STYLE_FONT** - wykrywa **bold**, _italic_, rozmiary czcionek
- **KEY_VALUE_PAIRS** - pary klucz-wartość (np. "Imię: Jan")
- **BARCODES** - kody kreskowe QR/1D/2D
- **FORMULAS** - formuły matematyczne LaTeX
- **LANGUAGES** - wykrywanie języków w dokumencie

## Parametry szczegółowe

### 1. **pages** (String)

```java
"1-5"          // Strony 1-5
"1,3,5"        // Strony 1, 3, 5
"1-5,8,10-20"  // Kombinacja zakresów
null           // Wszystkie strony (default)
```

### 2. **locale** (String)

```java
"pl-PL"   // Polski - lepsze rozpoznawanie ą,ę,ó,ł,ż,ź,ć,ń,ś
"en-US"   // Angielski
"de-DE"   // Niemiecki
null      // Auto-detect (default)
```

### 3. **outputContentFormat** (ContentFormat)

```java
ContentFormat.MARKDOWN  // ⭐ Markdown z formatowaniem
ContentFormat.TEXT      // Zwykły text (default)
```

**Markdown output zawiera:**

- Headers (`# Title`, `## Heading`)
- **Bold text** z wykrytych STYLE_FONT
- _Italic text_ z wykrytych STYLE_FONT
- Tabele jako markdown tables
- Listy punktowane/numerowane

### 4. **stringIndexType** (StringIndexType)

```java
StringIndexType.TEXT_ELEMENTS      // Dla offsetów w text elements
StringIndexType.UNICODE_CODE_POINT // Dla offsetów w Unicode
null                               // Default (TEXT_ELEMENTS)
```

### 5. **readingOrder** (nie w tym API, ale w innych modelach)

```
"natural" - lepsze składanie kolumn, złożonych layoutów
"basic"   - prosta kolejność lewa-prawa, góra-dół
```

## Tabele

### Wykrywanie tabel:

```java
// Tabele są automatycznie wykrywane w prebuilt-layout
AnalyzeResult result = poller.getFinalResult();

// Dostęp do tabel
List<DocumentTable> tables = result.getTables();
```

### Struktura tabeli:

```java
DocumentTable table = tables.get(0);
int rows = table.getRowCount();        // Liczba wierszy
int cols = table.getColumnCount();     // Liczba kolumn
List<DocumentTableCell> cells = table.getCells();

// Każda komórka ma:
cell.getRowIndex()      // Numer wiersza (0-based)
cell.getColumnIndex()   // Numer kolumny (0-based)
cell.getContent()       // Zawartość tekstowa
cell.getRowSpan()       // Scalenie wierszy (rowspan)
cell.getColumnSpan()    // Scalenie kolumn (colspan)
```

### Markdown tables (automatic):

Przy `ContentFormat.MARKDOWN` tabele są automatycznie konwertowane:

```markdown
| Header 1 | Header 2 | Header 3 |
| -------- | -------- | -------- |
| Cell 1   | Cell 2   | Cell 3   |
| Cell 4   | Cell 5   | Cell 6   |
```

## Przykład wyniku z formatowaniem

**Input PDF:**

```
Artykuł 1. Definicje
NW - nieszczęśliwy wypadek
```

**Output Markdown (STYLE_FONT enabled):**

```markdown
**Artykuł 1. Definicje**

_NW_ - nieszczęśliwy wypadek
```

## Obecna konfiguracja aplikacji

✅ **Włączone:**

- `outputContentFormat.MARKDOWN` - natywny markdown z Azure
- `STYLE_FONT` - bold/italic
- `KEY_VALUE_PAIRS` - pary klucz-wartość
- `locale: pl-PL` - lepsze polskie znaki
- Automatyczna ekstrakcja tabel

✅ **Obsługa tabel:**

- `result.getTables()` - wszystkie tabele
- Konwersja do markdown tables
- Dodane jako sekcje typu "table"

## Endpoint

`POST /api/v1/documents/analyze-chunked`

Zwraca:

```json
{
  "sections": [...sections + tables...],
  "content": "markdown z formatowaniem i tabelami"
}
```
