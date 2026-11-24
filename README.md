# FormSathi — Enhanced Full-Stack Hackathon Prototype

A runnable React + Spring Boot prototype for understanding scholarship/application notices.

## Included features
- PDF and TXT upload, plus pasted-text analysis
- PDF text extraction (selectable-text PDFs; scanned-image PDFs need OCR)
- Quick summary, eligibility criteria, deadlines and required-document extraction
- Personal eligibility check using applicant-provided details
- Document checklist with tickable completion status
- Simple question-answer helper based on extracted notice text
- Recent analyses stored in an H2 database

**Important:** This is a transparent, rule-based prototype. It does not use an external AI model and must not be presented as an official eligibility decision. Always verify requirements and dates against the original notice.

## Requirements
- Java 17 or newer
- Maven 3.8+
- Node.js 18+ and npm

## Run on Windows (VS Code terminals)

### 1. Start backend
Open a terminal in the `backend` folder and run:

```cmd
mvn spring-boot:run
```

Backend: http://localhost:8080  
Health check: http://localhost:8080/api/health  
H2 console: http://localhost:8080/h2-console

H2 console JDBC URL: `jdbc:h2:mem:formsathi`, username: `sa`, password: leave blank.

### 2. Start frontend
Open a SECOND terminal in the `frontend` folder and run:

```cmd
npm install
npm run dev
```

Open the URL printed by Vite, normally http://localhost:5173.

Keep both terminals open while using the app.

## Test flow
1. Upload `sample-data/sample-scholarship-notice.txt` or paste its contents.
2. Click **Analyze notice**.
3. Fill in the eligibility form and click **Check my eligibility**.
4. Tick documents as you collect them.
5. Ask a question such as: "What is the last date?" or "Which documents are required?"

## API endpoints
- `GET /api/health`
- `POST /api/documents/analyze` — multipart field `file`
- `POST /api/documents/analyze-text` — JSON `{ "text": "...", "filename": "pasted-notice.txt" }`
- `GET /api/documents/{id}`
- `GET /api/documents/recent`
- `POST /api/eligibility/check` — JSON applicant details and `documentId`
- `POST /api/documents/{id}/ask` — JSON `{ "question": "..." }`

## Notes / troubleshooting
- If `mvn` says there is no POM, first `cd` into the `backend` folder (the folder containing `pom.xml`).
- If port 8080 is busy, stop the other server or change `server.port` in `backend/src/main/resources/application.properties`.
- If port 5173 is busy, Vite will print another local URL.
- Only text-based/selectable PDFs can be extracted. Scanned PDFs require OCR, which is not included in this offline-ready version.
- The scholarship notice in `sample-data` is fictional demo content, not a real scholarship.
