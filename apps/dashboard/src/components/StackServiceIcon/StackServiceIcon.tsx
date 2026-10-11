const HUB_COLOR = '#27e7ff';
const SPRING_COLOR = '#6DB33F';

const HUB_NODES: [number, number][] = [
  [19, 6],
  [30, 12.5],
  [30, 25.5],
  [19, 32],
  [8, 25.5],
  [8, 12.5],
];

const HUB_SPOKES: [number, number, number, number][] = [
  [19, 15, 19, 8.5],
  [22.5, 17, 27.5, 14],
  [22.5, 21, 27.5, 24],
  [19, 23, 19, 29.5],
  [15.5, 21, 10.5, 24],
  [15.5, 17, 10.5, 14],
];

function HubGraphic() {
  return (
    <>
      <circle cx="19" cy="19" r="7" fill="rgba(39,231,255,0.12)" />
      <circle cx="19" cy="19" r="4" fill={HUB_COLOR} />
      {HUB_NODES.map(([cx, cy]) => (
        <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="2.5" fill={HUB_COLOR} opacity="0.7" />
      ))}
      {HUB_SPOKES.map(([x1, y1, x2, y2]) => (
        <line
          key={`${x1}-${y1}`}
          x1={x1}
          y1={y1}
          x2={x2}
          y2={y2}
          stroke={HUB_COLOR}
          strokeWidth="1"
          opacity="0.45"
        />
      ))}
    </>
  );
}

function SpringGraphic() {
  return (
    <>
      <circle cx="19" cy="19" r="17" fill="rgba(109,179,63,0.10)" />
      <circle cx="19" cy="19" r="16" fill={SPRING_COLOR} />
      <path d="M 9 30 C 3 20, 10 8, 29 8 C 32 21, 20 32, 9 30 Z" fill="white" />
      <path
        d="M 11 27 C 16 20, 22 14, 27 10"
        stroke={SPRING_COLOR}
        strokeWidth="1.5"
        fill="none"
        strokeLinecap="round"
        opacity="0.5"
      />
    </>
  );
}

/** The 38×38 icon shown on a stack-status card: the hub graphic or the Spring leaf. */
export function StackServiceIcon({ variant }: { variant: 'hub' | 'spring' }) {
  return (
    <span className="icon-box" aria-hidden="true">
      <svg
        width="38"
        height="38"
        viewBox="0 0 38 38"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        {variant === 'hub' ? <HubGraphic /> : <SpringGraphic />}
      </svg>
    </span>
  );
}
