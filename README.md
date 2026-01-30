# MCP Server Configuration

This PDF analyzer can be used as a Model Context Protocol (MCP) server.

## Docker Setup

1. Copy environment file:

```bash
cp .env.example .env
# Edit .env with your Azure credentials
```

2. Build and run:

```bash
docker-compose up --build
```

The service will be available at: http://localhost:8080

## API Endpoints

### POST /api/v1/documents/analyze

Upload a PDF file for analysis.

**Request:**

- Content-Type: multipart/form-data
- Parameter: `file` (PDF file)

**Response:**

```json
{
  "fileName": "document.pdf",
  "pageCount": 5,
  "sections": [...],
  "chunks": [...],
  "qualityMetrics": {
    "avgConfidence": 0.95,
    "totalParagraphs": 42,
    "totalChars": 5234,
    "hasStructureMarkers": true
  },
  "markdown": "# Document Title\n\n..."
}
```

### GET /api/v1/health

Health check endpoint.

## Configuration

Chunking behavior can be configured via environment variables or application.yml:

```yaml
app:
  chunking:
    strategy: semantic
    max-chunk-size: 1000
    overlap: 100
```

## MCP Integration

The service can be integrated with MCP clients by configuring the Docker container as an MCP server endpoint.
