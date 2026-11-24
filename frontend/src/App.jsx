import { useState } from 'react'

const sample = `SAMPLE SCHOLARSHIP APPLICATION NOTICE
FICTIONAL DEMO DOCUMENT FOR FORMSATHI TESTING ONLY — NOT AN OFFICIAL SCHOLARSHIP NOTICE.
Scholarship name: Bright Future Merit-cum-Means Scholarship 2026.
Academic year: 2026-2027.
Application opens: 1 November 2026.
Last date to apply: 30 December 2026, 11:59 PM.
ELIGIBILITY CRITERIA
Applicant must be a resident of India.
Applicant must be enrolled as a full-time student in a recognised school, college or university for academic year 2026-2027.
Applicant must have obtained at least 75% marks in the previous qualifying examination.
Annual family income must not exceed Rs. 2,50,000.
DOCUMENTS REQUIRED
Recent passport-size photograph.
Proof of identity such as Aadhaar card or another valid government-issued identity document.
Previous year's marksheet or grade report.
Current academic year admission certificate or bonafide certificate.
Family income certificate issued by a competent authority.
Bank passbook or cancelled cheque showing the applicant's name and account details.
HOW TO APPLY
Complete the online application form with accurate details. Upload clear copies of all required documents. Review the information and submit before the deadline. Save the application acknowledgement number.`

async function api(url, options = {}) {
  const response = await fetch(url, options)
  const data = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(data.error || `Request failed (${response.status})`)
  return data
}

export default function App() {
  const [file, setFile] = useState(null)
  const [text, setText] = useState('')
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [marks, setMarks] = useState('80')
  const [income, setIncome] = useState('150000')
  const [residency, setResidency] = useState('Yes')
  const [enrollment, setEnrollment] = useState('Yes')
  const [eligibility, setEligibility] = useState(null)
  const [checked, setChecked] = useState([])
  const [question, setQuestion] = useState('What is the last date?')
  const [answer, setAnswer] = useState('')

  async function analyze() {
    setBusy(true); setError(''); setEligibility(null); setAnswer(''); setChecked([])
    try {
      let data
      if (file) {
        const form = new FormData()
        form.append('file', file)
        data = await api('/api/documents/analyze', { method: 'POST', body: form })
      } else {
        data = await api('/api/documents/analyze-text', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ text, filename: 'pasted-notice.txt' })
        })
      }
      setResult(data)
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  async function checkEligibility() {
    if (!result) return
    setError('')
    try {
      const data = await api('/api/eligibility/check', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          documentId: result.id, residency, enrollment,
          marks: Number(marks), annualFamilyIncome: Number(income)
        })
      })
      setEligibility(data)
    } catch (e) { setError(e.message) }
  }

  async function ask() {
    if (!result) return
    setError('')
    try {
      const data = await api(`/api/documents/${result.id}/ask`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question })
      })
      setAnswer(data.answer)
    } catch (e) { setError(e.message) }
  }

  function useSample() { setFile(null); setText(sample); setError(''); }

  return <div className="app-shell">
    <header className="topbar">
      <div className="brand"><span className="brand-mark">F</span><span>Form<span className="brand-green">Sathi</span></span></div>
      <span className="tagline">Understand every notice. Apply with confidence.</span>
      <span className="status"><i /> Demo ready</span>
    </header>

    <main>
      <section className="hero">
        <div className="eyebrow">YOUR DOCUMENT COMPANION</div>
        <h1>Big forms. <em>Clear answers.</em></h1>
        <p>Upload a scholarship or application notice. Get the key details, check your eligibility and prepare your documents.</p>
        <div className="hero-pills"><span>✦ Simple summaries</span><span>✓ Eligibility checks</span><span>▤ Document checklist</span></div>
      </section>

      <section className="workspace">
        <div className="panel upload-panel">
          <div className="step-title"><span>01</span><div><h2>Add your notice</h2><p>Upload a PDF/TXT or paste its text below.</p></div></div>
          <label className="dropzone">
            <input type="file" accept=".pdf,.txt,application/pdf,text/plain" onChange={e => { setFile(e.target.files?.[0] || null); setText('') }} />
            <div className="upload-icon">⇧</div>
            <strong>{file ? file.name : 'Choose a document'}</strong>
            <small>PDF or TXT · Max 10 MB</small>
          </label>
          <div className="divider"><span>OR PASTE TEXT</span></div>
          <textarea value={text} onChange={e => { setText(e.target.value); setFile(null) }} placeholder="Paste scholarship notice or application instructions here..." />
          <div className="button-row">
            <button className="primary" onClick={analyze} disabled={busy || (!file && !text.trim())}>{busy ? 'Analyzing…' : '✧ Analyze notice'}</button>
            <button className="text-button" onClick={useSample}>Try sample scholarship notice</button>
          </div>
          {error && <div className="error">{error}</div>}
          <div className="privacy-note">Your demo runs locally on your computer. Do not upload sensitive personal documents during a public demo.</div>
        </div>

        <div className="panel results-panel">
          <div className="step-title"><span>02</span><div><h2>Your clear guide</h2><p>Key information extracted from the notice.</p></div></div>
          {!result ? <div className="empty"><div>▤</div><h3>Your results will appear here</h3><p>Upload a notice or try the sample to begin.</p></div> : <>
            <div className="result-heading"><div><small>ANALYZED DOCUMENT</small><h3>{result.filename}</h3></div><span className="success-badge">✓ Analyzed</span></div>
            <div className="summary-box"><h3>✦ Quick summary</h3><p>{result.summary}</p></div>
            <div className="deadline-card"><span>APPLICATION DEADLINE</span><strong>{result.deadline}</strong></div>
            <div className="result-section"><h3>Important points</h3><ul>{result.importantPoints.map((item, i) => <li key={i}>{item}</li>)}</ul></div>
            <div className="result-section"><h3>Eligibility criteria detected</h3>
              {result.eligibilityCriteria.length ? <ul>{result.eligibilityCriteria.map((item, i) => <li key={i}>{item}</li>)}</ul> : <p className="muted">No clear eligibility statements detected. Review the original notice.</p>}
            </div>
            <div className="result-section"><h3>Document checklist</h3>
              {result.requiredDocuments.length ? result.requiredDocuments.map((item, i) => <label className="check-item" key={i}><input type="checkbox" checked={checked.includes(i)} onChange={() => setChecked(old => old.includes(i) ? old.filter(x => x !== i) : [...old, i])}/><span className={checked.includes(i) ? 'done' : ''}>{item}</span></label>) : <p className="muted">No specific documents detected.</p>}
              {result.requiredDocuments.length > 0 && <div className="progress"><span style={{width: `${checked.length / result.requiredDocuments.length * 100}%`}} /></div>}
              <small className="muted">{checked.length} of {result.requiredDocuments.length} items marked ready (this checklist is stored only in this browser session).</small>
            </div>

            <div className="result-section">
              <h3>Check my eligibility</h3>
              <p className="muted">Enter your details for a preliminary comparison with the notice.</p>
              <div className="form-grid">
                <label>Resident of India? <select value={residency} onChange={e => setResidency(e.target.value)}><option>Yes</option><option>No</option><option>Not sure</option></select></label>
                <label>Currently enrolled? <select value={enrollment} onChange={e => setEnrollment(e.target.value)}><option>Yes</option><option>No</option><option>Not sure</option></select></label>
                <label>Previous exam marks (%) <input type="number" min="0" max="100" value={marks} onChange={e => setMarks(e.target.value)} /></label>
                <label>Annual family income (₹) <input type="number" min="0" value={income} onChange={e => setIncome(e.target.value)} /></label>
              </div>
              <button className="secondary" onClick={checkEligibility}>Check my eligibility</button>
              {eligibility && <div className="eligibility-result"><strong>{eligibility.result}</strong><ul>{eligibility.checks.map((x,i)=><li key={i}>{x}</li>)}</ul>{eligibility.caveats.map((x,i)=><small key={i}>• {x}<br/></small>)}</div>}
            </div>

            <div className="result-section">
              <h3>Ask this notice</h3>
              <div className="ask-row"><input value={question} onChange={e => setQuestion(e.target.value)} placeholder="Ask about deadline, marks, income..." /><button onClick={ask}>Ask</button></div>
              {answer && <div className="answer">{answer}</div>}
            </div>
          </>}
        </div>
      </section>
      <footer>FormSathi · A student hackathon prototype · Always verify details with the official scholarship provider.</footer>
    </main>
  </div>
}
