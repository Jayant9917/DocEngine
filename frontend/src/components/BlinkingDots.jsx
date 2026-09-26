function BlinkingDots({
  backgroundColor = '#0a0e14',
  colorFrom = '#0ea5e9',
  colorTo = '#67e8f9',
  density = 20,
  coverage = 0.6,
  dotSize = 1,
  layers = 2,
}) {
  const dots = Array.from({ length: density * 4 }, (_, index) => index)

  return (
    <div
      aria-hidden="true"
      className="blinking-dots"
      style={{
        '--dots-background': backgroundColor,
        '--dots-from': colorFrom,
        '--dots-to': colorTo,
        '--dot-size': `${dotSize}px`,
        '--dot-coverage': coverage,
        '--dot-layers': layers,
      }}
    >
      {dots.map((dot) => <span key={dot} className="blinking-dot" />)}
    </div>
  )
}

export default BlinkingDots
