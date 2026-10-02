# Resume Analyzer (Java / Spring Boot)

ATS-style resume checker: parses PDF/DOCX/TXT, scores out of 100, flags formatting errors, weak bullets,
missing metrics and keywords, and (optionally) generates a full line-by-line rewrite report with Claude.

## Run
    mvn spring-boot:run
    # optional AI report:
    export ANTHROPIC_API_KEY=your_key
Open http://localhost:8080

## Structure
- `AtsEngine` - rule-based scoring (pure Java, no dependencies)
- `ResumeTextExtractor` - PDFBox + Apache POI
- `ClaudeReportService` - sends the ATS-specialist prompt (`system-prompt.txt`) to the Claude API
- `AnalyzeController` - `POST /api/analyze` (multipart: `file`, `jd`, `ai`)

## Score weights (100)
Sections 20 | Contact & links 10 | Action verbs 15 | Quantified impact 20 | Weak phrasing 10 | Formatting 5 | Keywords 20

## Ideas to extend
Python microservice for spaCy NLP / semantic JD matching, user accounts + history (PostgreSQL), PDF report export.
