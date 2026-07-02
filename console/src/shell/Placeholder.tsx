import './Placeholder.css'

export default function Placeholder({ title }: { title: string }) {
  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">{title}</span>
        <h1>{title}</h1>
      </header>
      <div className="card placeholder-card">
        <p className="placeholder-lede">Coming in this phase</p>
        <p className="placeholder-sub">
          This surface is part of the console roadmap. It is scaffolded and will
          light up in a later slice.
        </p>
      </div>
    </section>
  )
}
