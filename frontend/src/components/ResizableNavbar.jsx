import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import './ResizableNavbar.css'

const items = [
  { name: 'How it works', href: '#how-it-works' },
  { name: 'Capabilities', href: '#capabilities' },
  { name: 'Architecture', href: '#architecture' },
]

export default function ResizableNavbar() {
  const [open, setOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return <header className={`resizable-navbar${scrolled ? ' is-scrolled' : ''}`}>
    <div className="resizable-navbar-inner">
      <a className="resizable-logo" href="#top" aria-label="DocEngine home"><img className="resizable-logo-image" src="/folder.png" alt="" /><span>DOCENGINE</span></a>
      <nav className="resizable-desktop-links" aria-label="Main navigation">{items.map((item) => <a key={item.name} href={item.href}>{item.name}</a>)}</nav>
      <div className="resizable-desktop-actions"><Link className="resizable-secondary" to="/">Overview</Link><Link className="resizable-primary" to="/upload">Launch app <span aria-hidden="true">→</span></Link></div>
      <button className="resizable-toggle" aria-expanded={open} aria-controls="mobile-navigation" onClick={() => setOpen((value) => !value)}>{open ? '×' : '☰'}<span className="sr-only">Toggle navigation</span></button>
    </div>
    {open && <nav id="mobile-navigation" className="resizable-mobile-links" aria-label="Mobile navigation">{items.map((item) => <a key={item.name} href={item.href} onClick={() => setOpen(false)}>{item.name}</a>)}<Link className="resizable-primary" to="/upload" onClick={() => setOpen(false)}>Launch app <span aria-hidden="true">→</span></Link></nav>}
  </header>
}
