import { lazy, Suspense, useEffect, useState } from 'react'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { Link } from 'react-router-dom'
import './Home.css'
import './HomeMonochrome.css'
import ResizableNavbar from '../components/ResizableNavbar'

const Dither = lazy(() => import('../components/Dither'))

const journey = ['Uploaded', 'Queued', 'Processing', 'Completed', 'Download']

const processSteps = [
  ['01', 'Upload a CSV', 'The API validates the CSV upload and stores the source file in object storage.'],
  ['02', 'Submit a conversion job', 'The API records the job and returns its ID without waiting for PDF generation.'],
  ['03', 'Queue the work', 'RabbitMQ carries the accepted job to an available worker.'],
  ['04', 'Claim and process', 'A worker atomically claims the job, reads the CSV, and creates a PDF.'],
  ['05', 'Store the result', 'The PDF is saved to object storage and the job status is updated.'],
  ['06', 'Download the PDF', 'The API returns a time-limited result URL when the job is complete.'],
]

const capabilities = [
  ['01', 'Asynchronous processing', 'The API accepts a job while CSV parsing and PDF generation run separately in the worker.'],
  ['02', 'Safe retries', 'An idempotency key lets clients retry a submission without creating another job.'],
  ['03', 'Atomic job claims', 'A database-side claim prevents competing workers from processing the same queued job.'],
  ['04', 'Tenant boundaries', 'Job access is scoped to the authenticated tenant.'],
  ['05', 'CSV-to-PDF conversion', 'The PDF contains all rows and columns from the uploaded header-based CSV.'],
  ['06', 'Observable services', 'Prometheus collects API and worker metrics for the Grafana dashboard.'],
]

const stack = ['React', 'Vite', 'Spring Boot', 'PostgreSQL', 'RabbitMQ', 'S3 / MinIO', 'AWS', 'Prometheus', 'Grafana', 'Docker Compose']
const architectureNodeOrder = ['Browser', 'React', 'Spring Boot', 'PostgreSQL', 'Object storage', 'RabbitMQ', 'DocEngine Worker']
const ditherWaveColor = [0.68, 0.68, 0.68]
const ditherBackgroundColor = [0.012, 0.012, 0.012]

export default function Home() {
  const [activeStep, setActiveStep] = useState(0)
  const reducedMotion = useReducedMotion()

  useEffect(() => {
    if (reducedMotion) return undefined
    const interval = window.setInterval(() => {
      setActiveStep((current) => (current + 1) % journey.length)
    }, 1900)
    return () => window.clearInterval(interval)
  }, [reducedMotion])

  return (
    <div className="home-page">
      <div className="home-atmosphere" aria-hidden="true">
        <Suspense fallback={null}>
          <Dither waveColor={ditherWaveColor} backgroundColor={ditherBackgroundColor} colorNum={2} pixelSize={3} waveSpeed={0.08} disableAnimation={false} />
        </Suspense>
      </div>

      <ResizableNavbar />
      {/*
        <div className="home-nav-inner">
          <a className="home-logo" href="#top" aria-label="DocEngine home"><span className="logo-orb" />DOCENGINE</a>
          <div className="home-nav-links">
            <a href="#how-it-works">How it works</a>
            <a href="#capabilities">Capabilities</a>
            <a href="#architecture">Architecture</a>
            <Link to="/upload" className="nav-launch">Launch app <span aria-hidden="true">↗</span></Link>
          </div>
        </div>
      </motion.nav> */}

      <main id="top">
        <section className="home-hero">
          <div className="hero-content">
            <motion.div className="eyebrow" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
              <span className="eyebrow-dot" /> DISTRIBUTED REPORT PROCESSING
            </motion.div>
            <motion.h1 initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.16, duration: 0.6 }}>
              Upload once.<br /><span>Process reliably.</span><br /><span>Download when ready.</span>
            </motion.h1>
            <motion.p className="hero-copy" initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.26 }}>
              Upload a header-based CSV and get a downloadable PDF containing its data while DocEngine handles conversion in the background.
            </motion.p>
            <motion.div className="hero-buttons" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.34 }}>
              <Link to="/upload" className="button-primary">Try DocEngine <span aria-hidden="true">→</span></Link>
            </motion.div>

            <motion.div className="journey-card" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.45 }}>
              <div className="journey-heading"><span>JOB LIFECYCLE</span><span className="demo-caption">Illustrative preview</span></div>
              <div className="journey-track" aria-label="Illustration of the CSV conversion job lifecycle">
                {journey.map((label, index) => (
                  <div className="journey-step" key={label}>
                    <div className={`journey-node ${index < activeStep ? 'is-done' : ''} ${index === activeStep ? 'is-active' : ''}`}>
                      {index < activeStep ? '✓' : String(index + 1).padStart(2, '0')}
                    </div>
                    {index < journey.length - 1 && <div className="journey-connector"><motion.span animate={{ scaleX: index < activeStep ? 1 : 0 }} transition={{ duration: 0.4 }} /></div>}
                    <span className={index <= activeStep ? 'journey-label is-bright' : 'journey-label'}>{label}</span>
                  </div>
                ))}
              </div>
              <AnimatePresence mode="wait">
                <motion.p className="journey-status" key={activeStep} initial={reducedMotion ? false : { opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={reducedMotion ? undefined : { opacity: 0, y: -6 }}>
                  Example stage <strong>{journey[activeStep]}</strong>
                </motion.p>
              </AnimatePresence>
            </motion.div>
          </div>
          <div className="hero-footnote"><span>01 / ASYNC BY DESIGN</span><span>CSV IN <i /> PDF OUT</span></div>
        </section>

        <section className="content-section" id="how-it-works">
          <div className="section-wrap">
            <motion.div className="section-heading" initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}>
            <span className="section-kicker">THE FLOW</span><h2>From CSV rows to a ready PDF.</h2><p>Six clear stages, coordinated by the API, queue, and worker.</p>
            </motion.div>
            <div className="process-grid">
              {processSteps.map(([number, title, description], index) => (
                <motion.article className="process-card" key={number} initial={reducedMotion ? false : { opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.18 + index * 0.07, duration: 0.45 }} whileHover={reducedMotion ? undefined : { y: -5 }}>
                  <span className="card-number">{number}</span><h3>{title}</h3><p>{description}</p>
                </motion.article>
              ))}
            </div>
          </div>
        </section>

        <section className="content-section section-tinted" id="capabilities">
          <div className="section-wrap">
            <motion.div className="section-heading" initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}>
              <span className="section-kicker">BUILT INTO THE MVP</span><h2>Reliability at every handoff.</h2><p>Core behavior implemented in DocEngine’s API and worker pipeline.</p>
            </motion.div>
            <div className="capability-grid">
              {capabilities.map(([number, title, description], index) => (
                <motion.article className="capability-card" key={number} initial={reducedMotion ? false : { opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.07, duration: 0.45 }}>
                  <span className="capability-number">{number}</span><div><h3>{title}</h3><p>{description}</p></div>
                </motion.article>
              ))}
            </div>
          </div>
        </section>

        <section className="content-section" id="architecture">
          <div className="section-wrap">
            <motion.div className="section-heading" initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}>
              <span className="section-kicker">SYSTEM MAP</span><h2>Small services. One connected flow.</h2><p>Each part has a focused role; the queue separates request handling from PDF generation.</p>
            </motion.div>
            <motion.div className="architecture-card" initial={reducedMotion ? false : { opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
              <div className="architecture-flow">
                <ArchitectureNode label="Browser" detail="User" kind="neutral" reducedMotion={reducedMotion} /><span className="flow-arrow">→</span>
                <ArchitectureNode label="React" detail="Frontend" kind="cyan" reducedMotion={reducedMotion} /><span className="flow-arrow">→</span>
                <ArchitectureNode label="Spring Boot" detail="API" kind="cyan" reducedMotion={reducedMotion} />
              </div>
              <div className="architecture-branches">
                <div className="branch-line" />
                <ArchitectureNode label="PostgreSQL" detail="Jobs · tenants · status" kind="green" reducedMotion={reducedMotion} />
              <ArchitectureNode label="Object storage" detail="CSV input · PDF output" kind="green" reducedMotion={reducedMotion} />
                <ArchitectureNode label="RabbitMQ" detail="Async job queue" kind="amber" reducedMotion={reducedMotion} />
                <span className="branch-arrow">↓</span>
                <ArchitectureNode label="DocEngine Worker" detail="Atomic claim · CSV to PDF" kind="cyan" reducedMotion={reducedMotion} />
              </div>
              <div className="monitoring-strip"><span>OBSERVABILITY</span><b>Prometheus</b><i>scrapes metrics</i><span className="flow-arrow">→</span><b>Grafana</b><i>visualizes metrics</i></div>
            </motion.div>
          </div>
        </section>

        <section className="content-section stack-section">
          <div className="section-wrap">
            <div className="stack-heading"><span className="section-kicker">THE TOOLKIT</span><h2>Built with familiar, focused tools.</h2></div>
            <div className="stack-list">{stack.map((tech, index) => <motion.span key={tech} initial={{ opacity: 0, y: 8 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: index * 0.04 }}>{tech}</motion.span>)}</div>
          </div>
        </section>

        <section className="closing-section">
          <motion.div className="closing-card" initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}>
            <span className="section-kicker">READY WHEN YOU ARE</span><h2>Turn a CSV into a PDF.</h2><p>Upload a header-based CSV and watch its conversion move through the DocEngine pipeline.</p>
            <Link to="/upload" className="button-primary">Open the app <span aria-hidden="true">→</span></Link>
          </motion.div>
        </section>
      </main>
      <footer className="home-footer"><div className="footer-brand"><a className="home-logo" href="#top"><span className="logo-orb" />DOCENGINE</a><p>Reliable asynchronous CSV-to-PDF processing.</p></div><div className="footer-status"><span><i /> API ready</span><small>One API · One worker · S3 / MinIO</small></div></footer>
    </div>
  )
}

function ArchitectureNode({ label, detail, kind, reducedMotion }) {
  const nodeIndex = architectureNodeOrder.indexOf(label)
  const iconMap = { React: 'react.svg', 'Spring Boot': 'springboot.svg', PostgreSQL: 'postgresql.svg', RabbitMQ: 'rabbitmq.svg' }
  const fallbackIcons = { Browser: '▣', 'Object storage': '▤', 'DocEngine Worker': '⚙' }
  return <motion.div className={`architecture-node node-${kind}`} initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 + nodeIndex * 0.08, duration: 0.35 }}><span className="architecture-icon" aria-hidden="true">{iconMap[label] ? <img src={`/architecture-icons/${iconMap[label]}`} alt="" /> : fallbackIcons[label]}</span><strong>{label}</strong><span>{detail}</span></motion.div>
}
