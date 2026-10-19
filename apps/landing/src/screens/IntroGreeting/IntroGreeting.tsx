import { useEffect } from 'react';
import Reveal from '../../ui/motion/Reveal';
import FrameOctagon from '../../ui/frames/FrameOctagon';
import DecipherText from '../../ui/vendor/react-bits/DecipherText';
import './IntroGreeting.scss';

type IntroGreetingProps = { firstName: string; onDone: () => void };

export default function IntroGreeting({ firstName, onDone }: IntroGreetingProps) {
  useEffect(() => {
    const timer = window.setTimeout(onDone, 2850);
    return () => window.clearTimeout(timer);
  }, [onDone]);

  return (
    <section className="intro-screen" aria-label={`Hi ${firstName}!`} data-testid="intro-greeting">
      <div className="intro-radar intro-radar-one" />
      <div className="intro-radar intro-radar-two" />

      <Reveal as="div" className="intro-frame" duration={1.2} from={{ scale: 0.88 }}>
        <FrameOctagon
          className="intro-octagon"
          duration={1.2}
          // eslint-disable-next-line no-restricted-syntax -- static custom properties; move into the CSS module in the CSS Modules PR
          style={{
            '--frame-line-color': 'hsl(184 100% 58%)',
            '--frame-bg-color': 'hsl(184 100% 58% / 8%)',
          }}
        />
        <DecipherText as="h1" className="intro-title" text={`Hi ${firstName}!`} durationMs={1200} />
      </Reveal>
    </section>
  );
}
