import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import Home from './pages/Home'
import './pages/UploadPage.css'

const API_BASE_URL = 'http://127.0.0.1:8081'
const STATUS_STEPS = ['Uploaded', 'Queued', 'Processing', 'Completed', 'Download']

function getCurrentStep(upload, job) {
  if (!job) return upload ? 0 : -1
  if (job.status === 'FAILED') return 2
  if (job.status === 'COMPLETED' && job.resultUrl) return 4
  return job.status === 'PROCESSING' ? 2 : 1
}

function UploadPage() {
  const fileInputRef = useRef(null)
  const [file, setFile] = useState(null)
  const [apiKey, setApiKey] = useState('demo-tenant-key')
  const [upload, setUpload] = useState(null)
  const [month, setMonth] = useState('2026-01')
  const [job, setJob] = useState(null)
  const [error, setError] = useState('')
  const [isUploading, setIsUploading] = useState(false)
  const [isCreatingJob, setIsCreatingJob] = useState(false)
  const [isDragging, setIsDragging] = useState(false)

  function selectFile(nextFile) {
    if (!nextFile) return
    if (!nextFile.name.toLowerCase().endsWith('.csv')) {
      setError('Please choose a CSV file. Other file types are not supported.')
      return
    }
    setError('')
    setFile(nextFile)
    setUpload(null)
    setJob(null)
  }

  useEffect(() => {
    if (!job?.jobId) return undefined
    let cancelled = false

    async function pollJobStatus() {
      try {
        const response = await fetch(`${API_BASE_URL}/api/v1/jobs/${job.jobId}`, {
          headers: { 'X-API-Key': apiKey },
        })
        const body = await response.json()
        if (!response.ok) throw new Error(body.message || body.error || 'Could not read job status.')
        if (!cancelled) setJob(body)
      } catch (statusError) {
        if (!cancelled) setError(statusError.message || 'Could not read job status.')
      }
    }

    pollJobStatus()
    const intervalId = window.setInterval(() => {
      if (job.status !== 'COMPLETED' && job.status !== 'FAILED') pollJobStatus()
    }, 2000)
    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [job?.jobId, job?.status, apiKey])

  async function handleUpload(event) {
    event.preventDefault()
    setError('')
    setUpload(null)
    setJob(null)
    if (!file) {
      setError('Choose a CSV file first.')
      return
    }
    if (!file.name.toLowerCase().endsWith('.csv')) {
      setError('Only CSV files are supported.')
      return
    }

    const formData = new FormData()
    formData.append('file', file)
    setIsUploading(true)
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/uploads`, {
        method: 'POST',
        headers: { 'X-API-Key': apiKey },
        body: formData,
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.message || body.error || 'Upload failed.')
      setUpload(body)
    } catch (uploadError) {
      setError(uploadError.message || 'Could not connect to the DocEngine API.')
    } finally {
      setIsUploading(false)
    }
  }

  async function handleCreateJob() {
    if (!upload || !month) return
    setError('')
    setJob(null)
    setIsCreatingJob(true)
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/jobs`, {
        method: 'POST',
        headers: {
          'X-API-Key': apiKey,
          'Idempotency-Key': `frontend-${upload.uploadId}-${month}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          jobType: 'GENERATE_MONTHLY_REPORT',
          input: { month, dataFile: upload.inputReference },
        }),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.message || body.error || 'Could not create report job.')
      setJob(body)
    } catch (jobError) {
      setError(jobError.message || 'Could not connect to the DocEngine API.')
    } finally {
      setIsCreatingJob(false)
    }
  }

  return (
    <main className="upload-page">
      <div className="upload-orb upload-orb-violet" aria-hidden="true" />
      <div className="upload-orb upload-orb-cyan" aria-hidden="true" />
      <div className="upload-shell">
        <header className="upload-header">
          <Link to="/" className="upload-back"><span aria-hidden="true">←</span> DocEngine home</Link>
          <p className="upload-eyebrow"><span className="upload-brand-mark">T</span> DOCENGINE <span>/</span> REPORT STUDIO</p>
          <h1>Turn your data into <span>a clear report.</span></h1>
          <p className="upload-intro">Upload a sales CSV, choose a month, and let DocEngine build a downloadable report in the background.</p>
        </header>

        <form onSubmit={handleUpload} className="upload-card">
          <div className="upload-card-heading">
            <div><span className="upload-step-index">01</span><h2>Source dataset</h2></div>
            <span className="upload-secure"><span /> PRIVATE UPLOAD</span>
          </div>

          <label className="upload-label" htmlFor="api-key">API key</label>
          <input id="api-key" value={apiKey} onChange={(event) => setApiKey(event.target.value)} autoComplete="off" className="upload-input" placeholder="Enter your tenant API key" />
          <p className="upload-field-hint">For local development, use the demo tenant key from your project setup.</p>

          <span className="upload-label upload-file-label">CSV dataset</span>
          <input ref={fileInputRef} id="csv-file" type="file" accept=".csv,text/csv" onChange={(event) => selectFile(event.target.files?.[0] ?? null)} className="upload-hidden-input" />
          <div
            className={`upload-dropzone${isDragging ? ' is-dragging' : ''}${file ? ' has-file' : ''}`}
            role="button"
            tabIndex={0}
            aria-label="Choose a CSV file or drop it here"
            onClick={() => fileInputRef.current?.click()}
            onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); fileInputRef.current?.click() } }}
            onDragOver={(event) => { event.preventDefault(); setIsDragging(true) }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={(event) => { event.preventDefault(); setIsDragging(false); selectFile(event.dataTransfer.files?.[0] ?? null) }}
          >
            <span className="upload-file-icon" aria-hidden="true">{file ? 'CSV' : '↑'}</span>
            <span className="upload-drop-copy"><strong>{file ? file.name : 'Drop your CSV file here'}</strong><small>{file ? `${(file.size / 1024).toFixed(1)} KB · Ready to upload` : 'or click to browse · CSV files only'}</small></span>
            <span className="upload-browse">{file ? 'Change' : 'Browse files'}</span>
          </div>

          <button type="submit" disabled={isUploading} className="upload-primary-button">
            {isUploading ? <><span className="upload-spinner" /> Uploading to DocEngine…</> : <>Upload dataset <span aria-hidden="true">→</span></>}
          </button>

          <AnimatePresence initial={false}>
            {error && <motion.p className="upload-error" role="alert" initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }}>{error}</motion.p>}
            {upload && (
              <motion.section className="upload-success" aria-live="polite" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}>
                <div className="upload-success-title"><span className="upload-check">✓</span><div><strong>Dataset uploaded</strong><small>Your file is safely stored and ready for processing.</small></div></div>
                <dl className="upload-details">
                  <div><dt>FILE</dt><dd>{upload.fileName}</dd></div>
                  <div><dt>UPLOAD ID</dt><dd>{upload.uploadId}</dd></div>
                  <div><dt>INPUT REFERENCE</dt><dd>{upload.inputReference}</dd></div>
                </dl>
              </motion.section>
            )}
          </AnimatePresence>
        </form>

        <AnimatePresence>
          {upload && (
            <motion.section className="report-card" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}>
              <div className="upload-card-heading">
                <div><span className="upload-step-index">02</span><h2>Build your report</h2></div>
                <span className="report-format">PDF REPORT</span>
              </div>
              <p className="report-description">Choose the month to include. DocEngine processes the job asynchronously, so you can watch its progress below.</p>
              <div className="report-controls">
                <div className="report-month-field"><label className="upload-label" htmlFor="report-month">Report month</label><input id="report-month" type="month" value={month} onChange={(event) => setMonth(event.target.value)} className="upload-input" /></div>
                <button type="button" onClick={handleCreateJob} disabled={isCreatingJob} className="upload-primary-button report-button">
                  {isCreatingJob ? 'Starting job…' : job ? 'Create another report' : 'Create report'} <span aria-hidden="true">→</span>
                </button>
              </div>

              {job && (
                <div className="job-status-card">
                  <div className="job-status-heading"><div><span className="upload-step-index">03</span><h2>Report progress</h2></div><span className={`job-status-badge status-badge-${job.status.toLowerCase()}`}>{job.status}</span></div>
                  <div className="status-steps" aria-label={`Job status: ${job.status}`}>
                    {STATUS_STEPS.map((step, index) => {
                      const currentStep = getCurrentStep(upload, job)
                      const failed = job.status === 'FAILED' && index === currentStep
                      const state = failed ? 'failed' : index < currentStep ? 'passed' : index === currentStep ? 'current' : 'future'
                      return <div className="status-step" key={step}><div className={`status-node status-${state}`}>{index < currentStep && !failed ? '✓' : index + 1}</div><span className={`status-label status-label-${state}`}>{step}</span>{index < STATUS_STEPS.length - 1 && <div className={`status-line ${index < currentStep ? 'status-line-passed' : ''}`} />}</div>
                    })}
                  </div>
                  <p className="job-id-line">JOB ID <code>{job.jobId}</code></p>
                  {job.status === 'FAILED' && <p className="job-failure">The report could not be generated. Check the CSV data and try again.</p>}
                  {job.resultUrl && <a className="report-download" href={job.resultUrl} target="_blank" rel="noreferrer"><span aria-hidden="true">↓</span> Download your PDF report</a>}
                </div>
              )}
            </motion.section>
          )}
        </AnimatePresence>

        <aside className="upload-tip"><span aria-hidden="true">✦</span><p><strong>Quick demo tip</strong> Use <code>demo-tenant-key</code> locally and choose one of the sample CSV files in the project’s <code>dummy data/</code> folder.</p></aside>
        <footer className="upload-footer">DOCENGINE <span>·</span> ASYNC REPORT PROCESSING</footer>
      </div>
    </main>
  )
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/upload" element={<UploadPage />} />
        <Route path="*" element={<Home />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
