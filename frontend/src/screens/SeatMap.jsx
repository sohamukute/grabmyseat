const GAP = 14;

export function SeatMap({ seats, mine = [], picked = [], onPick }) {
  if (seats.length === 0) return null;
  const lit = new Set(mine.map((seat) => `${seat.rowLabel}${seat.number}`));
  const rows = [...new Set(seats.map((seat) => seat.rowRank))].sort((a, b) => a - b);
  const width = Math.max(...seats.map((seat) => seat.number));
  const label = mine.length
    ? `Your seats: ${mine.map((seat) => `${seat.rowLabel}${seat.number}`).join(', ')}`
    : `${seats.filter((seat) => !seat.taken).length} of ${seats.length} seats free`;

  return (
    <figure className={onPick ? 'map pickable' : 'map'}>
      <svg viewBox={`-28 -34 ${width * GAP + 40} ${rows.length * GAP + 44}`} role="img" aria-label={label}>
        <rect className="stage" x={GAP / 2} y={-30} width={(width - 1) * GAP} height={6} rx={3} />
        {rows.map((rank, i) => {
          const row = seats.filter((seat) => seat.rowRank === rank);
          return (
            <g key={rank} transform={`translate(0 ${i * GAP})`}>
              <text className="row-label" x={-14} y={4}>{row[0].rowLabel}</text>
              {row.map((seat) => {
                const yours = lit.has(`${seat.rowLabel}${seat.number}`) || picked.includes(seat.id);
                const open = onPick && !seat.taken;
                return (
                  <circle key={seat.id} cx={(seat.number - 1) * GAP + GAP / 2} cy={0}
                          r={yours ? 5 : open ? 4.2 : 3.2}
                          className={yours ? 'seat yours' : seat.taken ? 'seat taken' : 'seat'}
                          role={open ? 'button' : undefined} tabIndex={open ? 0 : undefined}
                          aria-pressed={open ? picked.includes(seat.id) : undefined}
                          aria-label={open ? `Seat ${seat.rowLabel}${seat.number}` : undefined}
                          onClick={open ? () => onPick(seat) : undefined}
                          onKeyDown={open ? (e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), onPick(seat)) : undefined} />
                );
              })}
            </g>
          );
        })}
      </svg>
      <figcaption>{label}</figcaption>
    </figure>
  );
}
