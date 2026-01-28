# Zadanie dla agenta @coder

Zaimplementuj aplikację Spring Boot do analizy PDF z użyciem Azure Document Intelligence.

## Wymagania funkcjonalne

1. Wczytaj plik PDF z dysku
2. Wyślij do Azure Document Intelligence (layout model)
3. Odbierz i przetwórz odpowiedź z analizą struktury
4. Zapisz wynik do pliku JSON

## Stack technologiczny

- **Java 21** (użyj records, Optional, Stream API)
- **Spring Boot 3.2+**
- **Spring AI** (Azure OpenAI integration)
- **Azure SDK**: `azure-ai-documentintelligence` (najnowsza wersja)
- **Jackson** do JSON
- **Maven**

## Architektura - separacja warstw

**WYMAGANE:**

- Warstwa config: `AzureConfig`, `AzureAiConfig` - konfiguracja beanów Azure
- Warstwa service:
  - `DocumentAnalysisService` - ekstrakcja tekstu (Azure DI)
  - `DocumentProcessingService` - przetwarzanie AI (Spring AI)
  - `DocumentClassificationService` - klasyfikacja przez LLM
- Warstwa model: `AnalysisResult`, `Section`, `QualityMetrics`, `DocumentChunk` (records)
- Warstwa ai: `ChunkingStrategy`, `AgentPlanner` (przyszłe)
- Warstwa runner: `PdfProcessorRunner` (CommandLineRunner)

**ZASADY:**

- Runner wywołuje tylko Service layer
- DocumentAnalysisService → ekstrakcja struktury (Azure DI)
- DocumentProcessingService → chunking + AI processing (Spring AI)
- Service używa clients wstrzykniętych przez konstruktor
- Brak ręcznego `new` dla managed beans
- Tylko constructor injection (nigdy field injection)

**Flow:**

```
PDF → DocumentAnalysisService → AnalysisResult
         ↓
    DocumentProcessingService (chunking, klasyfikacja)
         ↓
    Azure OpenAI (agent planning, analysis)
         ↓
    Vector Store / dalsze przetwarzanie
```

## Struktura projektu

```
src/main/java/com/example/pdfanalyzer/
├── PdfAnalyzerApplication.java
├── config/
│   ├── AzureConfig.java
│   └── AzureAiConfig.java
├── service/
│   ├── DocumentAnalysisService.java
│   ├── DocumentProcessingService.java
│   └── DocumentClassificationService.java
├── ai/
│   ├── ChunkingStrategy.java (interface)
│   ├── SemanticChunker.java
│   └── AgentPlanner.java (przyszłe)
├── model/
│   ├── AnalysisResult.java (+ nested records)
│   └── DocumentChunk.java
└── runner/
    └── PdfProcessorRunner.java
```

## Konfiguracja

### application.yml (główny)

```yaml
spring:
  profiles:
    active: local
  ai:
    azure:
      openai:
        endpoint: ${AZURE_OPENAI_ENDPOINT}
        api-key: ${AZURE_OPENAI_API_KEY}
        chat:
          options:
            deployment-name: ${AZURE_OPENAI_DEPLOYMENT_NAME:gpt-4}
            temperature: 0.7
            max-tokens: 2000

azure:
  document-intelligence:
    endpoint: ${AZURE_DOC_INTELLIGENCE_ENDPOINT}
    key: ${AZURE_DOC_INTELLIGENCE_KEY}

app:
  chunking:
    strategy: semantic # semantic, fixed-size, paragraph
    max-chunk-size: 1000
    overlap: 100
```

### application-local.yml (lokalne klucze - NIE commitować)

```yaml
spring:
  ai:
    azure:
      openai:
        endpoint: https://your-resource.openai.azure.com/
        api-key: your-azure-openai-key
        chat:
          options:
            deployment-name: gpt-4

azure:
  document-intelligence:
    endpoint: https://your-resource.cognitiveservices.azure.com/
    key: your-doc-intelligence-key
```

**UWAGA:** Dodaj `application-local.yml` do `.gitignore`!

## Implementacja klas

### 1. AzureConfig.java (config)

```java
@Configuration
public class AzureConfig {
    @Value("${azure.document-intelligence.endpoint}")
    private String endpoint;

    @Value("${azure.document-intelligence.key}")
    private String key;

    @Bean
    public DocumentIntelligenceClient documentIntelligenceClient() {
        return new DocumentIntelligenceClientBuilder()
            .endpoint(endpoint)
            .credential(new AzureKeyCredential(key))
            .buildClient();
    }
}
```

### 1a. AzureAiConfig.java (config)

```java
@Configuration
public class AzureAiConfig {
    // Spring AI auto-configuration obsługuje Azure OpenAI
    // Wystarczy properties w application.yml

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
```

### 2. DocumentAnalysisService.java (service)

**Metoda główna:**

```java
public AnalysisResult analyzeDocument(Path pdfPath)
```

**Logika:**

1. Wczytaj PDF: `BinaryData.fromFile(pdfPath)`
2. Wywołaj Azure: `client.beginAnalyzeDocument("prebuilt-layout", binaryData, options)`
3. Pobierz wynik: `.getFinalResult()`
4. Zmapuj Azure `AnalyzeResult` → własny `AnalysisResult`

**Mapowanie:**

- `result.getPages()` → `pageCount`
- `result.getParagraphs()` → lista `Section`
  - Wyciągnij: `role`, `content`, `pageNumber` (z `boundingRegions`), `confidence`
  - **Filtruj role**: `pageHeader`, `pageFooter`, `pageNumber` (pomiń w wyniku)
- Oblicz `QualityMetrics`:
  - `avgConfidence` - średnia z wszystkich paragrafów
  - `totalParagraphs` - liczba paragrafów
  - `totalChars` - suma długości contentu
  - `hasStructureMarkers` - regex `§\s*\d+|Art\.\s*\d+|pkt\s+\d+`

**Injection:**

```java
private final DocumentIntelligenceClient client;

public DocumentAnalysisService(DocumentIntelligenceClient client) {
    this.client = client;
}
```

**Opcjonalnie @Cacheable:**
Rozważ cache dla powtarzających się plików (klucz: nazwa pliku + rozmiar).

### 2a. DocumentProcessingService.java (service - AI layer)

**Metoda główna:**

```java
public List<DocumentChunk> processAndChunk(AnalysisResult analysisResult)
public String classifyDocument(AnalysisResult analysisResult)
```

**Logika chunking:**

1. Pobierz sections z `AnalysisResult`
2. Użyj `ChunkingStrategy` do podziału na chunki
3. Zwróć `List<DocumentChunk>` z metadanymi

**Logika klasyfikacji:**

1. Przygotuj prompt z fragmentem dokumentu
2. Wywołaj Azure OpenAI przez Spring AI
3. Zwróć kategorię/typ dokumentu

**Injection:**

```java
private final ChatClient chatClient;
private final ChunkingStrategy chunkingStrategy;

public DocumentProcessingService(ChatClient chatClient, ChunkingStrategy chunkingStrategy) {
    this.chatClient = chatClient;
    this.chunkingStrategy = chunkingStrategy;
}
```

**Przykład klasyfikacji:**

```java
public String classifyDocument(AnalysisResult result) {
    String content = result.sections().stream()
        .limit(10)  // pierwsze 10 sekcji
        .map(Section::content)
        .collect(Collectors.joining("\n"));

    String prompt = """
        Classify the following document into one of these categories:
        - CONTRACT
        - TERMS_AND_CONDITIONS
        - INVOICE
        - LEGAL_DOCUMENT
        - OTHER

        Document excerpt:
        %s

        Return only the category name.
        """.formatted(content);

    return chatClient.prompt()
        .user(prompt)
        .call()
        .content();
}
```

### 3. AnalysisResult.java (model)

```java
public record AnalysisResult(
    String fileName,
    int pageCount,
    List<Section> sections,
    QualityMetrics quality
) {}

public record Section(
    String role,
    String content,
    int pageNumber,
    Double confidence
) {}

public record QualityMetrics(
    double avgConfidence,
    int totalParagraphs,
    int totalChars,
    boolean hasStructureMarkers
) {}

public record DocumentChunk(
    String id,
    String content,
    int chunkIndex,
    int pageNumber,
    Map<String, Object> metadata  // role, confidence, category
) {}
```

### 4. PdfProcessorRunner.java (runner)

```java
@Component
public class PdfProcessorRunner implements CommandLineRunner {

    private final DocumentAnalysisService service;
    private final ObjectMapper objectMapper;

    // Constructor injection
    public PdfProcessorRunner(DocumentAnalysisService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java -jar app.jar <pdf-path>");
            return;
        }

        Path pdfPath = Path.of(args[0]);
        AnalysisResult result = service.analyzeDocument(pdfPath);

        Path outputPath = pdfPath.resolveSibling(
            pdfPath.getFileName().toString().replace(".pdf", "_analysis.json")
        );

        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(outputPath.toFile(), result);

        System.out.println("Saved: " + outputPath);
    }
}
```

## Zależności (pom.xml)

```xml
<properties>
    <spring-ai.version>1.0.0-M4</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>

    <!-- Spring AI -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-azure-openai-spring-boot-starter</artifactId>
    </dependency>

    <!-- Azure Document Intelligence -->
    <dependency>
        <groupId>com.azure</groupId>
        <artifactId>azure-ai-documentintelligence</artifactId>
        <version>1.0.0-beta.4</version>
    </dependency>

    <!-- Jackson -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
```

## Wymagania jakości kodu

- **Użyj records** dla DTO (Java 21)
- **Optional** dla wartości nullable
- **Stream API** do przetwarzania list paragrafów
- **Logging** zamiast `System.out` (tylko w runner dozwolony sout)
- **Nie loguj** klucza API
- **Pretty print** JSON
- **Obsługa błędów** - wrap w try-catch z logowaniem

## Uruchomienie

### Opcja 1: Użyj application-local.yml (zalecane)

```bash
# Ustaw profile
export SPRING_PROFILES_ACTIVE=local

# Uruchom
./mvnw spring-boot:run -Dspring-boot.run.arguments="./testowy.pdf"
```

### Opcja 2: Environment variables

```bash
export AZURE_DOC_INTELLIGENCE_ENDPOINT="https://xxx.cognitiveservices.azure.com/"
export AZURE_DOC_INTELLIGENCE_KEY="xxx"
export AZURE_OPENAI_ENDPOINT="https://xxx.openai.azure.com/"
export AZURE_OPENAI_API_KEY="xxx"
export AZURE_OPENAI_DEPLOYMENT_NAME="gpt-4.1"

./mvnw spring-boot:run -Dspring-boot.run.arguments="./testowy.pdf"
```

## Kryteria akceptacji

### Faza 1 - Document Analysis (priorytet)

✅ Wszystkie warstwy oddzielone (config/service/model/ai/runner)
✅ Constructor injection w 100%
✅ Records używane dla DTO
✅ Filtrowanie niepotrzebnych ról
✅ Obliczenie QualityMetrics
✅ Pretty print JSON
✅ Brak hardcoded credentials w kodzie
✅ application-local.yml w .gitignore
✅ Kod kompiluje się i działa dla przykładowego PDF

### Faza 2 - AI Processing (przyszłe rozszerzenie)

⏳ DocumentProcessingService zaimplementowany
⏳ Chunking strategy działa (semantic/fixed-size)
⏳ Klasyfikacja dokumentu przez Azure OpenAI
⏳ DocumentChunk model z metadanymi
⏳ Spring AI integration testowana

### Faza 3 - Agent Planning (backlog)

⏳ AgentPlanner do orchestracji kroków
⏳ Function calling dla agentów
⏳ Vector store integration
⏳ RAG capabilities
